package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameRepositoryFixtureScheduleTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun scoreUpdateDoesNotBlockOnPreexistingLegacyConflict() = runTest {
        val league = Fixture(
            id = 1L,
            season = 2026,
            week = 12,
            matchSlot = MatchSlot.WEEKEND,
            homeTeamId = 10L,
            awayTeamId = 20L,
            competitionType = "SERIE_A"
        )
        val legacyConflict = Fixture(
            id = 2L,
            season = 2026,
            week = 12,
            matchSlot = MatchSlot.WEEKEND,
            homeTeamId = 10L,
            awayTeamId = 30L,
            competitionType = "LEGACY_UNKNOWN"
        )

        db.fixtureDao().insertFixtures(listOf(league, legacyConflict))

        repository.updateFixture(
            league.copy(homeScore = 2, awayScore = 1, isPlayed = true)
        )

        val updated = repository.getFixturesForWeek(2026, 12).first { it.id == 1L }
        assertEquals(2, updated.homeScore)
        assertEquals(1, updated.awayScore)
    }

    @Test
    fun rescheduleIntoOccupiedSlotIsRejected() = runTest {
        val existing = Fixture(
            id = 10L,
            season = 2026,
            week = 20,
            matchSlot = MatchSlot.MIDWEEK,
            homeTeamId = 1L,
            awayTeamId = 2L,
            competitionType = "COPA"
        )
        val movable = Fixture(
            id = 11L,
            season = 2026,
            week = 21,
            matchSlot = MatchSlot.MIDWEEK,
            homeTeamId = 1L,
            awayTeamId = 3L,
            competitionType = "CONTINENTAL_T1"
        )
        db.fixtureDao().insertFixtures(listOf(existing, movable))

        try {
            repository.updateFixture(movable.copy(week = 20))
            fail("Remarcar para um slot já ocupado pelo mesmo clube deveria ser rejeitado.")
        } catch (_: IllegalArgumentException) {
            // esperado
        }
    }
}
