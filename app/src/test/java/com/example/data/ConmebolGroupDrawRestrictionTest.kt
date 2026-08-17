package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConmebolGroupDrawRestrictionTest {

    @Test
    fun `draw avoids same-country clubs when a valid assignment exists`() {
        val countries = listOf(
            "Brasil",
            "Argentina",
            "Bolívia",
            "Chile",
            "Colômbia",
            "Equador",
            "Paraguai",
            "Uruguai"
        )
        val teams = (0 until 32).map { offset ->
            Team(
                id = offset + 1L,
                name = "Clube ${offset + 1}",
                city = "Cidade ${offset + 1}",
                state = "SA",
                country = countries[offset % countries.size],
                division = 1,
                rating = 100 - offset
            )
        }
        val ranked = teams.sortedWith(compareByDescending<Team> { it.rating }.thenBy { it.id })
        val potByTeamId = ranked.chunked(8)
            .flatMapIndexed { potIndex, pot -> pot.map { it.id to potIndex } }
            .toMap()

        val groups = ConmebolCompetitionSystem.drawGroups(
            season = 2026,
            teams = teams,
            competitionType = ConmebolCompetitionSystem.LIBERTADORES
        )

        assertEquals(8, groups.size)
        groups.forEach { group ->
            assertEquals(4, group.size)
            assertEquals(setOf(0, 1, 2, 3), group.map { potByTeamId.getValue(it.id) }.toSet())
            assertEquals(
                "Com um clube de cada país em cada pote, não há justificativa para repetir associação no grupo.",
                4,
                group.map { it.country }.distinct().size
            )
        }

        val second = ConmebolCompetitionSystem.drawGroups(
            season = 2026,
            teams = teams,
            competitionType = ConmebolCompetitionSystem.LIBERTADORES
        )
        assertEquals(groups, second)
        assertTrue(groups.flatten().map { it.id }.toSet() == teams.map { it.id }.toSet())
    }
}
