package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `Manchester United official snapshot is gameplay ready and excludes active loan list`() {
        val snapshot = ManchesterUnitedSquad2026_27.snapshot

        assertEquals(30, snapshot.players.size)
        assertEquals(30, snapshot.players.map { it.stableId }.distinct().size)
        assertEquals(EuropeanSquadCoverage.GAMEPLAY_READY_FACTUAL_SNAPSHOT, snapshot.coverage())
        assertEquals(4, snapshot.players.count { it.position == "GOL" })
        assertEquals(7, snapshot.players.count { it.position == "ATA" })
        assertEquals(10, snapshot.players.count { it.position == "ZAG" || it.position == "LAT" })
        assertEquals(9, snapshot.players.count { it.position == "MEI" || it.position == "VOL" })
        assertFalse(snapshot.players.any { it.fullName == "Altay Bayindir" })
        assertFalse(snapshot.players.any { it.fullName == "Andre Onana" })
        assertEquals(1, snapshot.players.single { it.fullName == "Senne Lammens" }.shirtNumber)
    }

    @Test
    fun `Manchester United snapshot records only official club sources`() {
        val snapshot = ManchesterUnitedSquad2026_27.snapshot

        assertTrue(snapshot.sourceRefs.isNotEmpty())
        assertTrue(snapshot.sourceRefs.all { it.startsWith("https://www.manutd.com/") })
        assertEquals("2026-08-18", snapshot.verifiedAsOfIso)
    }

    @Test
    fun `global factual catalog exposes one audited club and leaves the rest visibly missing`() {
        val expectedTotal = EuropeanDomesticBaseline2026_27.associations
            .sumOf { it.verifiedTopFlightClubs.size }
        val catalog = EuropeanRealSquads.catalog

        assertEquals(1, catalog.all().size)
        assertEquals(1, catalog.gameplayReadyClubs().size)
        assertEquals(expectedTotal - 1, catalog.missingTopFlightClubs().size)
        assertEquals(
            ManchesterUnitedSquad2026_27.snapshot,
            catalog.find("Inglaterra", "Manchester United")
        )
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
