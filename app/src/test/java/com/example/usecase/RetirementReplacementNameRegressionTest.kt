package com.example.usecase

import com.example.data.DefaultData
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetirementReplacementNameRegressionTest {
    @Test
    fun syntheticNameIsStableAndComesFromExistingCountryPools() {
        val country = "Brasil"
        val countryInfo = DefaultData.getCountryInfo(country)

        val first = SyntheticPlayerNameGenerator.forStableIdentity(country, 123_456L)
        val second = SyntheticPlayerNameGenerator.forStableIdentity(country, 123_456L)
        val allowedNames = countryInfo.firstNames.flatMap { firstName ->
            countryInfo.lastNames.map { lastName -> "$firstName $lastName" }
        }.toSet()

        assertEquals(first, second)
        assertTrue(first in allowedNames)
        assertFalse(first.startsWith("Novo Prospecto"))
    }

    @Test
    fun retirementReplacementNoLongerPersistsGenericProspectPrefix() {
        val source = File("src/main/java/com/example/usecase/SeasonTransitionUseCase.kt").readText()

        assertFalse(source.contains("name = \"Novo Prospecto"))
        assertTrue(source.contains("name = SyntheticPlayerNameGenerator.forStableIdentity("))
        assertTrue(source.contains("stableKey = player.id * 31L + currentSeason.toLong()"))
        assertTrue(source.contains("val rand = Random(currentSeason * 31L + sourceSave.playerTeamId)"))
    }

    @Test
    fun startupRepairTargetsOnlyPersistedGenericProspectNames() {
        val source = File("src/main/java/com/example/usecase/DatabaseIntegrityUseCase.kt").readText()

        assertTrue(source.contains("repairLegacySyntheticProspectNames()"))
        assertTrue(source.contains("SELECT id FROM players WHERE name = ? OR name LIKE ? ORDER BY id"))
        assertTrue(source.contains("LEGACY_SYNTHETIC_PROSPECT_NAME"))
        assertTrue(source.contains("player.copy("))
        assertTrue(source.contains("stableKey = player.id"))
        assertTrue(source.contains("repository.updatePlayers(replacements)"))
        assertTrue(source.contains("repository.db.playerBatchDao().getPlayersByIds(chunk)"))
    }
}
