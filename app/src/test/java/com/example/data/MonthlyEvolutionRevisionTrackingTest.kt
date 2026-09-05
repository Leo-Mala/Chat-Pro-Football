package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.usecase.PlayerEvolutionUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class MonthlyEvolutionRevisionTrackingTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before
    fun setup() = runTest {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        repository.saveTeams(
            listOf(
                Team(
                    id = 1L,
                    name = "Revision A",
                    city = "A",
                    state = "AA",
                    division = 1,
                    rating = 70,
                    trainingCenterLevel = 1
                ),
                Team(
                    id = 2L,
                    name = "Revision B",
                    city = "B",
                    state = "BB",
                    division = 1,
                    rating = 70,
                    trainingCenterLevel = 1
                )
            )
        )
        repository.savePlayers(
            listOf(
                Player(
                    id = 10L,
                    teamId = 1L,
                    name = "Tracked",
                    age = 24,
                    position = "MEI",
                    force = 70,
                    minutosJogados = 90
                ),
                Player(
                    id = 11L,
                    teamId = 1L,
                    name = "Stable",
                    age = 25,
                    position = "ATA",
                    force = 68
                )
            )
        )
        repository.saveGameSave(
            GameSave(currentSeason = 2026, currentWeek = 4, playerTeamId = 1L)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `tracker owns exact trigger set and football columns match digest inputs`() = runTest {
        assertNotNull(repository.prepareMonthlyEvolutionRevisionSnapshot())

        val triggers = linkedMapOf<String, String>()
        db.openHelper.writableDatabase.query(
            """
            SELECT name, sql
            FROM sqlite_master
            WHERE type = 'trigger'
              AND tbl_name = 'players'
              AND name GLOB 'monthly_player_revision_*'
            ORDER BY name ASC
            """.trimIndent()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                triggers[cursor.getString(0)] = cursor.getString(1)
            }
        }

        assertEquals(
            setOf(
                "monthly_player_revision_after_insert",
                "monthly_player_revision_after_delete",
                "monthly_player_revision_after_roster_change",
                "monthly_player_revision_after_football_change"
            ),
            triggers.keys
        )

        val footballSql = requireNotNull(
            triggers["monthly_player_revision_after_football_change"]
        )
        listOf(
            "age",
            "position",
            "force",
            "potential",
            "minutosJogados",
            "mediaNotas",
            "focoTreino",
            "atributosJson",
            "atributos"
        ).forEach { column ->
            assertTrue(
                "Football trigger must compare $column exactly",
                footballSql.contains("OLD.$column IS NOT NEW.$column")
            )
        }

        // A same-named no-op trigger must not be trusted merely because the name exists.
        db.openHelper.writableDatabase.execSQL(
            "DROP TRIGGER monthly_player_revision_after_football_change"
        )
        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER monthly_player_revision_after_football_change
            AFTER UPDATE ON players
            BEGIN
                SELECT 1;
            END
            """.trimIndent()
        )
        assertNull(repository.currentMonthlyEvolutionRevisionSnapshotOrNull())
    }

    @Test
    fun `football and roster epochs advance independently for real player writes`() = runTest {
        val before = requireNotNull(repository.prepareMonthlyEvolutionRevisionSnapshot())
        val original = requireNotNull(repository.getPlayer(10L))

        repository.updatePlayer(original.copy(force = original.force + 1))
        val afterFootball =
            requireNotNull(repository.currentMonthlyEvolutionRevisionSnapshotOrNull())
        assertTrue(afterFootball.footballRevision > before.footballRevision)
        assertEquals(before.rosterRevision, afterFootball.rosterRevision)

        repository.updatePlayer(
            requireNotNull(repository.getPlayer(10L)).copy(teamId = 2L)
        )
        val afterRoster =
            requireNotNull(repository.currentMonthlyEvolutionRevisionSnapshotOrNull())
        assertEquals(afterFootball.footballRevision, afterRoster.footballRevision)
        assertTrue(afterRoster.rosterRevision > afterFootball.rosterRevision)
    }

    @Test
    fun `insert and delete are both visible to roster epoch`() = runTest {
        val before = requireNotNull(repository.prepareMonthlyEvolutionRevisionSnapshot())

        repository.savePlayers(
            listOf(
                Player(
                    id = 12L,
                    teamId = 1L,
                    name = "Inserted",
                    age = 22,
                    position = "VOL",
                    force = 64
                )
            )
        )
        val afterInsert =
            requireNotNull(repository.currentMonthlyEvolutionRevisionSnapshotOrNull())
        assertEquals(before.footballRevision, afterInsert.footballRevision)
        assertTrue(afterInsert.rosterRevision > before.rosterRevision)

        repository.deletePlayer(12L)
        val afterDelete =
            requireNotNull(repository.currentMonthlyEvolutionRevisionSnapshotOrNull())
        assertEquals(afterInsert.footballRevision, afterDelete.footballRevision)
        assertTrue(afterDelete.rosterRevision > afterInsert.rosterRevision)
    }

    @Test
    fun `rolled back player edit also rolls back epoch`() = runTest {
        val before = requireNotNull(repository.prepareMonthlyEvolutionRevisionSnapshot())
        class Rollback : RuntimeException()

        try {
            repository.withTransaction {
                val player = requireNotNull(repository.getPlayer(10L))
                repository.updatePlayer(player.copy(force = player.force + 7))
                throw Rollback()
            }
        } catch (_: Rollback) {
            // Expected: both Player mutation and trigger UPDATE belong to the same SQLite tx.
        }

        val after = requireNotNull(repository.currentMonthlyEvolutionRevisionSnapshotOrNull())
        assertEquals(before, after)
        assertEquals(70, repository.getPlayer(10L)?.force)
    }

    @Test
    fun `editor style football edit between prepare and commit still fails closed`() = runTest {
        val save = requireNotNull(repository.getGameSave())
        val useCase = PlayerEvolutionUseCase(repository)
        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4")
        assertNotNull(plan.expectedPlayerRevision)

        val edited = requireNotNull(repository.getPlayer(10L)).copy(
            force = 91,
            potential = 95,
            atributosJson = "{\"finalizacao\":91}"
        )
        // Same persistence boundary used by GameViewModel.savePlayerFromEditor.
        repository.updatePlayer(edited)

        assertFalse(useCase.commitMonthlyEvolution(plan))
        assertEquals(91, repository.getPlayer(10L)?.force)
    }

    @Test
    fun `team move uses roster-only correction path without football invalidation`() = runTest {
        val save = requireNotNull(repository.getGameSave())
        val useCase = PlayerEvolutionUseCase(repository)
        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4")
        val preparedRevision = requireNotNull(plan.expectedPlayerRevision)

        repository.updatePlayer(
            requireNotNull(repository.getPlayer(10L)).copy(teamId = 2L)
        )
        val movedRevision =
            requireNotNull(repository.currentMonthlyEvolutionRevisionSnapshotOrNull())
        assertEquals(preparedRevision.footballRevision, movedRevision.footballRevision)
        assertTrue(movedRevision.rosterRevision > preparedRevision.rosterRevision)

        assertTrue(
            useCase.commitMonthlyEvolution(
                plan = plan,
                allowWeeklyRosterCorrections = true
            )
        )
        assertEquals(2L, repository.getPlayer(10L)?.teamId)
    }

    @Test
    fun `new player is targeted correction and removed player fails closed`() = runTest {
        val save = requireNotNull(repository.getGameSave())
        val useCase = PlayerEvolutionUseCase(repository)

        val insertionPlan = useCase.prepareMonthlyEvolution(save, "S2026_W4_INSERT")
        repository.savePlayers(
            listOf(
                Player(
                    id = 12L,
                    teamId = 1L,
                    name = "Late arrival",
                    age = 21,
                    position = "LAT",
                    force = 63
                )
            )
        )
        assertTrue(
            useCase.commitMonthlyEvolution(
                plan = insertionPlan,
                allowWeeklyRosterCorrections = true
            )
        )
        assertNotNull(repository.getPlayer(12L))

        // Prepare from the post-insert state, then remove an expected row. A deleted player cannot
        // be recalculated, so weekly correction must still fail closed.
        val deletionSave = requireNotNull(repository.getGameSave())
        val deletionPlan =
            useCase.prepareMonthlyEvolution(deletionSave, "S2026_W4_DELETE")
        repository.deletePlayer(11L)
        assertFalse(
            useCase.commitMonthlyEvolution(
                plan = deletionPlan,
                allowWeeklyRosterCorrections = true
            )
        )
    }

    @Test
    fun `missing or extra tracker infrastructure forces full stale validation`() = runTest {
        val save = requireNotNull(repository.getGameSave())
        val useCase = PlayerEvolutionUseCase(repository)
        val plan = useCase.prepareMonthlyEvolution(save, "S2026_W4")
        assertNotNull(plan.expectedPlayerRevision)

        db.openHelper.writableDatabase.execSQL(
            "DROP TRIGGER IF EXISTS monthly_player_revision_after_football_change"
        )
        db.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER monthly_player_revision_unexpected
            AFTER UPDATE ON players
            BEGIN
                SELECT 1;
            END
            """.trimIndent()
        )
        val edited = requireNotNull(repository.getPlayer(10L)).copy(force = 92)
        repository.updatePlayer(edited)

        assertNull(repository.currentMonthlyEvolutionRevisionSnapshotOrNull())
        assertFalse(useCase.commitMonthlyEvolution(plan))
        assertEquals(92, repository.getPlayer(10L)?.force)
    }

    @Test
    fun `validated monthly writes advance epoch and make a parallel old plan stale`() = runTest {
        val save = requireNotNull(repository.getGameSave())
        val useCase = PlayerEvolutionUseCase(repository)
        val firstPlan = useCase.prepareMonthlyEvolution(save, "S2026_W4_FIRST")
        val parallelOldPlan = useCase.prepareMonthlyEvolution(save, "S2026_W4_PARALLEL")
        val expectedRevision = requireNotNull(firstPlan.expectedPlayerRevision)
        assertEquals(expectedRevision, parallelOldPlan.expectedPlayerRevision)

        // Comparison happens before monthly-owned writes. The reset of minutosJogados is guaranteed
        // to touch player 10 and advance footballRevision, but must not abort this already-validated
        // transaction.
        assertTrue(useCase.commitMonthlyEvolution(firstPlan))
        val afterCommit =
            requireNotNull(repository.currentMonthlyEvolutionRevisionSnapshotOrNull())
        assertTrue(afterCommit.footballRevision > expectedRevision.footballRevision)

        // The separately prepared plan did not participate in that commit and must now be stale.
        assertFalse(useCase.commitMonthlyEvolution(parallelOldPlan))
    }
}
