package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtributosConverterSeedPerformanceTest {

    @Test
    fun `direct attribute serialization preserves all values and round trips`() {
        val attributes = Atributos(
            reflexos = 51,
            pegada = 52,
            umContraUm = 53,
            saidaDeGol = 54,
            lancamento = 55,
            desarme = 56,
            marcacao = 57,
            cabeceio = 58,
            passeCurto = 59,
            cruzamento = 60,
            drible = 61,
            passe = 62,
            primeiroToque = 63,
            finalizacao = 64,
            chuteDeLonge = 65,
            controleBola = 66,
            posicionamento = 67,
            concentracao = 68,
            sangueFrio = 69,
            antecipacao = 70,
            bravura = 71,
            trabalhoEquipe = 72,
            decisao = 73,
            semBola = 74,
            visaoJogo = 75,
            criatividade = 76,
            agressividade = 77,
            lideranca = 78,
            regularidade = 79,
            agilidade = 80,
            impulsao = 81,
            forca = 82,
            velocidade = 83,
            aceleracao = 84,
            resistencia = 85
        )
        val converter = AtributosConverter()

        val encoded = converter.fromAtributos(attributes)
        assertNotNull(encoded)
        assertTrue(encoded!!.startsWith("{\"reflexos\":51,"))
        assertTrue(encoded.endsWith("\"resistencia\":85}"))
        assertEquals(attributes, converter.toAtributos(encoded))
    }

    @Test
fun `default attributes use compact lossless persistence`() {
    val converter = AtributosConverter()
    val encoded = converter.fromAtributos(Atributos())
    assertEquals("{}", encoded)
    assertEquals(Atributos(), converter.toAtributos(encoded))
}

    @Test
    fun `career scale serialization avoids old multi minute allocation profile`() {
        val converter = AtributosConverter()
        val attributes = Atributos()
        val startedAt = System.nanoTime()
        var checksum = 0L

        repeat(75_720) {
            checksum += requireNotNull(converter.fromAtributos(attributes)).length
        }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
        assertTrue(checksum > 0L)
        assertTrue("75,720 attribute serializations took ${elapsedMs}ms", elapsedMs < 15_000L)
    }
}
