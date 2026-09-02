from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: anchor count={count}, expected=1")
    return text.replace(old, new, 1)


maintenance_path = Path("app/src/main/java/com/example/data/MonthlyEvolutionMaintenanceQueries.kt")
maintenance = maintenance_path.read_text(encoding="utf-8")

snapshot_anchor = '''internal fun Player.toMonthlyEvolutionInputSnapshot(): MonthlyEvolutionInputSnapshot =
    MonthlyEvolutionInputSnapshot(
'''
state_block = '''data class MonthlyEvolutionPlayerState(
    val id: Long,
    val atributosJson: String?,
    val force: Int,
    val evolucaoMensal: Double
)

internal fun Player.toMonthlyEvolutionPlayerState(): MonthlyEvolutionPlayerState =
    MonthlyEvolutionPlayerState(
        id = id,
        atributosJson = atributosJson,
        force = force,
        evolucaoMensal = evolucaoMensal
    )

internal fun Player.toMonthlyEvolutionInputSnapshot(): MonthlyEvolutionInputSnapshot =
    MonthlyEvolutionInputSnapshot(
'''
maintenance = replace_once(
    maintenance,
    snapshot_anchor,
    state_block,
    "compact monthly state",
)

writer_anchor = '''/**
 * Returns stable fingerprints for evolution-history rows already persisted for one monthly period.
'''
writer_block = '''internal fun GameRepository.applyMonthlyEvolutionPlayerStateDeltas(
    states: Collection<MonthlyEvolutionPlayerState>
): Int {
    if (states.isEmpty()) return 0
    val statement = db.openHelper.writableDatabase.compileStatement(
        """
        UPDATE players
        SET atributosJson = ?, force = ?, minutosJogados = 0, evolucaoMensal = ?
        WHERE id = ?
        """.trimIndent()
    )
    var updated = 0
    for (state in states) {
        statement.clearBindings()
        if (state.atributosJson == null) statement.bindNull(1) else statement.bindString(1, state.atributosJson)
        statement.bindLong(2, state.force.toLong())
        statement.bindDouble(3, state.evolucaoMensal)
        statement.bindLong(4, state.id)
        updated += statement.executeUpdateDelete()
    }
    return updated
}

/**
 * Returns stable fingerprints for evolution-history rows already persisted for one monthly period.
'''
maintenance = replace_once(
    maintenance,
    writer_anchor,
    writer_block,
    "compact monthly writer",
)
maintenance_path.write_text(maintenance, encoding="utf-8")


usecase_path = Path("app/src/main/java/com/example/usecase/PlayerEvolutionUseCase.kt")
usecase = usecase_path.read_text(encoding="utf-8")
usecase = replace_once(
    usecase,
    '''import com.example.data.MonthlyEvolutionInputSnapshot\nimport com.example.data.Player\n''',
    '''import com.example.data.MonthlyEvolutionInputSnapshot\nimport com.example.data.MonthlyEvolutionPlayerState\nimport com.example.data.Player\n''',
    "state import",
)
usecase = replace_once(
    usecase,
    '''import com.example.data.applyMonthlyEvolutionPlayerStates\n''',
    '''import com.example.data.applyMonthlyEvolutionPlayerStateDeltas\nimport com.example.data.applyMonthlyEvolutionPlayerStates\n''',
    "writer import",
)
usecase = replace_once(
    usecase,
    '''import com.example.data.toMonthlyEvolutionInputSnapshot\n''',
    '''import com.example.data.toMonthlyEvolutionInputSnapshot\nimport com.example.data.toMonthlyEvolutionPlayerState\n''',
    "state extension import",
)

plan_old = '''    /** Training-center level influences evolution and therefore participates in stale validation. */
    val expectedTrainingCenterLevels: Map<Long, Int> = emptyMap()
)
'''
plan_new = '''    /** Training-center level influences evolution and therefore participates in stale validation. */
    val expectedTrainingCenterLevels: Map<Long, Int> = emptyMap(),
    /**
     * Compact four-column persistence state for the weekly/season path. Existing detailed/manual
     * plans remain source-compatible through the default conversion from [updatedPlayers].
     */
    val updatedPlayerStates: List<MonthlyEvolutionPlayerState> =
        updatedPlayers.map { it.toMonthlyEvolutionPlayerState() }
)
'''
usecase = replace_once(usecase, plan_old, plan_new, "plan compact states")

prepare_old = '''        val evolutionResults = ArrayList<PlayerEvolutionResult>(
            if (retainDetailedResults) expectedPlayerCount else minOf(expectedPlayerCount, 4096)
        )
        val changedPlayers = ArrayList<Player>()
        val historyLogs = ArrayList<HistoricoEvolucao>()
'''
prepare_new = '''        val evolutionResults = ArrayList<PlayerEvolutionResult>(
            if (retainDetailedResults) expectedPlayerCount else 0
        )
        val changedPlayers = if (retainDetailedResults) ArrayList<Player>() else null
        val changedPlayerStates = if (retainDetailedResults) null else ArrayList<MonthlyEvolutionPlayerState>()
        val historyLogs = ArrayList<HistoricoEvolucao>()
'''
usecase = replace_once(usecase, prepare_old, prepare_new, "bounded plan collections")

process_old = '''            evolutionResults.addAll(batchResults)

            for (result in batchResults) {
                if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) changedPlayers.add(result.player)
                if (result.historyLogs.isNotEmpty()) historyLogs.addAll(result.historyLogs)
            }
'''
process_new = '''            if (detailed) evolutionResults.addAll(batchResults)

            for (result in batchResults) {
                if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) {
                    if (detailed) {
                        changedPlayers!!.add(result.player)
                    } else {
                        changedPlayerStates!!.add(result.player.toMonthlyEvolutionPlayerState())
                    }
                }
                if (result.historyLogs.isNotEmpty()) historyLogs.addAll(result.historyLogs)
            }
'''
usecase = replace_once(usecase, process_old, process_new, "bounded result retention")

return_old = '''            results = evolutionResults,
            updatedPlayers = changedPlayers,
            historyLogs = historyLogs,
            expectedInputs = expectedInputs,
            expectedPlayerCount = expectedPlayerCount,
            expectedTrainingCenterLevels = expectedTrainingLevels
'''
return_new = '''            results = evolutionResults,
            updatedPlayers = changedPlayers ?: emptyList(),
            historyLogs = historyLogs,
            expectedInputs = expectedInputs,
            expectedPlayerCount = expectedPlayerCount,
            expectedTrainingCenterLevels = expectedTrainingLevels,
            updatedPlayerStates = changedPlayerStates ?: emptyList()
'''
usecase = replace_once(usecase, return_old, return_new, "plan construction")

commit_old = '''        var playersToPersist = plan.updatedPlayers
        var historyToPersist = plan.historyLogs
'''
commit_new = '''        var playerStatesToPersist = plan.updatedPlayerStates
        var historyToPersist = plan.historyLogs
'''
usecase = replace_once(usecase, commit_old, commit_new, "commit compact states")

correction_old = '''            val correctedUpdatedPlayers = ArrayList<Player>()
            val correctedHistory = ArrayList<HistoricoEvolucao>()
            for (result in correctedResults) {
                if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) {
                    correctedUpdatedPlayers.add(result.player)
                }
                if (result.historyLogs.isNotEmpty()) correctedHistory.addAll(result.historyLogs)
            }

            playersToPersist = buildList {
                addAll(plan.updatedPlayers.filter { it.id !in correctionIds })
                addAll(correctedUpdatedPlayers)
            }
'''
correction_new = '''            val correctedPlayerStates = ArrayList<MonthlyEvolutionPlayerState>()
            val correctedHistory = ArrayList<HistoricoEvolucao>()
            for (result in correctedResults) {
                if (result.historyLogs.isNotEmpty() || result.netChange != 0.0) {
                    correctedPlayerStates.add(result.player.toMonthlyEvolutionPlayerState())
                }
                if (result.historyLogs.isNotEmpty()) correctedHistory.addAll(result.historyLogs)
            }

            playerStatesToPersist = buildList {
                addAll(plan.updatedPlayerStates.filter { it.id !in correctionIds })
                addAll(correctedPlayerStates)
            }
'''
usecase = replace_once(usecase, correction_old, correction_new, "correction compact states")

persist_old = '''        if (playersToPersist.isNotEmpty()) {
            check(repository.applyMonthlyEvolutionPlayerStates(playersToPersist) == playersToPersist.size) {
                "Falha fail-closed ao persistir delta de evolução mensal."
            }
        }
'''
persist_new = '''        if (playerStatesToPersist.isNotEmpty()) {
            check(repository.applyMonthlyEvolutionPlayerStateDeltas(playerStatesToPersist) == playerStatesToPersist.size) {
                "Falha fail-closed ao persistir delta de evolução mensal."
            }
        }
'''
usecase = replace_once(usecase, persist_old, persist_new, "compact state persistence")
usecase_path.write_text(usecase, encoding="utf-8")


test_path = Path("app/src/test/java/com/example/usecase/MonthlyEvolutionCompactPlanMemoryRegressionTest.kt")
test_path.write_text(r'''package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MonthlyEvolutionCompactPlanMemoryRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: PlayerEvolutionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        useCase = PlayerEvolutionUseCase(repository)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `season monthly plan retains compact write states instead of full changed players and results`() = runTest {
        val playerCount = 256
        val team = Team(
            id = 1L,
            name = "Heap FC",
            city = "BH",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 75,
            trainingCenterLevel = 3
        )
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 12, playerTeamId = team.id)
        repository.saveGameSave(save)
        repository.savePlayers(
            List(playerCount) { index ->
                Player(
                    id = index.toLong() + 1L,
                    teamId = team.id,
                    name = "Heap %04d".format(index),
                    age = 21,
                    position = if (index % 11 == 0) "GOL" else "ATA",
                    force = if (index == 0) 99 else 65,
                    potential = 99,
                    minutosJogados = 360,
                    mediaNotas = 8.0,
                    salary = 12_345L + index,
                    contractDurationWeeks = 77
                )
            }
        )

        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W12")

        assertEquals(playerCount, plan.expectedPlayerCount)
        assertEquals(playerCount, plan.expectedInputs.size)
        assertTrue("compact weekly plan must not retain full PlayerEvolutionResult objects", plan.results.isEmpty())
        assertTrue("compact weekly plan must not retain full changed Player entities", plan.updatedPlayers.isEmpty())
        assertTrue("changed players still need compact persistence state", plan.updatedPlayerStates.isNotEmpty())
        assertEquals(plan.updatedPlayerStates.size, plan.updatedPlayerStates.map { it.id }.distinct().size)

        val sentinelBefore = requireNotNull(repository.getPlayer(1L))
        assertTrue(useCase.commitMonthlyEvolution(plan))
        val sentinelAfter = requireNotNull(repository.getPlayer(1L))

        assertEquals(99, sentinelAfter.force)
        assertEquals(0, sentinelAfter.minutosJogados)
        assertEquals(sentinelBefore.salary, sentinelAfter.salary)
        assertEquals(sentinelBefore.contractDurationWeeks, sentinelAfter.contractDurationWeeks)
        assertFalse(repository.getHistoricoPorJogador(1L).isEmpty())
    }

    @Test
    fun `detailed standalone path retains legacy result contract`() = runTest {
        val team = Team(id = 2L, name = "Detailed FC", city = "SP", state = "SP", division = 1, rating = 75)
        repository.saveTeams(listOf(team))
        val save = GameSave(currentSeason = 2026, currentWeek = 4, playerTeamId = team.id)
        repository.saveGameSave(save)
        repository.savePlayers(
            List(12) { index ->
                Player(
                    id = 1_000L + index,
                    teamId = team.id,
                    name = "Detailed $index",
                    age = 22,
                    position = "MEI",
                    force = 65,
                    potential = 95,
                    minutosJogados = 180,
                    mediaNotas = 7.8
                )
            }
        )

        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4", retainDetailedResults = true)

        assertEquals(12, plan.results.size)
        assertTrue(plan.updatedPlayers.isNotEmpty())
        assertEquals(
            plan.updatedPlayers.map { it.id },
            plan.updatedPlayerStates.map { it.id }
        )
    }
}
''', encoding="utf-8")

print("season simulation memory patch prepared")
