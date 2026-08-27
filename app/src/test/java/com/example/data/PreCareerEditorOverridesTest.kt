package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PreCareerEditorOverridesTest {

    private val team = Team(
        id = 77L,
        name = "Clube Teste",
        city = "Cidade",
        state = "ST",
        country = "Brasil",
        division = 1,
        rating = 70,
        stadiumName = "Estádio"
    )

    @Test
    fun `untouched deterministic editor bootstrap does not override factual seed`() {
        val roster = DefaultData.generateRosterForTeam(
            team.id,
            team.rating,
            team.name,
            team.country
        )

        val overrides = detectPreCareerEditorOverrides(
            requestedTeams = listOf(team),
            persistedTeams = listOf(team),
            persistedPlayers = roster
        )

        assertNull(overrides)
    }

    @Test
    fun `edited force 99 and 30-player roster replace canonical career roster only for edited club`() {
        val editorRoster = DefaultData.generateRosterForTeam(
            team.id,
            team.rating,
            team.name,
            team.country
        ).map { it.copy(force = 99, potential = 99) }
        val editedTeam = team.copy(rating = 99)

        val overrides = detectPreCareerEditorOverrides(
            requestedTeams = listOf(team),
            persistedTeams = listOf(editedTeam),
            persistedPlayers = editorRoster
        )
        assertNotNull(overrides)

        val factualCareerRoster = (1L..20L).map { id ->
            Player(
                id = 900_000L + id,
                teamId = team.id,
                name = "Factual $id",
                age = 25,
                position = "MEI",
                force = 70
            )
        }

        val (mergedTeams, mergedPlayers, mergedLoans) = applyPreCareerEditorOverrides(
            seedTeams = listOf(team),
            seedPlayers = factualCareerRoster,
            seedLoans = emptyList(),
            overrides = overrides
        )

        assertEquals(99, mergedTeams.single().rating)
        assertEquals(30, mergedPlayers.count { it.teamId == team.id })
        assertTrue(mergedPlayers.filter { it.teamId == team.id }.all { it.force == 99 })
        assertTrue(mergedLoans.isEmpty())
    }

    @Test
    fun `replacing an edited roster removes factual loans whose player no longer exists`() {
        val editorRoster = DefaultData.generateRosterForTeam(
            team.id,
            team.rating,
            team.name,
            team.country
        ).map { it.copy(force = 99) }
        val overrides = detectPreCareerEditorOverrides(
            requestedTeams = listOf(team),
            persistedTeams = listOf(team),
            persistedPlayers = editorRoster
        )
        assertNotNull(overrides)

        val factualPlayer = Player(
            id = 999_001L,
            teamId = team.id,
            name = "Factual emprestado",
            age = 24,
            position = "ATA",
            force = 75
        )
        val loan = PlayerLoan(
            id = 1L,
            playerId = factualPlayer.id,
            ownerTeamId = team.id,
            borrowerTeamId = team.id,
            startSeason = 2026,
            startWeek = 1,
            durationWeeks = 10,
            remainingWeeks = 10
        )

        val (_, mergedPlayers, mergedLoans) = applyPreCareerEditorOverrides(
            seedTeams = listOf(team),
            seedPlayers = listOf(factualPlayer),
            seedLoans = listOf(loan),
            overrides = overrides
        )

        assertFalse(mergedPlayers.any { it.id == factualPlayer.id })
        assertTrue(mergedLoans.isEmpty())
    }
}
