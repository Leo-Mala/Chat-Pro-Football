from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one anchor, found {count}\nANCHOR:\n{old[:300]}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) Lightweight controlled-roster expiry detector. This does not renew or mutate anything.
replace_once(
    "app/src/main/java/com/example/data/repository.kt",
    """    suspend fun getPlayerCountByTeam(teamId: Long?): Int = db.playerDao().getPlayerCountByTeam(teamId)\n    suspend fun getFreeAgents(): List<Player> = db.playerDao().getFreeAgents()\n""",
    """    suspend fun getPlayerCountByTeam(teamId: Long?): Int = db.playerDao().getPlayerCountByTeam(teamId)\n\n    /**\n     * Counts only non-loaned players currently owned by the controlled sporting roster whose\n     * contracts would expire at the next canonical weekly tick. Season auto-simulation uses this\n     * as a fail-safe pause point so it never silently consumes the manager's renewal decision.\n     * Manual week progression keeps the existing contract-expiry rule unchanged.\n     */\n    suspend fun getControlledRosterExpiringContractCount(teamId: Long): Int {\n        if (teamId <= 0L) return 0\n        return db.openHelper.readableDatabase.query(\n            \"\"\"\n            SELECT COUNT(*) AS expiringCount\n            FROM players\n            WHERE teamId = ?\n              AND isOnLoan = 0\n              AND contractDurationWeeks = 1\n            \"\"\".trimIndent(),\n            arrayOf<Any>(teamId)\n        ).use { cursor ->\n            check(cursor.moveToFirst())\n            cursor.getInt(cursor.getColumnIndexOrThrow(\"expiringCount\"))\n        }\n    }\n\n    suspend fun getFreeAgents(): List<Player> = db.playerDao().getFreeAgents()\n"""
)

# 2) Season simulation must stop before silently expiring controlled-club players.
replace_once(
    "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt",
    """internal fun shouldStopSeasonSimulation(targetSeason: Int, currentSeason: Int): Boolean =\n    currentSeason != targetSeason\n""",
    """internal fun shouldStopSeasonSimulation(targetSeason: Int, currentSeason: Int): Boolean =\n    currentSeason != targetSeason\n\ninternal fun shouldPauseSeasonSimulationForExpiringContracts(expiringContractCount: Int): Boolean =\n    expiringContractCount > 0\n"""
)

replace_once(
    "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt",
    """                            val currentWeekNum = save.currentWeek\n                            _simulationCurrentWeek.value = currentWeekNum\n                            \n                            val weekFixtures = repo.getFixturesForWeek(save.currentSeason, currentWeekNum)\n""",
    """                            val currentWeekNum = save.currentWeek\n                            _simulationCurrentWeek.value = currentWeekNum\n\n                            // Auto-simulation must never make a contract-renewal decision for the\n                            // human manager. Stop before playing/closing the week so no fixture,\n                            // finance or contract mutation for this week has been committed yet.\n                            val expiringControlledContracts =\n                                repo.getControlledRosterExpiringContractCount(save.playerTeamId)\n                            if (shouldPauseSeasonSimulationForExpiringContracts(expiringControlledContracts)) {\n                                val contractLabel = if (expiringControlledContracts == 1) \"contrato vence\" else \"contratos vencem\"\n                                val pauseMessage =\n                                    \"$expiringControlledContracts $contractLabel ao fim da semana. Renove os contratos ou avance a semana manualmente.\"\n                                _simulationCompetitionName.value = \"Simulação pausada\"\n                                _simulationMatchInfo.value = pauseMessage\n                                _simulationLogs.value = (\n                                    listOf(\"Temp. ${save.currentSeason} | Sem. $currentWeekNum | Simulação pausada: $pauseMessage\") +\n                                        _simulationLogs.value\n                                    ).take(25)\n                                break\n                            }\n                            \n                            val weekFixtures = repo.getFixturesForWeek(save.currentSeason, currentWeekNum)\n"""
)

# 3) Always put the best eligible natural/emergency goalkeeper in the XI first.
replace_once(
    "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt",
    """    fun getStartingXIForTeam(\n        players: List<Player>,\n        teamId: Long = 0L,\n        teamRating: Int = 70,\n        teamName: String = \"Time\",\n        country: String = \"Brasil\"\n    ): List<Player> {\n        val available = players.filter { it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }.toMutableList()\n        if (available.isEmpty()) {\n            val generated = DefaultData.generateRosterForTeam(teamId, teamRating, teamName, country)\n            available.addAll(generated)\n        }\n        val chosenStarters = available.filter { it.isStarter }\n        val startingXI = chosenStarters.take(11).toMutableList()\n        if (startingXI.size < 11) {\n            val remaining = available.filter { it !in startingXI }.sortedByDescending { it.force }.take(11 - startingXI.size)\n            startingXI.addAll(remaining)\n        }\n        return startingXI\n    }\n""",
    """    fun getStartingXIForTeam(\n        players: List<Player>,\n        teamId: Long = 0L,\n        teamRating: Int = 70,\n        teamName: String = \"Time\",\n        country: String = \"Brasil\"\n    ): List<Player> {\n        val available = players.filter { it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }.toMutableList()\n        if (available.isEmpty()) {\n            val generated = DefaultData.generateRosterForTeam(teamId, teamRating, teamName, country)\n            available.addAll(generated)\n        }\n\n        val selectedGoalkeeper = GameEngine.selectMatchGoalkeeper(available)\n        val startingXI = mutableListOf<Player>()\n        selectedGoalkeeper?.let(startingXI::add)\n\n        val chosenStarters = available.filter {\n            it.isStarter && it.id != selectedGoalkeeper?.id\n        }\n        startingXI.addAll(chosenStarters.take(11 - startingXI.size))\n        if (startingXI.size < 11) {\n            val selectedIds = startingXI.mapTo(hashSetOf()) { it.id }\n            val remaining = available\n                .filter { it.id !in selectedIds }\n                .sortedByDescending { it.force }\n                .take(11 - startingXI.size)\n            startingXI.addAll(remaining)\n        }\n        return startingXI.take(11)\n    }\n"""
)

replace_once(
    "app/src/main/java/com/example/data/GameEngine.kt",
    """object GameEngine {\n    // Classic formations and their tactical modifiers\n""",
    """object GameEngine {\n    /**\n     * Picks the goalkeeper used by the match engine. A natural eligible goalkeeper always wins.\n     * If none is available, the existing emergency-outfield fallback is preserved but made\n     * deterministic and skill-based instead of silently using the first player in list order.\n     * No persisted position or sporting data is changed.\n     */\n    internal fun selectMatchGoalkeeper(players: List<Player>): Player? {\n        val eligible = players.filter {\n            it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0\n        }\n        if (eligible.isEmpty()) return null\n\n        eligible\n            .asSequence()\n            .filter { it.position == \"GOL\" }\n            .sortedWith(compareByDescending<Player> { it.force }.thenBy { it.id })\n            .firstOrNull()\n            ?.let { return it }\n\n        return eligible.sortedWith(\n            compareByDescending<Player> { emergencyGoalkeeperScore(it) }\n                .thenByDescending { it.force }\n                .thenBy { it.id }\n        ).firstOrNull()\n    }\n\n    private fun emergencyGoalkeeperScore(player: Player): Double {\n        val attributes = player.getAtributosObject()\n        return attributes.reflexos * 0.35 +\n            attributes.posicionamento * 0.25 +\n            attributes.agilidade * 0.20 +\n            attributes.concentracao * 0.20\n    }\n\n    // Classic formations and their tactical modifiers\n"""
)

replace_once(
    "app/src/main/java/com/example/data/GameEngine.kt",
    """        val homeGK = activeHome.find { it.position == \"GOL\" } ?: activeHome.firstOrNull()\n        val awayGK = activeAway.find { it.position == \"GOL\" } ?: activeAway.firstOrNull()\n""",
    """        val homeGK = selectMatchGoalkeeper(activeHome)\n        val awayGK = selectMatchGoalkeeper(activeAway)\n"""
)

# 4) Preserve the exact SHA-256 byte stream while avoiding millions of MessageDigest.update(byte)
# calls on Android ART/provider. Also align validation keyset pages with the 4,096-player prepare batch.
replace_once(
    "app/src/main/java/com/example/data/MonthlyEvolutionUniverseCommitment.kt",
    """import java.security.MessageDigest\n\n/**\n""",
    """import java.security.MessageDigest\n\nprivate const val MONTHLY_EVOLUTION_VALIDATION_BATCH_SIZE = 4096\nprivate const val MONTHLY_EVOLUTION_DIGEST_SCRATCH_BYTES = 8192\nprivate val monthlyEvolutionDigestScratch = ThreadLocal.withInitial {\n    ByteArray(MONTHLY_EVOLUTION_DIGEST_SCRATCH_BYTES)\n}\n\n/**\n"""
)

p = Path("app/src/main/java/com/example/data/MonthlyEvolutionUniverseCommitment.kt")
text = p.read_text(encoding="utf-8")
if text.count("LIMIT 1024") != 2:
    raise SystemExit(f"Monthly commitment: expected 2 LIMIT 1024 anchors, found {text.count('LIMIT 1024')}")
text = text.replace("LIMIT 1024", "LIMIT $MONTHLY_EVOLUTION_VALIDATION_BATCH_SIZE")
if text.count("if (rowsInBatch < 1024) break") != 1:
    raise SystemExit("Monthly commitment: rowsInBatch 1024 anchor mismatch")
text = text.replace(
    "if (rowsInBatch < 1024) break",
    "if (rowsInBatch < MONTHLY_EVOLUTION_VALIDATION_BATCH_SIZE) break",
    1
)
p.write_text(text, encoding="utf-8")

replace_once(
    "app/src/main/java/com/example/data/MonthlyEvolutionUniverseCommitment.kt",
    """private fun MessageDigest.updateIntValue(value: Int) {\n    update((value ushr 24).toByte())\n    update((value ushr 16).toByte())\n    update((value ushr 8).toByte())\n    update(value.toByte())\n}\n\nprivate fun MessageDigest.updateLongValue(value: Long) {\n    update((value ushr 56).toByte())\n    update((value ushr 48).toByte())\n    update((value ushr 40).toByte())\n    update((value ushr 32).toByte())\n    update((value ushr 24).toByte())\n    update((value ushr 16).toByte())\n    update((value ushr 8).toByte())\n    update(value.toByte())\n}\n\nprivate fun MessageDigest.updateNullableStringValue(value: String?) {\n    if (value == null) {\n        update(0.toByte())\n    } else {\n        update(1.toByte())\n        updateStringValue(value)\n    }\n}\n\nprivate fun MessageDigest.updateStringValue(value: String) {\n    updateIntValue(value.length)\n    for (char in value) {\n        val code = char.code\n        update((code ushr 8).toByte())\n        update(code.toByte())\n    }\n}\n""",
    """private fun MessageDigest.updateIntValue(value: Int) {\n    val scratch = monthlyEvolutionDigestScratch.get()\n    scratch[0] = (value ushr 24).toByte()\n    scratch[1] = (value ushr 16).toByte()\n    scratch[2] = (value ushr 8).toByte()\n    scratch[3] = value.toByte()\n    update(scratch, 0, 4)\n}\n\nprivate fun MessageDigest.updateLongValue(value: Long) {\n    val scratch = monthlyEvolutionDigestScratch.get()\n    scratch[0] = (value ushr 56).toByte()\n    scratch[1] = (value ushr 48).toByte()\n    scratch[2] = (value ushr 40).toByte()\n    scratch[3] = (value ushr 32).toByte()\n    scratch[4] = (value ushr 24).toByte()\n    scratch[5] = (value ushr 16).toByte()\n    scratch[6] = (value ushr 8).toByte()\n    scratch[7] = value.toByte()\n    update(scratch, 0, 8)\n}\n\nprivate fun MessageDigest.updateNullableStringValue(value: String?) {\n    val scratch = monthlyEvolutionDigestScratch.get()\n    scratch[0] = if (value == null) 0 else 1\n    update(scratch, 0, 1)\n    if (value != null) updateStringValue(value)\n}\n\nprivate fun MessageDigest.updateStringValue(value: String) {\n    updateIntValue(value.length)\n    if (value.isEmpty()) return\n\n    val scratch = monthlyEvolutionDigestScratch.get()\n    var charIndex = 0\n    while (charIndex < value.length) {\n        var byteIndex = 0\n        while (charIndex < value.length && byteIndex + 1 < scratch.size) {\n            val code = value[charIndex].code\n            scratch[byteIndex++] = (code ushr 8).toByte()\n            scratch[byteIndex++] = code.toByte()\n            charIndex++\n        }\n        update(scratch, 0, byteIndex)\n    }\n}\n"""
)

# Regression: contract guard detects the exact controlled-roster rows that the weekly SQL would release.
Path("app/src/test/java/com/example/ui/viewmodel/SeasonSimulationContractGuardRegressionTest.kt").write_text(r'''package com.example.ui.viewmodel

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeasonSimulationContractGuardRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `season simulation pauses before controlled roster contract expiry`() = runBlocking {
        val user = Team(id = 1L, name = "Usuário", city = "BH", state = "MG", division = 1, isPlayerControlled = true)
        val other = Team(id = 2L, name = "Outro", city = "SP", state = "SP", division = 1)
        repository.saveTeams(listOf(user, other))
        repository.savePlayers(
            listOf(
                Player(id = 101L, teamId = user.id, name = "Expira", age = 25, position = "ZAG", force = 80, contractDurationWeeks = 1),
                Player(id = 102L, teamId = user.id, name = "Seguro", age = 25, position = "MEI", force = 80, contractDurationWeeks = 2),
                Player(id = 103L, teamId = user.id, name = "Emprestado", age = 25, position = "ATA", force = 80, contractDurationWeeks = 1, isOnLoan = true, originalTeamId = other.id),
                Player(id = 201L, teamId = other.id, name = "CPU Expira", age = 25, position = "ZAG", force = 80, contractDurationWeeks = 1)
            )
        )

        val expiring = repository.getControlledRosterExpiringContractCount(user.id)
        assertEquals(1, expiring)
        assertTrue(shouldPauseSeasonSimulationForExpiringContracts(expiring))
        assertFalse(shouldPauseSeasonSimulationForExpiringContracts(0))

        val before = repository.getPlayersByTeam(user.id).map { it.id }.toSet()
        assertEquals(setOf(101L, 102L, 103L), before)
        // Detection is read-only: no player is released merely by checking the guard.
        assertEquals(before, repository.getPlayersByTeam(user.id).map { it.id }.toSet())
    }
}
''', encoding="utf-8")

# Regression: natural GK wins; otherwise the best emergency candidate is selected, not list order.
Path("app/src/test/java/com/example/data/EmergencyGoalkeeperSelectionRegressionTest.kt").write_text(r'''package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyGoalkeeperSelectionRegressionTest {
    @Test
    fun `natural eligible goalkeeper is always preferred`() {
        val field = Player(id = 1L, teamId = 1L, name = "Linha", age = 25, position = "ZAG", force = 99, defense = 99, pace = 99)
        val goalkeeper = Player(id = 2L, teamId = 1L, name = "Goleiro", age = 29, position = "GOL", force = 70)
        assertEquals(goalkeeper.id, GameEngine.selectMatchGoalkeeper(listOf(field, goalkeeper))?.id)
    }

    @Test
    fun `suspended goalkeeper falls back to best deterministic emergency candidate`() {
        val suspendedGoalkeeper = Player(
            id = 10L, teamId = 1L, name = "Suspenso", age = 30, position = "GOL", force = 95,
            suspensionWeeksRemaining = 1
        )
        val arbitraryFirst = Player(
            id = 11L, teamId = 1L, name = "Primeiro", age = 25, position = "ATA", force = 90,
            defense = 20, pace = 40
        )
        val bestEmergency = Player(
            id = 12L, teamId = 1L, name = "Emergência", age = 25, position = "ZAG", force = 78,
            defense = 92, pace = 70
        )

        assertEquals(
            bestEmergency.id,
            GameEngine.selectMatchGoalkeeper(listOf(arbitraryFirst, bestEmergency, suspendedGoalkeeper))?.id
        )
        assertEquals(
            bestEmergency.id,
            GameEngine.selectMatchGoalkeeper(listOf(bestEmergency, arbitraryFirst, suspendedGoalkeeper))?.id
        )
    }
}
''', encoding="utf-8")

# Regression: bulk digest updates must remain byte-for-byte compatible with the pre-optimization encoding.
Path("app/src/test/java/com/example/data/MonthlyEvolutionDigestCompatibilityRegressionTest.kt").write_text(r'''package com.example.data

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthlyEvolutionDigestCompatibilityRegressionTest {
    @Test
    fun `buffered monthly digest is identical to legacy bytewise encoding`() {
        val player = Player(
            id = 987654321L,
            teamId = 42L,
            name = "Álvaro 💡",
            age = 27,
            position = "MEI",
            force = 88,
            potential = 93,
            minutosJogados = 1234,
            mediaNotas = 7.375,
            focoTreino = "FINALIZAÇÃO",
            atributosJson = "{\"visão\":\"ação💡\"}"
        )
        val storage = "{\"unicode\":\"çãõ漢字💡\",\"v\":123}"

        val commitment = MonthlyEvolutionCommitmentBuilder(1).also { it.add(player, storage) }.build()
        val expected = legacyDigest(player, storage)

        assertEquals(readLong(expected, 0), commitment.digest0.single())
        assertEquals(readLong(expected, 8), commitment.digest1.single())
        assertEquals(readLong(expected, 16), commitment.digest2.single())
        assertEquals(readLong(expected, 24), commitment.digest3.single())
    }

    private fun legacyDigest(player: Player, atributosStorage: String): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        fun updateInt(value: Int) {
            digest.update((value ushr 24).toByte())
            digest.update((value ushr 16).toByte())
            digest.update((value ushr 8).toByte())
            digest.update(value.toByte())
        }
        fun updateLong(value: Long) {
            digest.update((value ushr 56).toByte())
            digest.update((value ushr 48).toByte())
            digest.update((value ushr 40).toByte())
            digest.update((value ushr 32).toByte())
            digest.update((value ushr 24).toByte())
            digest.update((value ushr 16).toByte())
            digest.update((value ushr 8).toByte())
            digest.update(value.toByte())
        }
        fun updateString(value: String) {
            updateInt(value.length)
            for (char in value) {
                val code = char.code
                digest.update((code ushr 8).toByte())
                digest.update(code.toByte())
            }
        }
        fun updateNullableString(value: String?) {
            if (value == null) digest.update(0.toByte()) else {
                digest.update(1.toByte())
                updateString(value)
            }
        }

        digest.reset()
        updateLong(player.id)
        updateInt(player.age)
        updateString(player.position)
        updateInt(player.force)
        updateInt(player.potential)
        updateInt(player.minutosJogados)
        updateLong(java.lang.Double.doubleToLongBits(player.mediaNotas))
        updateNullableString(player.focoTreino)
        updateNullableString(player.atributosJson)
        updateString(atributosStorage)
        return digest.digest()
    }

    private fun readLong(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in 0 until 8) value = (value shl 8) or (bytes[offset + index].toLong() and 0xffL)
        return value
    }
}
''', encoding="utf-8")

print("video regression patch applied")
