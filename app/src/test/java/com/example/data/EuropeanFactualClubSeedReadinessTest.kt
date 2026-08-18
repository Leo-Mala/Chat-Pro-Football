package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropeanFactualClubSeedReadinessTest {

    @Test
    fun `readiness classifies all 320 factual top flight clubs without silent gaps`() {
        val assessments = EuropeanFactualClubSeedReadiness.assessAll()

        assertEquals(320, assessments.size)
        assertEquals(320, assessments.map { it.country to it.clubName }.distinct().size)
        assertEquals(320, assessments.map { it.stableTeamId }.distinct().size)

        val counts = assessments.groupingBy { it.status }.eachCount()
        assertEquals(40, counts.getOrDefault(EuropeanFactualClubSeedStatus.READY, 0))
        assertEquals(280, counts.getOrDefault(EuropeanFactualClubSeedStatus.MISSING_EXPLICIT_TEMPLATE, 0))
        assertEquals(0, counts.getOrDefault(EuropeanFactualClubSeedStatus.NON_TOP_FLIGHT_TEMPLATE, 0))
        assertEquals(0, counts.getOrDefault(EuropeanFactualClubSeedStatus.INVALID_TEMPLATE_METADATA, 0))
        assertEquals(0, counts.getOrDefault(EuropeanFactualClubSeedStatus.GLOBAL_ID_MISMATCH, 0))
    }

    @Test
    fun `only England and Spain are currently fully materialized top flight templates`() {
        val ready = EuropeanFactualClubSeedReadiness.readyAssessments()
        val readyByCountry = ready.groupingBy { it.country }.eachCount()

        assertEquals(setOf("Inglaterra", "Espanha"), readyByCountry.keys)
        assertEquals(20, readyByCountry.getOrDefault("Inglaterra", 0))
        assertEquals(20, readyByCountry.getOrDefault("Espanha", 0))
        assertEquals(280, EuropeanFactualClubSeedReadiness.notReadyAssessments().size)
    }

    @Test
    fun `Manchester United is ready but Trabzonspor is still missing explicit template`() {
        val united = EuropeanFactualClubSeedReadiness.assess("Inglaterra", "Manchester United")
        val trabzonspor = EuropeanFactualClubSeedReadiness.assess("Turquia", "Trabzonspor")

        assertEquals(EuropeanFactualClubSeedStatus.READY, united.status)
        assertEquals(5L, united.stableTeamId)
        assertEquals(5L, united.resolvedGlobalId)
        assertNotNull(united.template)

        assertEquals(EuropeanFactualClubSeedStatus.MISSING_EXPLICIT_TEMPLATE, trabzonspor.status)
        assertNotNull(StableTeamIdentityRegistry.idFor("Turquia", "Trabzonspor"))
        assertEquals(null, trabzonspor.template)
        assertEquals(null, trabzonspor.resolvedGlobalId)
    }

    @Test
    fun `every ready persisted team keeps canonical id and complete minimum metadata`() {
        val assessments = EuropeanFactualClubSeedReadiness.readyAssessments().associateBy { it.stableTeamId }
        val teams = EuropeanFactualClubSeedReadiness.readyTopFlightTeams()

        assertEquals(40, teams.size)
        assertEquals(40, teams.map { it.id }.distinct().size)
        teams.forEach { team ->
            val assessment = requireNotNull(assessments[team.id])
            assertEquals(assessment.country, team.country)
            assertEquals(1, team.division)
            assertTrue(team.name.isNotBlank())
            assertTrue(team.city.isNotBlank())
            assertTrue(team.state.isNotBlank())
            assertTrue(team.stadiumName.isNotBlank())
            assertTrue(team.rating in 1..100)
            assertEquals(team.id, GlobalFootballSystem.getGlobalId(team.country, team.name))
        }
    }
}
