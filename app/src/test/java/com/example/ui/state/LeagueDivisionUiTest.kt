package com.example.ui.state

import com.example.data.LeagueHierarchyLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeagueDivisionUiTest {

    @Test
    fun brazilExposesAllFiveConfiguredDivisionTabs() {
        val hierarchy = LeagueHierarchyLoader.getHierarchyForCountry("Brasil")
        val tabs = LeagueDivisionUi.tabsForHierarchy(hierarchy)

        assertEquals(listOf(1, 2, 3, 4, 5), tabs.map { it.division })
        assertEquals(5, tabs.map { it.key }.toSet().size)
        assertEquals("DIVISION_5", tabs.last().key)
        assertEquals(5, LeagueDivisionUi.divisionFromKey(tabs.last().key))
        assertTrue(
            "A quinta divisão precisa ser distinguível visualmente da quarta",
            tabs.last().label != tabs[3].label
        )
    }

    @Test
    fun countriesExposeOnlyTheirRealConfiguredLevels() {
        val franceTabs = LeagueDivisionUi.tabsForHierarchy(
            LeagueHierarchyLoader.getHierarchyForCountry("França")
        )
        val switzerlandTabs = LeagueDivisionUi.tabsForHierarchy(
            LeagueHierarchyLoader.getHierarchyForCountry("Suíça")
        )
        val boliviaTabs = LeagueDivisionUi.tabsForHierarchy(
            LeagueHierarchyLoader.getHierarchyForCountry("Bolívia")
        )

        assertEquals(listOf(1, 2, 3), franceTabs.map { it.division })
        assertEquals(listOf(1, 2), switzerlandTabs.map { it.division })
        assertEquals(listOf(1), boliviaTabs.map { it.division })
    }

    @Test
    fun nonDivisionKeysAreNotMisreadAsLeagueLevels() {
        assertNull(LeagueDivisionUi.divisionFromKey("COPA"))
        assertNull(LeagueDivisionUi.divisionFromKey("CONTINENTAL_T1"))
        assertNull(LeagueDivisionUi.divisionFromKey("DIVISION_0"))
        assertNull(LeagueDivisionUi.divisionFromKey("DIVISION_X"))
    }
}
