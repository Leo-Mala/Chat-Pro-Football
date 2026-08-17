package com.example.support

import com.example.data.FixtureScheduleValidator
import com.example.data.GameCalendar
import com.example.data.GameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Invariantes de carreira compartilhados pelos testes integrados da Fase 9.7.
 *
 * O objetivo é detectar corrupção estrutural cedo, sem repetir a mesma bateria de asserts
 * em cada cenário de temporada, persistência ou competição.
 */
object CareerInvariantAssertions {

    suspend fun assertRepositorySeason(
        repository: GameRepository,
        season: Int,
        minimumRosterTeamIds: Set<Long> = emptySet(),
        minimumRosterSize: Int = 16
    ) {
        val teams = repository.getAllTeams()
        val players = repository.getAllPlayers()
        val fixtures = repository.getFixturesForSeason(season)

        assertTrue("A carreira deve possuir clubes persistidos", teams.isNotEmpty())
        assertEquals(
            "IDs de clubes devem permanecer únicos",
            teams.size,
            teams.map { it.id }.toSet().size
        )
        assertEquals(
            "IDs de jogadores devem permanecer únicos",
            players.size,
            players.map { it.id }.toSet().size
        )

        val validTeamIds = teams.map { it.id }.toSet()
        assertTrue(
            "Todo jogador deve pertencer a um clube existente ou ser Agente Livre (teamId=0)",
            players.all { it.teamId == 0L || it.teamId in validTeamIds }
        )

        assertTrue(
            "Todos os fixtures consultados para a temporada devem pertencer à própria temporada",
            fixtures.all { it.season == season }
        )
        assertTrue(
            "Nenhum fixture pode ficar fora das ${GameCalendar.WEEKS_PER_SEASON} semanas canônicas",
            fixtures.all { it.week in 1..GameCalendar.WEEKS_PER_SEASON }
        )

        // Centraliza semana/slot, confronto contra si mesmo, fixture duplicado e clube repetido
        // no mesmo slot. MIDWEEK + WEEKEND na mesma semana continua permitido.
        FixtureScheduleValidator.requireValid(fixtures)

        fixtures.forEach { fixture ->
            assertEquals(
                "Placar deve ser persistido atomicamente para os dois lados do fixture ${fixture.id}",
                fixture.homeScore == null,
                fixture.awayScore == null
            )
            if (fixture.isPlayed) {
                assertNotNull("Fixture jogado deve possuir gols do mandante", fixture.homeScore)
                assertNotNull("Fixture jogado deve possuir gols do visitante", fixture.awayScore)
            }
        }

        if (minimumRosterTeamIds.isNotEmpty()) {
            val rosterCounts = players
                .filter { it.teamId != 0L }
                .groupingBy { it.teamId }
                .eachCount()
            minimumRosterTeamIds.forEach { teamId ->
                assertTrue("Clube $teamId deve continuar existindo", teamId in validTeamIds)
                assertTrue(
                    "Clube $teamId deve iniciar a temporada com pelo menos $minimumRosterSize atletas",
                    rosterCounts.getOrDefault(teamId, 0) >= minimumRosterSize
                )
            }
        }
    }
}
