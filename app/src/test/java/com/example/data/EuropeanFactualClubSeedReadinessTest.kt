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
        assertEquals(41, counts.getOrDefault(EuropeanFactualClubSeedStatus.READY, 0))
        assertEquals(279, counts.getOrDefault(EuropeanFactualClubSeedStatus.MISSING_EXPLICIT_TEMPLATE, 0))
        assertEquals(0, counts.getOrDefault(EuropeanFactualClubSeedStatus.NON_TOP_FLIGHT_TEMPLATE, 0))
        assertEquals(0, counts.getOrDefault(EuropeanFactualClubSeedStatus.INVALID_TEMPLATE_METADATA, 0))
        assertEquals(0, counts.getOrDefault(EuropeanFactualClubSeedStatus.GLOBAL_ID_MISMATCH, 0))
    }

    @Test
    fun `ready set is England Spain plus explicitly materialized Trabzonspor`() {
        val ready = EuropeanFactualClubSeedReadiness.readyAssessments()
        val expected = EuropeanDomesticBaseline2026_27.associations
            .filter { it.country == "Inglaterra" || it.country == "Espanha" }
            .flatMap { baseline -> baseline.verifiedTopFlightClubs.map { baseline.country to it } }
            .toMutableSet()
            .apply { add("Turquia" to "Trabzonspor") }
        val actual = ready.map { it.country to it.clubName }.toSet()
        val readyByCountry = ready.groupingBy { it.country }.eachCount()

        assertEquals(expected, actual)
        assertEquals(setOf("Inglaterra", "Espanha", "Turquia"), readyByCountry.keys)
        assertEquals(20, readyByCountry.getOrDefault("Inglaterra", 0))
        assertEquals(20, readyByCountry.getOrDefault("Espanha", 0))
        assertEquals(1, readyByCountry.getOrDefault("Turquia", 0))
        assertEquals(279, EuropeanFactualClubSeedReadiness.notReadyAssessments().size)
    }

    @Test
    fun `Manchester United and Trabzonspor both preserve canonical stable ids`() {
        val united = EuropeanFactualClubSeedReadiness.assess("Inglaterra", "Manchester United")
        val trabzonspor = EuropeanFactualClubSeedReadiness.assess("Turquia", "Trabzonspor")
        val record = requireNotNull(
            EuropeanAdditionalClubTemplates2026_27.find("Turquia", "Trabzonspor")
        )

        assertEquals(EuropeanFactualClubSeedStatus.READY, united.status)
        assertEquals(5L, united.stableTeamId)
        assertEquals(5L, united.resolvedGlobalId)
        assertNotNull(united.template)

        assertEquals(EuropeanFactualClubSeedStatus.READY, trabzonspor.status)
        assertEquals(130_395L, trabzonspor.stableTeamId)
        assertEquals(130_395L, trabzonspor.resolvedGlobalId)
        assertEquals(130_395L, record.stableTeamId)
        assertEquals("2026/27", record.domesticSeasonLabel)
        assertEquals("2026-08-18", record.verifiedAsOfIso)
        assertTrue(record.sourceRefs.isNotEmpty())
        assertTrue(record.sourceRefs.all { it.contains("trabzonspor.org.tr") })
        assertNotNull(trabzonspor.template)
        assertEquals("Trabzon", trabzonspor.template?.city)
        assertEquals("TRA", trabzonspor.template?.state)
        assertEquals("Papara Park", trabzonspor.template?.stadium)
        assertEquals(78, trabzonspor.template?.rating)

        val reverse = requireNotNull(GlobalFootballSystem.getTeamByGlobalId(130_395L))
        assertEquals("Trabzonspor", reverse.name)
        assertEquals("Turquia", reverse.country)
        assertEquals("Papara Park", reverse.stadiumName)
    }

    @Test
    fun `every ready persisted team keeps canonical id and complete minimum metadata`() {
        val assessments = EuropeanFactualClubSeedReadiness.readyAssessments().associateBy { it.stableTeamId }
        val teams = EuropeanFactualClubSeedReadiness.readyTopFlightTeams()

        assertEquals(41, teams.size)
        assertEquals(41, teams.map { it.id }.distinct().size)
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
