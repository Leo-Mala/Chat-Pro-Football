package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class SuperMundialSystemTest {

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
    fun quarterfinalsProgressThroughWeek40AndChampionIsRecordedOnlyOnce() = runTest {
        val season = 2026
        val quarterfinals = listOf(
            Fixture(id = 1L, season = season, week = 38, homeTeamId = 101L, awayTeamId = 102L, homeScore = 2, awayScore = 0, competitionType = "WORLD_CUP", isPlayed = true),
            Fixture(id = 2L, season = season, week = 38, homeTeamId = 103L, awayTeamId = 104L, homeScore = 1, awayScore = 0, competitionType = "WORLD_CUP", isPlayed = true),
            Fixture(id = 3L, season = season, week = 38, homeTeamId = 105L, awayTeamId = 106L, homeScore = 3, awayScore = 1, competitionType = "WORLD_CUP", isPlayed = true),
            Fixture(id = 4L, season = season, week = 38, homeTeamId = 107L, awayTeamId = 108L, homeScore = 2, awayScore = 1, competitionType = "WORLD_CUP", isPlayed = true)
        )
        repository.saveFixtures(quarterfinals)

        SuperMundialSystem.processProgression(season, 38, repository)

        val semifinals = repository.getFixturesForWeek(season, 39)
            .filter { it.competitionType == "WORLD_CUP" }
        assertEquals(2, semifinals.size)

        repository.updateFixtures(
            semifinals.mapIndexed { index, fixture ->
                fixture.copy(
                    homeScore = if (index == 0) 2 else 1,
                    awayScore = 0,
                    isPlayed = true
                )
            }
        )

        SuperMundialSystem.processProgression(season, 39, repository)

        val finals = repository.getFixturesForWeek(season, GameCalendar.WEEKS_PER_SEASON)
            .filter { it.competitionType == "WORLD_CUP" }
        assertEquals(1, finals.size)

        repository.updateFixture(
            finals.single().copy(homeScore = 1, awayScore = 0, isPlayed = true)
        )

        SuperMundialSystem.processProgression(season, GameCalendar.WEEKS_PER_SEASON, repository)

        val recordsAfterFirstPass = repository.getAllHistoricalRecords()
            .filter { it.season == season && it.competitionName.contains("Mundial") }
        assertEquals(1, recordsAfterFirstPass.size)
        assertTrue(recordsAfterFirstPass.single().championTeamName.isNotBlank())

        SuperMundialSystem.processProgression(season, GameCalendar.WEEKS_PER_SEASON, repository)

        val recordsAfterSecondPass = repository.getAllHistoricalRecords()
            .filter { it.season == season && it.competitionName.contains("Mundial") }
        assertEquals(
            "Reprocessar a semana 40 não pode duplicar o registro do campeão.",
            1,
            recordsAfterSecondPass.size
        )
    }
}
