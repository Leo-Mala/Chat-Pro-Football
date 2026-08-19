package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Fc26LegacyTeamIdPreservationTest {

    @Test
    fun `non materialized clubs retain their pre 9_11A1 ids`() {
        ApplicationProvider.getApplicationContext<Context>()

        val expected = listOf(
            Triple("Itália", "Empoli", 491L),
            Triple("Itália", "Palermo FC", 445L),
            Triple("Itália", "Spezia", 464L),
            Triple("Itália", "Padova", 469L),
            Triple("Portugal", "Famalicao", 1052L)
        )

        expected.forEach { (country, teamName, legacyId) ->
            val exists = DefaultData.countriesMap.getValue(country).teams.any {
                it.name.equals(teamName, ignoreCase = true)
            }
            require(exists) { "Legacy regression fixture no longer exists: $country / $teamName" }
            assertEquals(legacyId, GlobalFootballSystem.getGlobalId(country, teamName))
        }
    }
}
