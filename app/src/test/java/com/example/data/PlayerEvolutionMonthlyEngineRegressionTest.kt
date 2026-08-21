package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerEvolutionMonthlyEngineRegressionTest {

    @Test
    fun `low allocation serializer round trips every attribute exactly`() {
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

        val json = PlayerEvolutionMonthlyEngine.serializeAttributes(attributes)
        assertEquals(attributes, AtributosConverter.jsonToAtributos(json))
    }

    @Test
    fun `monthly engine preserves player count and result identity envelope`() {
        val team = Team(
            id = 42L,
            name = "Regression FC",
            city = "Test",
            state = "TS",
            country = "Brasil",
            division = 1,
            rating = 70,
            trainingCenterLevel = 3
        )
        val players = listOf(
            Player(id = 1001L, teamId = team.id, name = "A", age = 24, position = "MEI", force = 70),
            Player(id = 1002L, teamId = team.id, name = "B", age = 34, position = "ZAG", force = 70)
        )

        val results = PlayerEvolutionMonthlyEngine.process(players, mapOf(team.id to team), "TEST_PERIOD")

        assertEquals(players.size, results.size)
        assertEquals(players.map { it.id }, results.map { it.player.id })
        assertEquals(players.map { it.teamId }, results.map { it.player.teamId })
        assertEquals(players.map { it.contractDurationWeeks }, results.map { it.player.contractDurationWeeks })
        assertEquals(players.map { it.salary }, results.map { it.player.salary })
    }
}
