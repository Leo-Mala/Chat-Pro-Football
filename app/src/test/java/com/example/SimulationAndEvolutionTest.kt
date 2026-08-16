package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class SimulationAndEvolutionTest {

    @Test
    fun test6_1_MatchSimulationIntegration() {
        val homeTeam = Team(id = 1L, name = "Flamengo", city = "Rio de Janeiro", state = "RJ", division = 1)
        val awayTeam = Team(id = 2L, name = "Palmeiras", city = "São Paulo", state = "SP", division = 1)

        val homePlayers = listOf(
            Player(id = 1, teamId = 1, name = "Goleiro H", age = 25, position = "GOL", force = 80),
            Player(id = 2, teamId = 1, name = "Zagueiro H", age = 27, position = "ZAG", force = 78),
            Player(id = 3, teamId = 1, name = "Meia H", age = 24, position = "MEI", force = 82),
            Player(id = 4, teamId = 1, name = "Atacante H", age = 22, position = "ATA", force = 85)
        )

        val awayPlayers = listOf(
            Player(id = 5, teamId = 2, name = "Goleiro A", age = 28, position = "GOL", force = 81),
            Player(id = 6, teamId = 2, name = "Zagueiro A", age = 26, position = "ZAG", force = 79),
            Player(id = 7, teamId = 2, name = "Meia A", age = 25, position = "MEI", force = 80),
            Player(id = 8, teamId = 2, name = "Atacante A", age = 23, position = "ATA", force = 83)
        )

        val output = GameEngine.simulateMatchDetailedWithStats(
            homeTeam = homeTeam,
            awayTeam = awayTeam,
            homePlayers = homePlayers,
            awayPlayers = awayPlayers,
            homeTactics = "4-3-3",
            homeStyle = "Ofensiva",
            awayTactics = "4-4-2",
            awayStyle = "Equilibrada",
            isRivalry = true,
            randomSeed = 12345L
        )

        // Verificação de posse de bola
        assertTrue("Posse de bola da casa deve estar entre 25 e 75%", output.homePossession in 25..75)
        assertEquals(100, output.homePossession + output.awayPossession)

        // Verificação de finalizações
        assertTrue("Chutes do time da casa devem ser > 0", output.homeShots > 0)
        assertTrue("Chutes do time visitante devem ser > 0", output.awayShots > 0)

        // Verificação de notas dos jogadores (0.0 a 10.0)
        for ((playerId, rating) in output.playerRatings) {
            assertTrue("Nota do jogador $playerId ($rating) deve estar entre 0.0 e 10.0", rating in 0.0..10.0)
        }
    }

    @Test
    fun test6_2_YoungPlayerEvolution() {
        val basePlayer = Player(
            id = 101,
            teamId = 1,
            name = "Promessa Base",
            age = 19,
            position = "ATA",
            force = 65,
            finishing = 65,
            passing = 65,
            pace = 65,
            strength = 65,
            vision = 65,
            defense = 65,
            potential = 88,
            minutosJogados = 300, // > 240m (fator 1.2)
            mediaNotas = 8.0,      // > 7.5 (fator 1.3)
            focoTreino = "FINALIZACAO"
        )
        val initialAttr = basePlayer.getAtributosObject()
        val initialJson = AtributosConverter.atributosToJson(initialAttr)
        val initialForce = CalculadoraNota.calcularNota(Posicao.ATACANTE, initialAttr)
        val youngPlayer = basePlayer.copy(atributosJson = initialJson, force = initialForce)

        val teamMap = mapOf(1L to Team(id = 1L, name = "Clube A", city = "SP", state = "SP", division = 1, trainingCenterLevel = 3))

        val results = PlayerEvolutionSystem.processMonthlyEvolution(
            players = listOf(youngPlayer),
            teamsMap = teamMap,
            periodDate = "2026-02"
        )

        val res = results.first()
        assertTrue("Jogador jovem com alto potencial deve evoluir ou manter nota (antigo: $initialForce, novo: ${res.player.force})", res.player.force >= initialForce)
        assertTrue("Atributos devem respeitar o limite de potencial (88)", res.newAttributes.finalizacao <= 88)
    }

    @Test
    fun test6_3_OldPlayerDecline() {
        val basePlayer = Player(
            id = 102,
            teamId = 1,
            name = "Veterano 34 Anos",
            age = 34,
            position = "ZAG",
            force = 75,
            potential = 80,
            minutosJogados = 50,
            mediaNotas = 5.0
        )
        val initialAttr = basePlayer.getAtributosObject()
        val initialJson = AtributosConverter.atributosToJson(initialAttr)
        val initialForce = CalculadoraNota.calcularNota(Posicao.ZAGUEIRO, initialAttr)
        val veteran = basePlayer.copy(atributosJson = initialJson, force = initialForce)

        val teamMap = mapOf(1L to Team(id = 1L, name = "Clube A", city = "SP", state = "SP", division = 1, trainingCenterLevel = 1))

        val results = PlayerEvolutionSystem.processMonthlyEvolution(
            players = listOf(veteran),
            teamsMap = teamMap,
            periodDate = "2026-02"
        )

        val res = results.first()

        // Verifica que o fator de idade para >33 anos reduz os atributos físicos ou os mantêm sem ganho desproporcional
        assertTrue("Jogador veterano não deve ganhar força (force antigo: $initialForce, force novo: ${res.player.force})", res.player.force <= initialForce)
    }

    @Test
    fun test6_4_TrainingFocusFactor() {
        val focusTech = PlayerEvolutionSystem.calculateFocusFactor("finalizacao", "FINALIZACAO")
        assertEquals("Foco exato no atributo deve dar fator 2.0", 2.0, focusTech, 0.01)

        val focusCategory = PlayerEvolutionSystem.calculateFocusFactor("velocidade", "FISICO")
        assertEquals("Foco na categoria FÍSICO deve dar fator 2.0 para velocidade", 2.0, focusCategory, 0.01)

        val focusMismatched = PlayerEvolutionSystem.calculateFocusFactor("desarme", "FINALIZACAO")
        assertEquals("Foco diferente deve dar fator 0.5", 0.5, focusMismatched, 0.01)

        val focusNone = PlayerEvolutionSystem.calculateFocusFactor("passe", "NENHUM")
        assertEquals("Foco NENHUM deve dar fator 1.0", 1.0, focusNone, 0.01)
    }
}
