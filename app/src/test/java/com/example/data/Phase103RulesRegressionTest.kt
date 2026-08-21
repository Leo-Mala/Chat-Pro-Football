package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase103RulesRegressionTest {

    @Test
    fun `FIFA group ranking uses head to head before overall goal difference`() {
        val fixtures = listOf(
            played(1L, 2L, 0, 1),
            played(1L, 3L, 5, 0),
            played(1L, 4L, 4, 0),
            played(2L, 3L, 1, 0),
            played(2L, 4L, 0, 1),
            played(3L, 4L, 1, 0)
        )

        val ranking = FifaClubWorldCupRules.groupRanking(fixtures)

        assertEquals(listOf(2L, 1L, 3L, 4L), ranking)
    }

    @Test
    fun `FIFA group ranking fails closed while any fixture is undecided`() {
        val fixtures = listOf(
            played(1L, 2L, 1, 0),
            played(3L, 4L, 0, 0).copy(homeScore = null, awayScore = null, isPlayed = false)
        )

        assertTrue(FifaClubWorldCupRules.groupRanking(fixtures).isEmpty())
    }

    @Test
    fun `UEFA qualified slots retain their concrete destination competition`() {
        val fields = UefaQualificationRules.selectLeaguePhaseFields(uefaCandidates(120))

        assertEquals(36, fields.championsLeague.size)
        assertEquals(36, fields.europaLeague.size)
        assertEquals(36, fields.conferenceLeague.size)
        assertTrue(fields.championsLeague.all {
            it.slot.destinationCompetition == CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE
        })
        assertTrue(fields.europaLeague.all {
            it.slot.destinationCompetition == CompetitionIdentity.UEFA_EUROPA_LEAGUE
        })
        assertTrue(fields.conferenceLeague.all {
            it.slot.destinationCompetition == CompetitionIdentity.UEFA_CONFERENCE_LEAGUE
        })
    }

    private fun played(home: Long, away: Long, homeScore: Int, awayScore: Int) =
        Fixture(
            season = 2029,
            week = SuperMundialSystem.GROUP_WEEK_1,
            matchSlot = MatchSlot.MIDWEEK,
            homeTeamId = home,
            awayTeamId = away,
            homeScore = homeScore,
            awayScore = awayScore,
            competitionType = "WORLD_CUP_GP_A",
            isPlayed = true
        )

    private fun uefaCandidates(count: Int): List<Team> {
        val countries = listOf(
            "Inglaterra", "Espanha", "Itália", "Alemanha", "França",
            "Portugal", "Países Baixos", "Bélgica", "Turquia", "Escócia",
            "Áustria", "Suíça", "Dinamarca", "Noruega", "Suécia",
            "Polônia", "Tchéquia", "Croácia", "Sérvia", "Grécia"
        )
        return (0 until count).map { index ->
            val country = countries[index % countries.size]
            Team(
                id = index + 1L,
                name = "$country Regressão ${index + 1}",
                city = "Cidade ${index + 1}",
                state = country.take(2),
                country = country,
                division = 1,
                rating = 70 + (index % 20)
            )
        }
    }
}
