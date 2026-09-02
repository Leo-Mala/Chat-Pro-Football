from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one anchor, found {count}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) Preserve explicit/manual Player.force as a persistent offset from the attribute-derived index.
path = "app/src/main/java/com/example/data/PlayerEvolutionSystem.kt"
old = '''    fun getCTMultiplier(ctLevel: Int): Double {
        val level = ctLevel.coerceIn(1, 5)
        return 0.7 + (level - 1) * 0.15
    }
'''
new = '''    fun getCTMultiplier(ctLevel: Int): Double {
        val level = ctLevel.coerceIn(1, 5)
        return 0.7 + (level - 1) * 0.15
    }

    /**
     * Player.force is an editable persisted value, while CalculadoraNota is the technical-attribute
     * index shown separately in the UI. Evolution must apply only the attribute-derived DELTA to
     * the persisted force; replacing force with the raw index destroys explicit editor changes
     * (for example 99 -> 68) on the next monthly/post-match tick.
     *
     * For ordinary unedited players whose force already equals the calculated index this is exactly
     * equivalent to the historical calculation.
     */
    internal fun preserveEditedForceAcrossAttributeChange(
        player: Player,
        oldAttributes: Atributos,
        newAttributes: Atributos
    ): Int {
        val position = Posicao.fromCode(player.position)
        val oldIndex = CalculadoraNota.calcularNota(position, oldAttributes)
        val newIndex = CalculadoraNota.calcularNota(position, newAttributes)
        return (player.force + (newIndex - oldIndex)).coerceIn(1, 99)
    }

    /**
     * One-time compatibility repair for careers saved by builds that had already collapsed an
     * explicitly edited 99-strength controlled roster back to its attribute index. The strong
     * majority guard distinguishes that historical corruption from a normal later transfer whose
     * individual force legitimately differs from the rest of a 99 roster.
     */
    internal fun repairHistoricalControlledTeam99Roster(
        team: Team,
        roster: List<Player>
    ): List<Player> {
        if (!team.isPlayerControlled || team.rating != 99 || roster.isEmpty()) return emptyList()
        val materiallyCollapsed = roster.count { it.force <= 94 }
        if (materiallyCollapsed * 2 < roster.size) return emptyList()
        return roster.filter { it.force != 99 }.map { it.copy(force = 99) }
    }
'''
replace_once(path, old, new)

old = '''            val newJson = AtributosConverter.atributosToJson(newAtributos)
            val newCalculatedForce = CalculadoraNota.calcularNota(posEnum, newAtributos)

            val updatedPlayer = player.copy(
                atributosJson = newJson,
                force = newCalculatedForce,
                minutosJogados = 0, // reseta contador mensal
                evolucaoMensal = (newCalculatedForce - player.force).toDouble()
            )

            PlayerEvolutionResult(
                player = updatedPlayer,
                oldAttributes = oldAtributos,
                newAttributes = newAtributos,
                netChange = (newCalculatedForce - player.force).toDouble(),
                historyLogs = historyLogs
            )
'''
new = '''            val newJson = AtributosConverter.atributosToJson(newAtributos)
            val newPersistedForce = preserveEditedForceAcrossAttributeChange(
                player = player,
                oldAttributes = oldAtributos,
                newAttributes = newAtributos
            )
            val forceDelta = (newPersistedForce - player.force).toDouble()

            val updatedPlayer = player.copy(
                atributosJson = newJson,
                force = newPersistedForce,
                minutosJogados = 0, // reseta contador mensal
                evolucaoMensal = forceDelta
            )

            PlayerEvolutionResult(
                player = updatedPlayer,
                oldAttributes = oldAtributos,
                newAttributes = newAtributos,
                netChange = forceDelta,
                historyLogs = historyLogs
            )
'''
replace_once(path, old, new)

old = '''                    val newForce = CalculadoraNota.calcularNota(posEnum, newAttrMap)
                    player.copy(
                        atributosJson = AtributosConverter.atributosToJson(newAttrMap),
                        force = newForce
                    )
'''
new = '''                    val newForce = preserveEditedForceAcrossAttributeChange(
                        player = player,
                        oldAttributes = currentAttr,
                        newAttributes = newAttrMap
                    )
                    player.copy(
                        atributosJson = AtributosConverter.atributosToJson(newAttrMap),
                        force = newForce
                    )
'''
replace_once(path, old, new)

# 2) Production allocation-conscious monthly engine must use the same persisted-force semantics.
path = "app/src/main/java/com/example/data/PlayerEvolutionMonthlyEngine.kt"
old = '''            val newAtributos = values.toAtributos()
            val newCalculatedForce = CalculadoraNota.calcularNota(posEnum, newAtributos)
            val netChange = (newCalculatedForce - player.force).toDouble()
            val hasPersistedDelta = historyLogs.isNotEmpty() || netChange != 0.0

            if (retainUnchangedResults || hasPersistedDelta) {
                val newJson = serializeAttributes(newAtributos)
                val updatedPlayer = player.copy(
                    atributosJson = newJson,
                    force = newCalculatedForce,
                    minutosJogados = 0,
                    evolucaoMensal = netChange
                )
'''
new = '''            val newAtributos = values.toAtributos()
            val newPersistedForce = PlayerEvolutionSystem.preserveEditedForceAcrossAttributeChange(
                player = player,
                oldAttributes = oldAtributos,
                newAttributes = newAtributos
            )
            val netChange = (newPersistedForce - player.force).toDouble()
            val hasPersistedDelta = historyLogs.isNotEmpty() || netChange != 0.0

            if (retainUnchangedResults || hasPersistedDelta) {
                val newJson = serializeAttributes(newAtributos)
                val updatedPlayer = player.copy(
                    atributosJson = newJson,
                    force = newPersistedForce,
                    minutosJogados = 0,
                    evolucaoMensal = netChange
                )
'''
replace_once(path, old, new)

# 3) Existing careers already corrupted by the old recalculation are repaired on normal slot open.
path = "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt"
old = '''            val targetRepo = session.repository
            val integrityUseCase = DatabaseIntegrityUseCase(targetRepo)
            integrityUseCase.repairDatabase()
        } catch (e: Exception) {
'''
new = '''            val targetRepo = session.repository
            val integrityUseCase = DatabaseIntegrityUseCase(targetRepo)
            integrityUseCase.repairDatabase()

            val save = targetRepo.getGameSave()
            if (save != null) {
                val controlledTeam = targetRepo.getTeam(save.playerTeamId)
                if (controlledTeam != null) {
                    val currentRoster = targetRepo.getPlayersByTeam(controlledTeam.id)
                    val repairedPlayers = PlayerEvolutionSystem.repairHistoricalControlledTeam99Roster(
                        team = controlledTeam,
                        roster = currentRoster
                    )
                    if (repairedPlayers.isNotEmpty()) {
                        targetRepo.updatePlayers(repairedPlayers)
                    }
                }
            }
        } catch (e: Exception) {
'''
replace_once(path, old, new)

# 4) Focused deterministic regressions for the exact manual-test failure and safe migration guard.
test_path = Path("app/src/test/java/com/example/data/ManualStrengthPersistenceRegressionTest.kt")
test_path.write_text('''package com.example.data

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
''', encoding="utf-8")
