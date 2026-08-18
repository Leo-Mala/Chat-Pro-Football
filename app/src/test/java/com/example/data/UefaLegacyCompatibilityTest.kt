package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UefaLegacyCompatibilityTest {

    @Test
    fun `legacy persisted codes remain distinct from new UEFA identities`() {
        assertEquals(
            CompetitionIdentity.LEGACY_CONTINENTAL_T1,
            CompetitionRulesRegistry.resolve("CONTINENTAL_T1")?.identity
        )
        assertEquals(
            CompetitionIdentity.LEGACY_CONTINENTAL_T2,
            CompetitionRulesRegistry.resolve("CONTINENTAL_T2")?.identity
        )
        assertEquals(
            CompetitionIdentity.LEGACY_CONTINENTAL_T3,
            CompetitionRulesRegistry.resolve("CONTINENTAL_T3")?.identity
        )

        assertEquals(
            CompetitionIdentity.UEFA_CHAMPIONS_LEAGUE,
            CompetitionRulesRegistry.resolve(UefaCompetitionSystem.CHAMPIONS_LEAGUE)?.identity
        )
        assertEquals(
            CompetitionIdentity.UEFA_EUROPA_LEAGUE,
            CompetitionRulesRegistry.resolve(UefaCompetitionSystem.EUROPA_LEAGUE)?.identity
        )
        assertEquals(
            CompetitionIdentity.UEFA_CONFERENCE_LEAGUE,
            CompetitionRulesRegistry.resolve(UefaCompetitionSystem.CONFERENCE_LEAGUE)?.identity
        )

        assertNotEquals(
            CompetitionRulesRegistry.resolve("CONTINENTAL_T1")?.identity,
            CompetitionRulesRegistry.resolve(UefaCompetitionSystem.CHAMPIONS_LEAGUE)?.identity
        )
    }
}
