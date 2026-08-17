package com.example.support

import com.example.data.FixtureScheduleValidator
import com.example.data.GameCalendar
import com.example.data.GameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

/**
 * Invariantes de carreira compartilhados pelos testes integrados.
 *
 * O objetivo é detectar corrupção estrutural cedo, sem repetir a mesma bateria de asserts
 * em cada cenário de temporada, persistência ou competição.
 */
object CareerInvariantAssertions {

    suspend fun assertRepositorySeason(
        repository: GameRepository,
        season: Int,
        minimumRosterTeamIds: Set<Long> = emptySet(),
        minimumRosterSize: Int = 16,
        maximumRosterSize: Int = 35,
        requireGoalkeeperForRosterTeams: Boolean = true
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
            "Todo jogador deve pertencer a um clube existente ou ser Agente Livre (teamId=null)",
            players.all { it.teamId == null || it.teamId in validTeamIds }
        )
        assertTrue(
            "Duração de contrato não pode ser negativa",
            players.all { it.contractDurationWeeks >= 0 }
        )
        assertTrue(
            "Agente Livre não pode permanecer marcado como emprestado",
            players.filter { it.teamId == null }.none { it.isOnLoan }
        )

        val activeLoans = repository.getActiveLoans()
        val activeLoanPlayerIds = activeLoans.map { it.playerId }
        assertEquals(
            "Um jogador não pode possuir dois empréstimos ativos",
            activeLoanPlayerIds.size,
            activeLoanPlayerIds.toSet().size
        )
        activeLoans.forEach { loan ->
            val player = players.firstOrNull { it.id == loan.playerId }
            assertNotNull("Empréstimo ativo deve apontar para jogador existente", player)
            requireNotNull(player)
            assertTrue("Empréstimo ativo deve possuir duração restante positiva", loan.remainingWeeks > 0)
            assertTrue("Clube proprietário do empréstimo deve existir", loan.ownerTeamId in validTeamIds)
            assertTrue("Clube tomador do empréstimo deve existir", loan.borrowerTeamId in validTeamIds)
            assertTrue("Jogador emprestado deve permanecer marcado como empréstimo", player.isOnLoan)
            assertEquals("Jogador emprestado deve pertencer ao tomador", loan.borrowerTeamId, player.teamId)
            assertEquals("Jogador emprestado deve preservar proprietário original", loan.ownerTeamId, player.originalTeamId)
        }

        assertTrue(
            "Todos os fixtures consultados para a temporada devem pertencer à própria temporada",
            fixtures.all { it.season == season }
        )
        assertTrue(
            "Nenhum fixture pode ficar fora das ${GameCalendar.WEEKS_PER_SEASON} semanas canônicas",
            fixtures.all { it.week in 1..GameCalendar.WEEKS_PER_SEASON }
        )
        assertTrue(
            "Fixtures persistidos devem referenciar somente clubes persistidos",
            fixtures.all { it.homeTeamId in validTeamIds && it.awayTeamId in validTeamIds }
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
            val playersByTeam = players.filter { it.teamId != null }.groupBy { it.teamId }
            minimumRosterTeamIds.forEach { teamId ->
                assertTrue("Clube $teamId deve continuar existindo", teamId in validTeamIds)
                val roster = playersByTeam[teamId].orEmpty()
                assertTrue(
                    "Clube $teamId deve possuir pelo menos $minimumRosterSize atletas",
                    roster.size >= minimumRosterSize
                )
                assertTrue(
                    "Clube $teamId não pode ultrapassar $maximumRosterSize atletas",
                    roster.size <= maximumRosterSize
                )
                if (requireGoalkeeperForRosterTeams) {
                    assertTrue(
                        "Clube $teamId deve possuir ao menos um goleiro utilizável",
                        roster.any { it.position == "GOL" }
                    )
                }
            }
        }
    }
}
