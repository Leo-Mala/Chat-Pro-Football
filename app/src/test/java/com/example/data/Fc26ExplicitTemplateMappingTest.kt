package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Fc26ExplicitTemplateMappingTest {
    @Test
    fun `explicit template mapping follows audited source id country and target name not positional team id`() {
        val dataset = datasetFor("CA Banfield", 110404L, leagueId = 353L)
        val teams = listOf(
            team(9_999L, "Banfield", "Argentina"),
            team(8_888L, "Banfield", "Teste")
        )

        val match = Fc26ClubMatcher.match(dataset, teams).single()

        assertEquals(Fc26ClubMatchStatus.MATCHED, match.status)
        assertEquals(9_999L, match.targetTeamId)
        assertEquals("Banfield", match.targetTeamName)
    }

    @Test
    fun `explicit template mapping does not fall through to wrong country when audited target is absent`() {
        val dataset = datasetFor("CA Banfield", 110404L, leagueId = 353L)
        val teams = listOf(team(8_888L, "Banfield", "Teste"))

        val match = Fc26ClubMatcher.match(dataset, teams).single()

        assertEquals(Fc26ClubMatchStatus.UNMATCHED, match.status)
        assertNull(match.targetTeamId)
    }

    private fun team(id: Long, name: String, country: String) = Team(
        id = id,
        name = name,
        city = "X",
        state = "X",
        country = country,
        division = 1,
        rating = 70
    )

    private fun datasetFor(clubName: String, clubId: Long, leagueId: Long): Fc26Dataset {
        val player = Fc26NormalizedPlayer(
            sourcePlayerId = clubId + 1_000L,
            shortName = "T",
            fullName = "Test Player $clubId",
            sourceAge = 25,
            birthDateIso = "2000-01-01",
            heightCm = 180,
            weightKg = 75,
            nationality = "Test",
            positions = listOf("CM"),
            overall = 70,
            potential = 75,
            valueEur = 1_000_000,
            wageEur = 10_000,
            leagueId = leagueId,
            leagueName = "Test League",
            clubTeamId = clubId,
            clubName = clubName,
            clubPosition = "CM",
            clubLoanedFrom = null,
            contractUntilYear = 2028,
            preferredFoot = "Right",
            weakFoot = 3,
            skillMoves = 3,
            internationalReputation = 1,
            workRate = "Medium/Medium",
            releaseClauseEur = 0,
            summaryPace = 70,
            summaryShooting = 70,
            summaryPassing = 70,
            summaryDribbling = 70,
            summaryDefending = 70,
            summaryPhysic = 70,
            atributos = Atributos()
        )
        return Fc26Dataset(
            Fc26DatasetManifest(
                schemaVersion = 1,
                datasetSource = "FC26",
                datasetVersion = "2025-09-19",
                sourceFile = "test.csv",
                sourceSha256 = "source",
                assetFile = "test.tsv.gz",
                assetSha256 = "asset",
                playerCount = 1,
                clubCount = 1,
                leagueCount = 1,
                nationalityCount = 1,
                freeAgentCount = 0,
                loanedPlayerCount = 0,
                validationStatus = "VALIDATED",
                money = Fc26MoneyManifest("EUR", "BRL", 6.2567, "2025-09-19", "test")
            ),
            listOf(player)
        )
    }
}
