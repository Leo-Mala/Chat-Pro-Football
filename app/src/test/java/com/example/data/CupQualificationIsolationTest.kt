package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CupQualificationIsolationTest {

    @Test
    fun `continental priority does not change national cup participants`() {
        val canonicalTeams = (1L..40L).map { id ->
            Team(
                id = id,
                name = "Brasil Clube $id",
                city = "Cidade $id",
                state = "BR",
                country = "Brasil",
                division = 1,
                rating = 101 - id.toInt()
            )
        }

        // Mesmos clubes, mas com prioridade continental deliberadamente invertida.
        // Se essa visão vazasse para a Copa, o conjunto de 32 participantes mudaria.
        val continentalPriorityView = canonicalTeams.map { team ->
            team.copy(rating = team.id.toInt())
        }

        val baseline = CupCompetitionSystem.generateSeasonOpeningFixtures(
            season = 2027,
            teams = canonicalTeams,
            userTeamId = 1L,
            userCountry = "Brasil"
        )
        val withContinentalPriority = CupCompetitionSystem.generateSeasonOpeningFixtures(
            season = 2027,
            teams = canonicalTeams,
            userTeamId = 1L,
            userCountry = "Brasil",
            continentalTeams = continentalPriorityView
        )

        val baselineCup = participants(baseline.filter { it.competitionType == "COPA" })
        val priorityCup = participants(withContinentalPriority.filter { it.competitionType == "COPA" })
        assertEquals(baselineCup, priorityCup)
        assertEquals(32, priorityCup.size)

        val baselineTier1 = participants(
            baseline.filter { it.competitionType.startsWith("CONTINENTAL_T1_GP_") }
        )
        val priorityTier1 = participants(
            withContinentalPriority.filter { it.competitionType.startsWith("CONTINENTAL_T1_GP_") }
        )

        // Confirma que a visão alternativa realmente afeta apenas o corte continental.
        assertEquals(32, baselineTier1.size)
        assertEquals(32, priorityTier1.size)
        assertNotEquals(baselineTier1, priorityTier1)
    }

    private fun participants(fixtures: List<Fixture>): Set<Long> =
        fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
}
