package com.example.data

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
