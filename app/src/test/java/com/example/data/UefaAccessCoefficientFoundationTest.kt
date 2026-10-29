package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UefaAccessCoefficientFoundationTest {

    @Test
    fun `qualifying paths expose only official stages represented by each path`() {
        assertEquals(
            listOf(
                UefaQualificationStage.Q1,
                UefaQualificationStage.Q2,
                UefaQualificationStage.Q3,
                UefaQualificationStage.PLAYOFF
            ),
            UefaQualificationStructure.stagesFor(
                UefaCompetitionCode.UCL,
                UefaQualificationPath.CHAMPIONS
            )
        )
        assertEquals(
            listOf(
                UefaQualificationStage.Q2,
                UefaQualificationStage.Q3,
                UefaQualificationStage.PLAYOFF
            ),
            UefaQualificationStructure.stagesFor(
                UefaCompetitionCode.UCL,
                UefaQualificationPath.LEAGUE
            )
        )
        assertEquals(
            listOf(
                UefaQualificationStage.Q1,
                UefaQualificationStage.Q2,
                UefaQualificationStage.Q3,
                UefaQualificationStage.PLAYOFF
            ),
            UefaQualificationStructure.stagesFor(
                UefaCompetitionCode.UEL,
                UefaQualificationPath.MAIN
            )
        )
        assertEquals(
            listOf(UefaQualificationStage.Q3, UefaQualificationStage.PLAYOFF),
            UefaQualificationStructure.stagesFor(
                UefaCompetitionCode.UEL,
                UefaQualificationPath.CHAMPIONS
            )
        )
        assertEquals(
            listOf(
                UefaQualificationStage.Q1,
                UefaQualificationStage.Q2,
                UefaQualificationStage.Q3,
                UefaQualificationStage.PLAYOFF
            ),
            UefaQualificationStructure.stagesFor(
                UefaCompetitionCode.UECL,
                UefaQualificationPath.MAIN
            )
        )
        assertEquals(
            listOf(
                UefaQualificationStage.Q2,
                UefaQualificationStage.Q3,
                UefaQualificationStage.PLAYOFF
            ),
            UefaQualificationStructure.stagesFor(
                UefaCompetitionCode.UECL,
                UefaQualificationPath.CHAMPIONS
            )
        )
    }

    @Test
    fun `elimination transfers preserve competition stage and path without inventing unresolved subpath`() {
        assertEquals(
            UefaEntryPoint(
                UefaCompetitionCode.UECL,
                UefaQualificationStage.Q2,
                UefaQualificationPath.CHAMPIONS
            ),
            UefaQualificationStructure.destinationAfterElimination(
                UefaEntryPoint(
                    UefaCompetitionCode.UCL,
                    UefaQualificationStage.Q1,
                    UefaQualificationPath.CHAMPIONS
                )
            )
        )
        assertEquals(
            UefaEntryPoint(
                UefaCompetitionCode.UEL,
                UefaQualificationStage.Q3,
                UefaQualificationPath.CHAMPIONS
            ),
            UefaQualificationStructure.destinationAfterElimination(
                UefaEntryPoint(
                    UefaCompetitionCode.UCL,
                    UefaQualificationStage.Q2,
                    UefaQualificationPath.CHAMPIONS
                )
            )
        )
        assertEquals(
            UefaEntryPoint(
                UefaCompetitionCode.UEL,
                UefaQualificationStage.PLAYOFF,
                null
            ),
            UefaQualificationStructure.destinationAfterElimination(
                UefaEntryPoint(
                    UefaCompetitionCode.UCL,
                    UefaQualificationStage.Q3,
                    UefaQualificationPath.CHAMPIONS
                )
            )
        )
        assertEquals(
            UefaEntryPoint(
                UefaCompetitionCode.UECL,
                UefaQualificationStage.PLAYOFF,
                UefaQualificationPath.CHAMPIONS
            ),
            UefaQualificationStructure.destinationAfterElimination(
                UefaEntryPoint(
                    UefaCompetitionCode.UEL,
                    UefaQualificationStage.Q3,
                    UefaQualificationPath.CHAMPIONS
                )
            )
        )
        assertEquals(
            UefaEntryPoint(
                UefaCompetitionCode.UECL,
                UefaQualificationStage.LEAGUE_PHASE,
                null
            ),
            UefaQualificationStructure.destinationAfterElimination(
                UefaEntryPoint(
                    UefaCompetitionCode.UEL,
                    UefaQualificationStage.PLAYOFF,
                    UefaQualificationPath.MAIN
                )
            )
        )
        assertNull(
            UefaQualificationStructure.destinationAfterElimination(
                UefaEntryPoint(
                    UefaCompetitionCode.UECL,
                    UefaQualificationStage.PLAYOFF,
                    UefaQualificationPath.MAIN
                )
            )
        )
    }

    @Test
    fun `association coefficient uses official result weights and truncates at the thousandth`() {
        assertEquals(
            1_000L,
            UefaCoefficientRules.associationMatchPoints(
                UefaCoefficientRules.MatchResult.WIN,
                qualifyingOrPlayoff = true
            )
        )
        assertEquals(
            500L,
            UefaCoefficientRules.associationMatchPoints(
                UefaCoefficientRules.MatchResult.DRAW,
                qualifyingOrPlayoff = true
            )
        )
        assertEquals(
            2_000L,
            UefaCoefficientRules.associationMatchPoints(
                UefaCoefficientRules.MatchResult.WIN,
                qualifyingOrPlayoff = false
            )
        )
        assertEquals(
            1_000L,
            UefaCoefficientRules.associationMatchPoints(
                UefaCoefficientRules.MatchResult.DRAW,
                qualifyingOrPlayoff = false
            )
        )
        assertEquals(3_333L, UefaCoefficientRules.associationSeasonCoefficient(10_001L, 3))
        assertEquals("3.333", UefaCoefficientRules.formatMilli(3_333L))
    }

    @Test
    fun `club coefficient uses five seasons and twenty percent association floor`() {
        val ownBelowFloor = listOf(1_000L, 2_000L, 1_000L, 2_000L, 3_000L)
        val ownAboveFloor = listOf(3_000L, 3_000L, 3_000L, 3_000L, 3_000L)

        assertEquals(
            10_000L,
            UefaCoefficientRules.clubFiveSeasonCoefficient(
                ownBelowFloor,
                associationFiveSeasonCoefficientMilli = 50_000L
            )
        )
        assertEquals(
            15_000L,
            UefaCoefficientRules.clubFiveSeasonCoefficient(
                ownAboveFloor,
                associationFiveSeasonCoefficientMilli = 50_000L
            )
        )
        assertEquals(
            15_000L,
            UefaCoefficientRules.associationFiveSeasonCoefficient(
                listOf(1_000L, 2_000L, 3_000L, 4_000L, 5_000L)
            )
        )
    }

    @Test
    fun `club coefficient encodes conference qualifying points league minimums and position bonuses`() {
        assertEquals(1_000L, UefaCoefficientRules.conferenceQualifyingEliminationPoints(UefaQualificationStage.Q1))
        assertEquals(1_500L, UefaCoefficientRules.conferenceQualifyingEliminationPoints(UefaQualificationStage.Q2))
        assertEquals(2_000L, UefaCoefficientRules.conferenceQualifyingEliminationPoints(UefaQualificationStage.Q3))
        assertEquals(2_500L, UefaCoefficientRules.conferenceQualifyingEliminationPoints(UefaQualificationStage.PLAYOFF))

        assertEquals(0L, UefaCoefficientRules.leaguePhaseGuaranteedMinimum(UefaCompetitionCode.UCL))
        assertEquals(3_000L, UefaCoefficientRules.leaguePhaseGuaranteedMinimum(UefaCompetitionCode.UEL))
        assertEquals(2_500L, UefaCoefficientRules.leaguePhaseGuaranteedMinimum(UefaCompetitionCode.UECL))

        assertEquals(12_000L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UCL, 1))
        assertEquals(6_250L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UCL, 24))
        assertEquals(6_000L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UCL, 25))
        assertEquals(6_000L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UCL, 36))

        assertEquals(6_000L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UEL, 1))
        assertEquals(250L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UEL, 24))
        assertEquals(0L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UEL, 25))

        assertEquals(4_000L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UECL, 1))
        assertEquals(2_000L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UECL, 9))
        assertEquals(1_875L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UECL, 10))
        assertEquals(125L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UECL, 24))
        assertEquals(0L, UefaCoefficientRules.leaguePositionBonus(UefaCompetitionCode.UECL, 25))

        UefaCoefficientRules.KnockoutMilestone.entries.forEach { milestone ->
            assertEquals(1_500L, UefaCoefficientRules.knockoutMilestoneBonus(UefaCompetitionCode.UCL, milestone))
            assertEquals(1_000L, UefaCoefficientRules.knockoutMilestoneBonus(UefaCompetitionCode.UEL, milestone))
            assertEquals(500L, UefaCoefficientRules.knockoutMilestoneBonus(UefaCompetitionCode.UECL, milestone))
        }
    }

    @Test
    fun `2026 27 Champions league phase allocation totals thirty six and remains fail safe`() {
        assertEquals(36, UefaAccessList2026_27.championsLeaguePhaseAllocationTotal())
        assertEquals(4, UefaAccessList2026_27.championsDirectLeagueSlots(" Inglaterra "))
        assertEquals(3, UefaAccessList2026_27.championsDirectLeagueSlots("França"))
        assertEquals(1, UefaAccessList2026_27.championsDirectLeagueSlots("Turquia"))
        assertNull(UefaAccessList2026_27.championsDirectLeagueSlots("Grécia"))
        assertNull(UefaAccessList2026_27.championsDirectLeagueSlots("País inventado"))
        assertEquals(1, UefaAccessList2026_27.europeanPerformanceSpots("Inglaterra"))
        assertEquals(1, UefaAccessList2026_27.europeanPerformanceSpots("Espanha"))
        assertEquals(0, UefaAccessList2026_27.europeanPerformanceSpots("Itália"))
    }

    @Test
    fun `domestic planner consumes stored standings and preserves typed source`() {
        val standings = listOf(
            standing(teamId = 101L, country = "Inglaterra", position = 1),
            standing(teamId = 102L, country = "Inglaterra", position = 2),
            standing(teamId = 201L, country = "Espanha", position = 1),
            standing(teamId = 301L, country = "Brasil", position = 1)
        )

        val englishRunnerUp = UefaDomesticAccessPlanner.fromLeaguePosition(
            standings = standings,
            country = "Inglaterra",
            position = 2
        )
        assertEquals(102L, englishRunnerUp?.teamId)
        assertEquals("Inglaterra", englishRunnerUp?.country)
        assertEquals(QualificationSource.LeaguePosition(2), englishRunnerUp?.source)

        assertNull(UefaDomesticAccessPlanner.fromLeaguePosition(standings, "Brasil", 1))
        assertNull(UefaDomesticAccessPlanner.fromLeaguePosition(standings, "País inventado", 1))
        assertNull(UefaDomesticAccessPlanner.fromLeaguePosition(standings, "Inglaterra", 9))
    }

    @Test
    fun `cup winner and titleholder retain real UEFA association`() {
        val cup = UefaDomesticAccessPlanner.fromNationalCupWinner(501L, "Espanha")
        assertEquals(501L, cup?.teamId)
        assertEquals("Espanha", cup?.country)
        assertEquals(QualificationSource.NationalCupWinner("Espanha"), cup?.source)

        val titleholder = UefaDomesticAccessPlanner.fromTitleholder(
            teamId = 601L,
            country = "Alemanha",
            competition = CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE
        )
        assertEquals(601L, titleholder?.teamId)
        assertEquals("Alemanha", titleholder?.country)
        assertEquals(
            QualificationSource.ContinentalChampion(CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE),
            titleholder?.source
        )

        assertNull(UefaDomesticAccessPlanner.fromNationalCupWinner(701L, "Brasil"))
        assertNull(
            UefaDomesticAccessPlanner.fromTitleholder(
                teamId = 702L,
                country = "Brasil",
                competition = CompetitionIdentity.UEFA_EUROPA_LEAGUE
            )
        )
        assertTrue(UefaAccessList2026_27.europeanPerformanceSpotCountries.size == 2)
    }

    private fun standing(teamId: Long, country: String, position: Int): GlobalLeagueStanding =
        GlobalLeagueStanding(
            season = 2026,
            country = country,
            division = 1,
            teamId = teamId,
            position = position,
            points = 80 - position,
            played = 38,
            wins = 20,
            draws = 10,
            losses = 8,
            goalsFor = 65,
            goalsAgainst = 35,
            goalDifference = 30
        )
}
