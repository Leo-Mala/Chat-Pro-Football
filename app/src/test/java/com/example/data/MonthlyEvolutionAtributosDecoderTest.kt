package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthlyEvolutionAtributosDecoderTest {

    @Test
    fun `canonical compact payload preserves every attribute`() {
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
        val encoded = requireNotNull(AtributosConverter.atributosToJson(attributes))

        assertEquals(attributes, MonthlyEvolutionAtributosDecoder.decode(encoded))
    }

    @Test
    fun `compact default payload remains lossless`() {
        assertEquals(Atributos(), MonthlyEvolutionAtributosDecoder.decode("{}"))
    }

    @Test
    fun `legacy reordered sparse payload keeps existing fallback semantics`() {
        val legacy = "{\"forca\":77, \"reflexos\":61}"

        assertEquals(
            AtributosConverter.jsonToAtributos(legacy),
            MonthlyEvolutionAtributosDecoder.decode(legacy)
        )
        assertEquals(61, requireNotNull(MonthlyEvolutionAtributosDecoder.decode(legacy)).reflexos)
        assertEquals(77, requireNotNull(MonthlyEvolutionAtributosDecoder.decode(legacy)).forca)
    }

    @Test
    fun `malformed and null payloads preserve converter behavior`() {
        val malformed = "not-json"

        assertEquals(
            AtributosConverter.jsonToAtributos(malformed),
            MonthlyEvolutionAtributosDecoder.decode(malformed)
        )
        assertNull(MonthlyEvolutionAtributosDecoder.decode(null))
    }

    @Test
    fun `non canonical integer overflow falls back without crashing`() {
        val overflow = "{\"reflexos\":999999999999999999999}"

        assertEquals(
            AtributosConverter.jsonToAtributos(overflow),
            MonthlyEvolutionAtributosDecoder.decode(overflow)
        )
    }
}
