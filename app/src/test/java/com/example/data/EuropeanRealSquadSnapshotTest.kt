package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropeanRealSquadSnapshotTest {

    @Test
    fun `snapshot binds to factual club season source and stable team id`() {
        val snapshot = snapshot("Manchester United", readyPlayers("United"))

        assertEquals(5L, snapshot.teamId)
        assertEquals("2026/27", snapshot.domesticSeasonLabel)
        assertEquals("2026-08-18", snapshot.verifiedAsOfIso)
        assertEquals(EuropeanSquadCoverage.GAMEPLAY_READY_FACTUAL_SNAPSHOT, snapshot.coverage())

        val gameplay = snapshot.toGameplayPlayers(teamRating = 84)
        assertEquals(snapshot.players.map { it.stableId }, gameplay.map { it.id })
        assertTrue(gameplay.all { it.teamId == 5L })
    }

    @Test
    fun `partial snapshot cannot be mistaken for gameplay ready coverage`() {
        val partial = snapshot(
            clubName = "Manchester United",
            players = listOf(player("Only Keeper", "2000-01-01", "GOL"))
        )

        assertEquals(EuropeanSquadCoverage.PARTIAL_FACTUAL_SNAPSHOT, partial.coverage())
    }

    @Test
    fun `catalog rejects same factual player in two clubs`() {
        val shared = player("Same Person", "2000-01-01", "MEI")
        val united = snapshot("Manchester United", listOf(shared))
        val arsenal = snapshot("Arsenal FC", listOf(shared))

        var failed = false
        try {
            EuropeanRealSquadCatalog(listOf(united, arsenal))
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `empty catalog reports every factual top flight club as missing`() {
        val catalog = EuropeanRealSquadCatalog(emptyList())
        val expected = EuropeanDomesticBaseline2026_27.associations
            .sumOf { it.verifiedTopFlightClubs.size }

        assertEquals(expected, catalog.missingTopFlightClubs().size)
        assertTrue(catalog.gameplayReadyClubs().isEmpty())
        assertTrue(catalog.all().isEmpty())
    }

    private fun snapshot(
        clubName: String,
        players: List<EuropeanRealPlayerTemplate>
    ) = EuropeanRealSquadSnapshot(
        country = "Inglaterra",
        clubName = clubName,
        domesticSeasonLabel = "2026/27",
        verifiedAsOfIso = "2026-08-18",
        sourceRefs = listOf("official:test-fixture"),
        players = players
    )

    private fun readyPlayers(prefix: String): List<EuropeanRealPlayerTemplate> {
        val positions = listOf(
            "GOL", "GOL",
            "ZAG", "ZAG", "ZAG", "ZAG", "LAT", "LAT",
            "VOL", "VOL", "MEI", "MEI", "MEI",
            "ATA", "ATA", "ATA", "ATA", "ATA"
        )
        return positions.mapIndexed { index, position ->
            player(
                name = "$prefix Player ${index + 1}",
                birthDate = "${1997 + (index % 8)}-${(index % 9) + 1}-${(index % 20) + 1}".let { raw ->
                    val parts = raw.split('-')
                    "%04d-%02d-%02d".format(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                },
                position = position
            )
        }
    }

    private fun player(name: String, birthDate: String, position: String) =
        EuropeanRealPlayerTemplate(
            fullName = name,
            birthDateIso = birthDate,
            nationality = "England",
            position = position
        )
}
