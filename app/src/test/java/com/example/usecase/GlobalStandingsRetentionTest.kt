package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GlobalLeagueStanding
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GlobalStandingsRetentionTest {

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
    fun newerSeasonPrunesOnlyOldLowerDivisions() = runBlocking {
        repository.saveGlobalStandingsForSeason(
            2026,
            listOf(
                standing(2026, division = 1, teamId = 1, position = 1),
                standing(2026, division = 2, teamId = 2, position = 1)
            )
        )

        repository.saveGlobalStandingsForSeason(
            2027,
            listOf(
                standing(2027, division = 1, teamId = 1, position = 1),
                standing(2027, division = 2, teamId = 2, position = 1)
            )
        )

        val oldSeason = repository.getGlobalStandingsForSeason(2026)
        assertEquals(1, oldSeason.size)
        assertEquals(1, oldSeason.single().division)
        assertEquals(1L, oldSeason.single().teamId)

        val latestSeason = repository.getGlobalStandingsForSeason(2027)
        assertEquals(2, latestSeason.size)
        assertEquals(listOf(1, 2), latestSeason.map { it.division })
    }

    private fun standing(
        season: Int,
        division: Int,
        teamId: Long,
        position: Int
    ) = GlobalLeagueStanding(
        season = season,
        country = "Brasil",
        division = division,
        teamId = teamId,
        position = position,
        points = 3,
        played = 1,
        wins = 1,
        draws = 0,
        losses = 0,
        goalsFor = 1,
        goalsAgainst = 0,
        goalDifference = 1
    )
}
