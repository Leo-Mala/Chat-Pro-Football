package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
        assertEquals(279, counts.getOrDefault(EuropeanFactualClubSeedStatus.MISSING_EXPLICIT_TEMPLATE, 0))
        assertEquals(0, counts.getOrDefault(EuropeanFactualClubSeedStatus.NON_TOP_FLIGHT_TEMPLATE, 0))
        assertEquals(0, counts.getOrDefault(EuropeanFactualClubSeedStatus.INVALID_TEMPLATE_METADATA, 0))
        assertEquals(1, counts.getOrDefault(EuropeanFactualClubSeedStatus.GLOBAL_ID_MISMATCH, 0))
    }

    @Test
    fun `ready set remains England and Spain until additional template resolver is integrated`() {
        val ready = EuropeanFactualClubSeedReadiness.readyAssessments()
        val expected = EuropeanDomesticBaseline2026_27.associations
            .filter { it.country == "Inglaterra" || it.country == "Espanha" }
            .flatMap { baseline -> baseline.verifiedTopFlightClubs.map { baseline.country to it } }
            .toSet()
        val actual = ready.map { it.country to it.clubName }.toSet()
        val readyByCountry = ready.groupingBy { it.country }.eachCount()

        assertEquals(expected, actual)
        assertEquals(setOf("Inglaterra", "Espanha"), readyByCountry.keys)
        assertEquals(20, readyByCountry.getOrDefault("Inglaterra", 0))
        assertEquals(20, readyByCountry.getOrDefault("Espanha", 0))
        assertEquals(280, EuropeanFactualClubSeedReadiness.notReadyAssessments().size)
    }

    @Test
    fun `Manchester United is ready while Trabzonspor template waits for global id resolver`() {
        val united = EuropeanFactualClubSeedReadiness.assess("Inglaterra", "Manchester United")
        val trabzonspor = EuropeanFactualClubSeedReadiness.assess("Turquia", "Trabzonspor")

        assertEquals(EuropeanFactualClubSeedStatus.READY, united.status)
        assertEquals(5L, united.stableTeamId)
        assertEquals(5L, united.resolvedGlobalId)
        assertNotNull(united.template)

        assertEquals(EuropeanFactualClubSeedStatus.GLOBAL_ID_MISMATCH, trabzonspor.status)
        assertEquals(130_395L, trabzonspor.stableTeamId)
        assertNotNull(trabzonspor.template)
        assertEquals("Trabzon", trabzonspor.template?.city)
        assertEquals("Papara Park", trabzonspor.template?.stadium)
        assertEquals(78, trabzonspor.template?.rating)
        assertNotNull(trabzonspor.resolvedGlobalId)
        assertNotEquals(trabzonspor.stableTeamId, trabzonspor.resolvedGlobalId)
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
