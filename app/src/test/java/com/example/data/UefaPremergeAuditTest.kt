package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UefaPremergeAuditTest {

    @Test
    fun `Champions and Europa never generate three consecutive equal venues`() {
        val fields = UefaQualificationRules.selectLeaguePhaseFields(uefaCandidates())

        assertNoThreeConsecutiveVenues(
            fields.championsLeague.map { it.team },
            UefaCompetitionSystem.CHAMPIONS_LEAGUE,
            UefaCompetitionSystem.CHAMPIONS_EUROPA_LEAGUE_WEEKS
        )
        assertNoThreeConsecutiveVenues(
            fields.europaLeague.map { it.team },
            UefaCompetitionSystem.EUROPA_LEAGUE,
            UefaCompetitionSystem.CHAMPIONS_EUROPA_LEAGUE_WEEKS
        )
        assertNoThreeConsecutiveVenues(
            fields.conferenceLeague.map { it.team },
            UefaCompetitionSystem.CONFERENCE_LEAGUE,
            UefaCompetitionSystem.CONFERENCE_LEAGUE_WEEKS
        )
    }

    @Test
    fun `round of 16 order preserves quarterfinal seeds one to four and semifinal seeds one and two`() {
        val topEight = (1L..8L).toList()
        val playoffWinners = (101L..108L).toList()

        val pairs = UefaCompetitionSystem.buildRoundOf16Pairs(topEight, playoffWinners)

        // O segundo elemento recebe a volta em casa nas oitavas.
        assertEquals(listOf(1L, 8L, 4L, 5L, 2L, 7L, 3L, 6L), pairs.map { it.second })
        assertEquals(listOf(108L, 101L, 105L, 104L, 107L, 102L, 106L, 103L), pairs.map { it.first })

        // progressAggregateRound agrupa caminhos de dois em dois e faz o primeiro caminho receber
        // a volta. Portanto as quartas preservam 1,4,2,3 e as semis preservam 1,2.
        val quarterfinalSeedPaths = pairs.map { it.second }.chunked(2).map { it.first() }
        assertEquals(listOf(1L, 4L, 2L, 3L), quarterfinalSeedPaths)
        assertEquals(setOf(1L, 2L, 3L, 4L), quarterfinalSeedPaths.toSet())

        val semifinalSeedPaths = quarterfinalSeedPaths.chunked(2).map { it.first() }
        assertEquals(listOf(1L, 2L), semifinalSeedPaths)
        assertFalse(1L in pairs.map { it.second }.take(4) && 2L in pairs.map { it.second }.take(4))
    }

    private fun assertNoThreeConsecutiveVenues(
        teams: List<Team>,
        competitionType: String,
        weeks: List<Int>
    ) {
        val fixtures = UefaCompetitionSystem.generateLeaguePhase(
            season = 2027,
            teams = teams,
            competitionType = competitionType
        )

        teams.forEach { team ->
            val pattern = weeks.map { week ->
                val fixture = fixtures.single {
                    it.week == week && (it.homeTeamId == team.id || it.awayTeamId == team.id)
                }
                if (fixture.homeTeamId == team.id) 'H' else 'A'
            }
            assertTrue(pattern.take(2).toSet().size == 2)
            assertTrue(pattern.takeLast(2).toSet().size == 2)
            assertTrue(pattern.windowed(3).none { window -> window.distinct().size == 1 })
        }
    }

    private fun uefaCandidates(): List<Team> {
        val countries = listOf(
            "Inglaterra", "Espanha", "Itália", "Alemanha", "França",
            "Portugal", "Países Baixos", "Bélgica", "Turquia", "Escócia",
            "Áustria", "Suíça", "Dinamarca", "Noruega", "Suécia",
            "Polônia", "Tchéquia", "Croácia", "Sérvia", "Grécia"
        )
        return (0 until 120).map { index ->
            val country = countries[index % countries.size]
            Team(
                id = index + 1L,
                name = "$country Audit ${index + 1}",
                city = "Cidade ${index + 1}",
                state = country.take(2),
                country = country,
                division = 1,
                rating = 60 + (index % 35)
            )
        }
    }
}
