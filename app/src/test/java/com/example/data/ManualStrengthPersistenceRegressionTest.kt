package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualStrengthPersistenceRegressionTest {

    private fun attributesAtTechnicalIndex50(): Atributos = Atributos(reflexos = 51)

    @Test
    fun `monthly evolution does not collapse explicitly edited force 99 to attribute index`() {
        val player = Player(
            id = 1L,
            teamId = 10L,
            name = "Edited 99",
            age = 24,
            position = "MEI",
            force = 99,
            potential = 50,
            atributos = attributesAtTechnicalIndex50()
        )
        val team = Team(
            id = 10L,
            name = "Edited Club",
            city = "City",
            state = "ST",
            division = 1,
            isPlayerControlled = true,
            rating = 99
        )

        val result = PlayerEvolutionMonthlyEngine.process(
            players = listOf(player),
            teamsMap = mapOf(team.id to team),
            periodDate = "2026-04"
        ).single()

        assertEquals(99, result.player.force)
        assertEquals(0.0, result.netChange, 0.0)
    }

    @Test
    fun `ordinary unedited force still follows attribute derived delta`() {
        val oldAttributes = attributesAtTechnicalIndex50()
        val position = Posicao.MEIA
        val oldIndex = CalculadoraNota.calcularNota(position, oldAttributes)
        val newAttributes = oldAttributes.copy(
            passe = 80,
            primeiroToque = 80,
            drible = 80,
            controleBola = 80
        )
        val newIndex = CalculadoraNota.calcularNota(position, newAttributes)
        val player = Player(
            id = 2L,
            teamId = 10L,
            name = "Normal",
            age = 24,
            position = "MEI",
            force = oldIndex,
            atributos = oldAttributes
        )

        val persisted = PlayerEvolutionSystem.preserveEditedForceAcrossAttributeChange(
            player,
            oldAttributes,
            newAttributes
        )

        assertEquals(newIndex, persisted)
    }

    @Test
    fun `old controlled 99 save with majority collapsed forces self heals`() {
        val team = Team(
            id = 10L,
            name = "Edited Club",
            city = "City",
            state = "ST",
            division = 1,
            isPlayerControlled = true,
            rating = 99
        )
        val roster = (1L..30L).map { id ->
            Player(
                id = id,
                teamId = team.id,
                name = "P$id",
                age = 24,
                position = "MEI",
                force = if (id <= 24L) 68 + (id % 20).toInt() else 99
            )
        }

        val repaired = PlayerEvolutionSystem.repairHistoricalControlledTeam99Roster(team, roster)

        assertEquals(24, repaired.size)
        assertTrue(repaired.all { it.force == 99 })
    }

    @Test
    fun `single legitimate lower force transfer is not promoted by compatibility repair`() {
        val team = Team(
            id = 10L,
            name = "Edited Club",
            city = "City",
            state = "ST",
            division = 1,
            isPlayerControlled = true,
            rating = 99
        )
        val roster = (1L..29L).map { id ->
            Player(id = id, teamId = team.id, name = "P$id", age = 24, position = "MEI", force = 99)
        } + Player(id = 30L, teamId = team.id, name = "New signing", age = 24, position = "ATA", force = 85)

        assertTrue(PlayerEvolutionSystem.repairHistoricalControlledTeam99Roster(team, roster).isEmpty())
    }
}
