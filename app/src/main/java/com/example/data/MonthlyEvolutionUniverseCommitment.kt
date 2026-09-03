package com.example.data

import java.security.MessageDigest

private const val MONTHLY_EVOLUTION_VALIDATION_BATCH_SIZE = 4096
private const val MONTHLY_EVOLUTION_DIGEST_SCRATCH_BYTES = 8192
private val monthlyEvolutionDigestScratch = object : ThreadLocal<ByteArray>() {
    override fun initialValue(): ByteArray = ByteArray(MONTHLY_EVOLUTION_DIGEST_SCRATCH_BYTES)
}

/**
 * Compact stale-plan proof for the production monthly path.
 *
 * The previous compact path retained two sizeable attribute strings for every world player until
 * the weekly close committed. On a ~75k-player career this kept a large object graph alive across
 * the most memory-sensitive part of season simulation. This representation keeps only primitive
 * ids/team ids plus a SHA-256 digest of every football input that influences monthly evolution.
 */
data class MonthlyEvolutionUniverseCommitment(
    val playerIds: LongArray,
    val teamIds: LongArray,
    val teamPresent: BooleanArray,
    val digest0: LongArray,
    val digest1: LongArray,
    val digest2: LongArray,
    val digest3: LongArray
) {
    init {
        val size = playerIds.size
        require(teamIds.size == size)
        require(teamPresent.size == size)
        require(digest0.size == size)
        require(digest1.size == size)
        require(digest2.size == size)
        require(digest3.size == size)
        for (index in 1 until size) {
            require(playerIds[index] > playerIds[index - 1]) {
                "Monthly evolution commitment player ids must be unique and sorted."
            }
        }
    }

    val size: Int get() = playerIds.size

    fun teamIdAt(index: Int): Long? = if (teamPresent[index]) teamIds[index] else null
}

private data class MonthlyEvolutionRowCommitment(
    val id: Long,
    val teamId: Long?,
    val digest0: Long,
    val digest1: Long,
    val digest2: Long,
    val digest3: Long
)

/** Builds the compact proof while the normal 4,096-player evolution batches stream past. */
internal class MonthlyEvolutionCommitmentBuilder(expectedSize: Int) {
    private val rows = ArrayList<MonthlyEvolutionRowCommitment>(expectedSize.coerceAtLeast(0))
    private val digest = MessageDigest.getInstance("SHA-256")
    private val digestOutput = ByteArray(32)

    fun add(player: Player, atributosStorage: String) {
        updateMonthlyEvolutionStateDigest(
            digest = digest,
            id = player.id,
            age = player.age,
            position = player.position,
            force = player.force,
            potential = player.potential,
            minutosJogados = player.minutosJogados,
            mediaNotas = player.mediaNotas,
            focoTreino = player.focoTreino,
            atributosJson = player.atributosJson,
            atributosStorage = atributosStorage
        )
        finishDigest(digest, digestOutput)
        rows.add(
            MonthlyEvolutionRowCommitment(
                id = player.id,
                teamId = player.teamId,
                digest0 = readLong(digestOutput, 0),
                digest1 = readLong(digestOutput, 8),
                digest2 = readLong(digestOutput, 16),
                digest3 = readLong(digestOutput, 24)
            )
        )
    }

    fun build(): MonthlyEvolutionUniverseCommitment {
        rows.sortBy { it.id }
        val size = rows.size
        val playerIds = LongArray(size)
        val teamIds = LongArray(size)
        val teamPresent = BooleanArray(size)
        val digest0 = LongArray(size)
        val digest1 = LongArray(size)
        val digest2 = LongArray(size)
        val digest3 = LongArray(size)

        for (index in rows.indices) {
            val row = rows[index]
            if (index > 0) {
                check(row.id > playerIds[index - 1]) {
                    "Duplicate player id ${row.id} in monthly evolution commitment."
                }
            }
            playerIds[index] = row.id
            if (row.teamId != null) {
                teamPresent[index] = true
                teamIds[index] = row.teamId
            }
            digest0[index] = row.digest0
            digest1[index] = row.digest1
            digest2[index] = row.digest2
            digest3[index] = row.digest3
        }

        return MonthlyEvolutionUniverseCommitment(
            playerIds = playerIds,
            teamIds = teamIds,
            teamPresent = teamPresent,
            digest0 = digest0,
            digest1 = digest1,
            digest2 = digest2,
            digest3 = digest3
        )
    }
}

/**
 * Validates every persisted player against a compact proof without reconstructing the old
 * 75k-entry snapshot list or a HashMap copy. New weekly rows remain targeted corrections; missing
 * rows or changed football inputs fail closed. Team moves preserve the existing training-center
 * rule: same effective level is safe, different level is recalculated under the transaction.
 */
internal fun GameRepository.validateMonthlyEvolutionUniverseCommitment(
    expected: MonthlyEvolutionUniverseCommitment,
    expectedTrainingCenterLevels: Map<Long, Int>,
    currentTrainingCenterLevels: Map<Long, Int>,
    allowRosterCorrections: Boolean
): MonthlyEvolutionRosterValidation {
    if (expected.size == 0) {
        val count = getMonthlyEvolutionPlayerCount()
        return MonthlyEvolutionRosterValidation(
            valid = count == 0,
            correctionIds = emptySet(),
            currentPlayerCount = count
        )
    }

    val corrections = linkedSetOf<Long>()
    val database = db.openHelper.writableDatabase
    val digest = MessageDigest.getInstance("SHA-256")
    val digestOutput = ByteArray(32)
    var expectedIndex = 0
    var lastSeenId: Long? = null
    var currentCount = 0
    var valid = true

    while (true) {
        val query = if (lastSeenId == null) {
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            ORDER BY id ASC
            LIMIT $MONTHLY_EVOLUTION_VALIDATION_BATCH_SIZE
            """.trimIndent()
        } else {
            """
            SELECT id, teamId, age, position, force, potential, minutosJogados, mediaNotas,
                   focoTreino, atributosJson, atributos
            FROM players
            WHERE id > ?
            ORDER BY id ASC
            LIMIT $MONTHLY_EVOLUTION_VALIDATION_BATCH_SIZE
            """.trimIndent()
        }
        val args = lastSeenId?.let { arrayOf<Any>(it) } ?: emptyArray()
        var rowsInBatch = 0
        var batchLastId: Long? = null

        database.query(query, args).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val teamIdIndex = cursor.getColumnIndexOrThrow("teamId")
            val ageIndex = cursor.getColumnIndexOrThrow("age")
            val positionIndex = cursor.getColumnIndexOrThrow("position")
            val forceIndex = cursor.getColumnIndexOrThrow("force")
            val potentialIndex = cursor.getColumnIndexOrThrow("potential")
            val minutesIndex = cursor.getColumnIndexOrThrow("minutosJogados")
            val ratingIndex = cursor.getColumnIndexOrThrow("mediaNotas")
            val focusIndex = cursor.getColumnIndexOrThrow("focoTreino")
            val jsonIndex = cursor.getColumnIndexOrThrow("atributosJson")
            val attributesIndex = cursor.getColumnIndexOrThrow("atributos")

            while (cursor.moveToNext()) {
                val currentId = cursor.getLong(idIndex)
                val currentTeamId = if (cursor.isNull(teamIdIndex)) null else cursor.getLong(teamIdIndex)
                currentCount++
                rowsInBatch++
                batchLastId = currentId

                while (expectedIndex < expected.size && expected.playerIds[expectedIndex] < currentId) {
                    valid = false // expected row disappeared
                    expectedIndex++
                }

                if (expectedIndex >= expected.size || currentId < expected.playerIds[expectedIndex]) {
                    if (allowRosterCorrections) corrections.add(currentId) else valid = false
                    continue
                }

                updateMonthlyEvolutionStateDigest(
                    digest = digest,
                    id = currentId,
                    age = cursor.getInt(ageIndex),
                    position = cursor.getString(positionIndex),
                    force = cursor.getInt(forceIndex),
                    potential = cursor.getInt(potentialIndex),
                    minutosJogados = cursor.getInt(minutesIndex),
                    mediaNotas = cursor.getDouble(ratingIndex),
                    focoTreino = if (cursor.isNull(focusIndex)) null else cursor.getString(focusIndex),
                    atributosJson = if (cursor.isNull(jsonIndex)) null else cursor.getString(jsonIndex),
                    atributosStorage = cursor.getString(attributesIndex)
                )
                finishDigest(digest, digestOutput)
                if (expected.digest0[expectedIndex] != readLong(digestOutput, 0) ||
                    expected.digest1[expectedIndex] != readLong(digestOutput, 8) ||
                    expected.digest2[expectedIndex] != readLong(digestOutput, 16) ||
                    expected.digest3[expectedIndex] != readLong(digestOutput, 24)
                ) {
                    valid = false
                }

                val expectedTeamId = expected.teamIdAt(expectedIndex)
                if (expectedTeamId != currentTeamId) {
                    if (!allowRosterCorrections) {
                        valid = false
                    } else {
                        val oldLevel = expectedTeamId?.let { expectedTrainingCenterLevels[it] } ?: 1
                        val newLevel = currentTeamId?.let { currentTrainingCenterLevels[it] } ?: 1
                        if (oldLevel != newLevel) corrections.add(currentId)
                    }
                }
                expectedIndex++
            }
        }

        if (rowsInBatch == 0) break
        check(lastSeenId == null || requireNotNull(batchLastId) > lastSeenId!!) {
            "Monthly evolution compact validation keyset did not advance after player id $lastSeenId."
        }
        lastSeenId = batchLastId
        if (rowsInBatch < MONTHLY_EVOLUTION_VALIDATION_BATCH_SIZE) break
    }

    if (expectedIndex != expected.size) valid = false
    return MonthlyEvolutionRosterValidation(valid, corrections, currentCount)
}

private fun updateMonthlyEvolutionStateDigest(
    digest: MessageDigest,
    id: Long,
    age: Int,
    position: String,
    force: Int,
    potential: Int,
    minutosJogados: Int,
    mediaNotas: Double,
    focoTreino: String?,
    atributosJson: String?,
    atributosStorage: String
) {
    digest.reset()
    digest.updateLongValue(id)
    digest.updateIntValue(age)
    digest.updateStringValue(position)
    digest.updateIntValue(force)
    digest.updateIntValue(potential)
    digest.updateIntValue(minutosJogados)
    digest.updateLongValue(java.lang.Double.doubleToLongBits(mediaNotas))
    digest.updateNullableStringValue(focoTreino)
    digest.updateNullableStringValue(atributosJson)
    digest.updateStringValue(atributosStorage)
}

private fun finishDigest(digest: MessageDigest, output: ByteArray) {
    check(digest.digest(output, 0, output.size) == output.size) {
        "SHA-256 monthly evolution digest length mismatch."
    }
}

private fun MessageDigest.updateIntValue(value: Int) {
    val scratch = monthlyEvolutionDigestScratch.get()
    scratch[0] = (value ushr 24).toByte()
    scratch[1] = (value ushr 16).toByte()
    scratch[2] = (value ushr 8).toByte()
    scratch[3] = value.toByte()
    update(scratch, 0, 4)
}

private fun MessageDigest.updateLongValue(value: Long) {
    val scratch = monthlyEvolutionDigestScratch.get()
    scratch[0] = (value ushr 56).toByte()
    scratch[1] = (value ushr 48).toByte()
    scratch[2] = (value ushr 40).toByte()
    scratch[3] = (value ushr 32).toByte()
    scratch[4] = (value ushr 24).toByte()
    scratch[5] = (value ushr 16).toByte()
    scratch[6] = (value ushr 8).toByte()
    scratch[7] = value.toByte()
    update(scratch, 0, 8)
}

private fun MessageDigest.updateNullableStringValue(value: String?) {
    val scratch = monthlyEvolutionDigestScratch.get()
    scratch[0] = if (value == null) 0.toByte() else 1.toByte()
    update(scratch, 0, 1)
    if (value != null) updateStringValue(value)
}

private fun MessageDigest.updateStringValue(value: String) {
    updateIntValue(value.length)
    if (value.isEmpty()) return

    val scratch = monthlyEvolutionDigestScratch.get()
    var charIndex = 0
    while (charIndex < value.length) {
        var byteIndex = 0
        while (charIndex < value.length && byteIndex + 1 < scratch.size) {
            val code = value[charIndex].code
            scratch[byteIndex++] = (code ushr 8).toByte()
            scratch[byteIndex++] = code.toByte()
            charIndex++
        }
        update(scratch, 0, byteIndex)
    }
}

private fun readLong(bytes: ByteArray, offset: Int): Long {
    var value = 0L
    for (index in 0 until 8) {
        value = (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
    }
    return value
}
