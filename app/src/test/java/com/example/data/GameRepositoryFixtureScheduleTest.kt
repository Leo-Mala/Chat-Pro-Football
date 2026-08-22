package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        seedTeams(10L, 20L, 30L)
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
    fun pendingWeekendFixtureIsReturnedBeforePlayedMidweekFixtureForManualFlow() = runTest {
        seedTeams(1L, 2L, 3L)
        val playedMidweek = Fixture(
            id = 20L,
            season = 2026,
            week = 10,
            matchSlot = MatchSlot.MIDWEEK,
            homeTeamId = 1L,
            awayTeamId = 2L,
            competitionType = "CONTINENTAL_T1",
            homeScore = 2,
            awayScore = 1,
            isPlayed = true
        )
        val pendingWeekend = Fixture(
            id = 21L,
            season = 2026,
            week = 10,
            matchSlot = MatchSlot.WEEKEND,
            homeTeamId = 3L,
            awayTeamId = 1L,
            competitionType = "SERIE_A"
        )
        repository.saveFixtures(listOf(playedMidweek, pendingWeekend))

        val userFixtures = repository.getFixturesForWeek(2026, 10)
            .filter { it.homeTeamId == 1L || it.awayTeamId == 1L }

        assertEquals(2, userFixtures.size)
        assertEquals(pendingWeekend.id, userFixtures.first().id)
        assertFalse(userFixtures.first().isPlayed)
        assertEquals(MatchSlot.WEEKEND, userFixtures.first().matchSlot)
    }

    @Test
    fun rescheduleIntoOccupiedSlotIsRejected() = runTest {
        seedTeams(1L, 2L, 3L)
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

    @Test
    fun unknownNonVirtualTeamReferenceIsRejectedWithoutPersistingFixture() = runTest {
        seedTeams(1L)
        val invalid = Fixture(
            id = 40L,
            season = 2026,
            week = 4,
            matchSlot = MatchSlot.WEEKEND,
            homeTeamId = 1L,
            awayTeamId = GlobalFootballSystem.VIRTUAL_TEAM_ID_FLOOR - 1L,
            competitionType = "SERIE_A"
        )

        try {
            repository.saveFixtures(listOf(invalid))
            fail("Fixture com clube real não persistido deveria falhar fechado.")
        } catch (_: IllegalArgumentException) {
            // esperado
        }

        assertTrue(repository.getAllFixtures().isEmpty())
    }

    @Test
    fun fullCalendarReferenceValidationSupportsMoreThanSqliteBindLimit() = runTest {
        val teamIds = (1L..1_200L).toList()
        repository.saveTeams(
            teamIds.map { id ->
                Team(
                    id = id,
                    name = "Time $id",
                    city = "Cidade $id",
                    state = "BR",
                    country = "Brasil",
                    division = 1
                )
            }
        )
        val fixtures = teamIds.chunked(2).mapIndexed { index, pair ->
            Fixture(
                id = 10_000L + index,
                season = 2026,
                week = 1,
                matchSlot = MatchSlot.WEEKEND,
                homeTeamId = pair[0],
                awayTeamId = pair[1],
                competitionType = "SERIE_A"
            )
        }

        repository.saveFixtures(fixtures)

        assertEquals(600, repository.getFixturesForSeason(2026).size)
    }

    private suspend fun seedTeams(vararg ids: Long) {
        repository.saveTeams(
            ids.map { id ->
                Team(
                    id = id,
                    name = "Time $id",
                    city = "Cidade $id",
                    state = "BR",
                    country = "Brasil",
                    division = 1
                )
            }
        )
    }
}
