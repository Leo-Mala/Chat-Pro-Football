package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UefaCompetitionSystemTest {

    @Test
    fun `typed UEFA qualification fills three 36 club fields without using rating`() {
        val candidates = uefaCandidates(120)
        val first = UefaQualificationRules.selectLeaguePhaseFields(candidates)
        val reratedAndReversed = candidates
            .reversed()
            .mapIndexed { index, team -> team.copy(rating = 1 + (index % 99)) }
        val second = UefaQualificationRules.selectLeaguePhaseFields(reratedAndReversed)

        assertEquals(36, first.championsLeague.size)
        assertEquals(36, first.europaLeague.size)
        assertEquals(36, first.conferenceLeague.size)
        assertEquals(108, first.all.map { it.team.id }.toSet().size)

        assertEquals(
            first.championsLeague.map { it.team.id },
            second.championsLeague.map { it.team.id }
        )
        assertEquals(
            first.europaLeague.map { it.team.id },
            second.europaLeague.map { it.team.id }
        )
        assertEquals(
            first.conferenceLeague.map { it.team.id },
            second.conferenceLeague.map { it.team.id }
        )

        first.all.forEach { qualified ->
            assertTrue(qualified.slot.source is QualificationSource.AssociationSlot)
            assertTrue(qualified.slot.ordinal > 0)
        }
    }

    @Test
    fun `Champions League and Europa League generate official 36 by 8 league phase shape`() {
        val fields = UefaQualificationRules.selectLeaguePhaseFields(uefaCandidates(120))

        assertLeaguePhaseShape(
            teams = fields.championsLeague.map { it.team },
            competitionType = UefaCompetitionSystem.CHAMPIONS_LEAGUE,
            expectedMatchesPerClub = 8,
            expectedPotCount = 4,
            expectedOpponentsPerPot = 2
        )
        assertLeaguePhaseShape(
            teams = fields.europaLeague.map { it.team },
            competitionType = UefaCompetitionSystem.EUROPA_LEAGUE,
            expectedMatchesPerClub = 8,
            expectedPotCount = 4,
            expectedOpponentsPerPot = 2
        )
    }

    @Test
    fun `Conference League generates official 36 by 6 league phase shape`() {
        val fields = UefaQualificationRules.selectLeaguePhaseFields(uefaCandidates(120))

        assertLeaguePhaseShape(
            teams = fields.conferenceLeague.map { it.team },
            competitionType = UefaCompetitionSystem.CONFERENCE_LEAGUE,
            expectedMatchesPerClub = 6,
            expectedPotCount = 6,
            expectedOpponentsPerPot = 1
        )
    }

    @Test
    fun `CupCompetitionSystem routes new UEFA seasons to concrete codes only`() {
        val teams = uefaCandidates(120)
        val fixtures = CupCompetitionSystem.generateSeasonOpeningFixtures(
            season = 2027,
            teams = teams,
            userTeamId = teams.first().id,
            userCountry = teams.first().country,
            continentalTeams = teams
        )

        val uefaFixtures = fixtures.filter {
            it.competitionType in setOf(
                UefaCompetitionSystem.CHAMPIONS_LEAGUE,
                UefaCompetitionSystem.EUROPA_LEAGUE,
                UefaCompetitionSystem.CONFERENCE_LEAGUE
            )
        }
        assertEquals(396, uefaFixtures.size)
        assertEquals(144, uefaFixtures.count { it.competitionType == UefaCompetitionSystem.CHAMPIONS_LEAGUE })
        assertEquals(144, uefaFixtures.count { it.competitionType == UefaCompetitionSystem.EUROPA_LEAGUE })
        assertEquals(108, uefaFixtures.count { it.competitionType == UefaCompetitionSystem.CONFERENCE_LEAGUE })
        assertTrue(fixtures.none { it.competitionType.startsWith("CONTINENTAL_T") })
        FixtureScheduleValidator.requireValid(fixtures)
    }

    @Test
    fun `UEFA phase metadata distinguishes league phase from knockout`() {
        assertEquals(
            UefaCompetitionSystem.Phase.LEAGUE_PHASE,
            UefaCompetitionSystem.phaseFor(UefaCompetitionSystem.CHAMPIONS_LEAGUE, 2)
        )
        assertEquals(
            UefaCompetitionSystem.Phase.KNOCKOUT_PLAYOFF,
            UefaCompetitionSystem.phaseFor(UefaCompetitionSystem.CHAMPIONS_LEAGUE, 28)
        )
        assertEquals(
            UefaCompetitionSystem.Phase.ROUND_OF_16,
            UefaCompetitionSystem.phaseFor(UefaCompetitionSystem.EUROPA_LEAGUE, 31)
        )
        assertEquals(
            UefaCompetitionSystem.Phase.QUARTERFINAL,
            UefaCompetitionSystem.phaseFor(UefaCompetitionSystem.CONFERENCE_LEAGUE, 34)
        )
        assertEquals(
            UefaCompetitionSystem.Phase.SEMIFINAL,
            UefaCompetitionSystem.phaseFor(UefaCompetitionSystem.CONFERENCE_LEAGUE, 38)
        )
        assertEquals(
            UefaCompetitionSystem.Phase.FINAL,
            UefaCompetitionSystem.phaseFor(UefaCompetitionSystem.CHAMPIONS_LEAGUE, 40)
        )
        assertEquals(null, UefaCompetitionSystem.phaseFor(UefaCompetitionSystem.CHAMPIONS_LEAGUE, 23))
        assertFalse(CompetitionRules.isKnockoutCompetition(UefaCompetitionSystem.CHAMPIONS_LEAGUE))
    }

    @Test
    fun `league ranking applies points then goal difference before deterministic fallback`() {
        val fixtures = listOf(
            Fixture(
                season = 2027,
                week = 2,
                matchSlot = MatchSlot.MIDWEEK,
                homeTeamId = 1L,
                awayTeamId = 3L,
                competitionType = UefaCompetitionSystem.CHAMPIONS_LEAGUE,
                homeScore = 2,
                awayScore = 0,
                isPlayed = true
            ),
            Fixture(
                season = 2027,
                week = 2,
                matchSlot = MatchSlot.MIDWEEK,
                homeTeamId = 2L,
                awayTeamId = 4L,
                competitionType = UefaCompetitionSystem.CHAMPIONS_LEAGUE,
                homeScore = 1,
                awayScore = 0,
                isPlayed = true
            )
        )

        val ranking = UefaCompetitionSystem.leagueRanking(
            fixtures,
            UefaCompetitionSystem.CHAMPIONS_LEAGUE
        )
        assertEquals(listOf(1L, 2L), ranking.take(2).map { it.teamId })
        assertEquals(3, ranking[0].points)
        assertEquals(2, ranking[0].goalDifference)
        assertEquals(3, ranking[1].points)
        assertEquals(1, ranking[1].goalDifference)
    }

    private fun assertLeaguePhaseShape(
        teams: List<Team>,
        competitionType: String,
        expectedMatchesPerClub: Int,
        expectedPotCount: Int,
        expectedOpponentsPerPot: Int
    ) {
        val fixtures = UefaCompetitionSystem.generateLeaguePhase(
            season = 2027,
            teams = teams,
            competitionType = competitionType
        )
        val weeks = if (competitionType == UefaCompetitionSystem.CONFERENCE_LEAGUE) {
            UefaCompetitionSystem.CONFERENCE_LEAGUE_WEEKS
        } else {
            UefaCompetitionSystem.CHAMPIONS_EUROPA_LEAGUE_WEEKS
        }
        assertEquals(36 * expectedMatchesPerClub / 2, fixtures.size)
        weeks.forEach { week ->
            assertEquals(18, fixtures.count { it.week == week })
        }

        val pots = UefaCompetitionSystem.drawPots(teams, competitionType)
        assertEquals(expectedPotCount, pots.size)
        val potByTeamId = buildMap<Long, Int> {
            pots.forEachIndexed { potIndex, pot -> pot.forEach { put(it.id, potIndex) } }
        }
        val teamById = teams.associateBy { it.id }

        teams.forEach { team ->
            val appearances = fixtures.filter { it.homeTeamId == team.id || it.awayTeamId == team.id }
            assertEquals(expectedMatchesPerClub, appearances.size)
            assertEquals(expectedMatchesPerClub / 2, appearances.count { it.homeTeamId == team.id })
            assertEquals(expectedMatchesPerClub / 2, appearances.count { it.awayTeamId == team.id })

            val opponents = appearances.map { fixture ->
                if (fixture.homeTeamId == team.id) fixture.awayTeamId else fixture.homeTeamId
            }
            assertEquals(expectedMatchesPerClub, opponents.toSet().size)
            assertTrue(opponents.none { opponentId -> teamById.getValue(opponentId).country == team.country })

            val byPot = opponents.groupingBy { potByTeamId.getValue(it) }.eachCount()
            assertEquals(expectedPotCount, byPot.size)
            assertTrue(byPot.values.all { it == expectedOpponentsPerPot })

            val byAssociation = opponents.groupingBy { teamById.getValue(it).country }.eachCount()
            assertTrue(byAssociation.values.all { it <= 2 })

            val firstTwo = appearances.filter { it.week in weeks.take(2) }
            val lastTwo = appearances.filter { it.week in weeks.takeLast(2) }
            assertEquals(1, firstTwo.count { it.homeTeamId == team.id })
            assertEquals(1, firstTwo.count { it.awayTeamId == team.id })
            assertEquals(1, lastTwo.count { it.homeTeamId == team.id })
            assertEquals(1, lastTwo.count { it.awayTeamId == team.id })
        }
        FixtureScheduleValidator.requireValid(fixtures)
    }

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
                name = "$country Clube ${index + 1}",
                city = "Cidade ${index + 1}",
                state = country.take(2),
                country = country,
                division = 1,
                rating = 100 - (index % 60)
            )
        }
    }
}
