package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldFootballRulesCatalogProjectionTest {

    @Test
    fun `GlobalFootballSystem projects dedicated CONMEBOL calendar from the canonical registry`() {
        val libertadores = requireNotNull(GlobalFootballSystem.getCompetitionByCode("CONMEBOL_CL"))
        val sudamericana = requireNotNull(GlobalFootballSystem.getCompetitionByCode("CONMEBOL_CS"))

        listOf(libertadores, sudamericana).forEach { competition ->
            assertEquals(ConmebolCompetitionSystem.GROUP_WEEKS.first(), competition.startWeek)
            assertEquals(ConmebolCompetitionSystem.FINAL_WEEK, competition.endWeek)
            assertEquals("CONMEBOL", competition.confederation)
        }

        assertEquals(
            CompetitionRulesRegistry.catalogDefinitions.map { it.code }.toSet(),
            GlobalFootballSystem.competitions.map { it.code }.toSet()
        )
    }
}
