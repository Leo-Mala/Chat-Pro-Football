package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class TargetedUiFlowDaoTest {

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
    fun teamFlowReadsOnlyRequestedTeamAndReturnsNullWhenMissing() = runTest {
        repository.saveTeams(
            listOf(
                Team(10L, "Time Dez", "Cidade", "MG", "Brasil", 1),
                Team(20L, "Time Vinte", "Cidade", "MG", "Brasil", 1)
            )
        )

        assertEquals("Time Dez", repository.getTeamFlow(10L).first()?.name)
        assertNull(repository.getTeamFlow(999L).first())
    }

    @Test
    fun dashboardLeagueFlowsExcludeOtherDivisionsCompetitionsAndSeasons() = runTest {
        repository.saveTeams(
            listOf(
                Team(10L, "Usuário", "Cidade", "MG", "Brasil", 1),
                Team(20L, "Rival", "Cidade", "SP", "Brasil", 1),
                Team(30L, "Segunda", "Cidade", "RJ", "Brasil", 2),
                Team(40L, "Exterior", "Cidade", "", "Argentina", 1)
            )
        )
        repository.saveFixtures(
            listOf(
                Fixture(1L, 2026, 1, 10L, 20L, 2, 0, competitionType = "SERIE_A", isPlayed = true),
                Fixture(2L, 2026, 2, 10L, 20L, competitionType = "SERIE_A", isPlayed = false),
                Fixture(3L, 2026, 3, 10L, 20L, 1, 0, competitionType = "COPA", isPlayed = true),
                Fixture(4L, 2025, 4, 10L, 20L, 1, 1, competitionType = "SERIE_A", isPlayed = true)
            )
        )

        val teams = repository.getTeamsByCountryDivisionFlow("Brasil", 1).first()
        val fixtures = repository.getPlayedFixturesForCompetitionFlow(2026, "SERIE_A").first()

        assertEquals(listOf(10L, 20L), teams.map { it.id }.sorted())
        assertEquals(listOf(1L), fixtures.map { it.id })
    }

    @Test
    fun nextFixtureFlowSkipsPlayedAndOtherTeamsAndReturnsEarliestPendingMatch() = runTest {
        repository.saveTeams(
            listOf(
                Team(10L, "Usuário", "Cidade", "MG", "Brasil", 1),
                Team(20L, "Rival A", "Cidade", "MG", "Brasil", 1),
                Team(30L, "Rival B", "Cidade", "MG", "Brasil", 1),
                Team(40L, "Outro", "Cidade", "MG", "Brasil", 1)
            )
        )
        repository.saveFixtures(
            listOf(
                Fixture(
                    id = 1L,
                    season = 2026,
                    week = 3,
                    matchSlot = MatchSlot.WEEKEND,
                    homeTeamId = 10L,
                    awayTeamId = 20L,
                    competitionType = "SERIE_A",
                    isPlayed = true
                ),
                Fixture(
                    id = 2L,
                    season = 2026,
                    week = 4,
                    matchSlot = MatchSlot.WEEKEND,
                    homeTeamId = 30L,
                    awayTeamId = 40L,
                    competitionType = "SERIE_A"
                ),
                Fixture(
                    id = 3L,
                    season = 2026,
                    week = 5,
                    matchSlot = MatchSlot.MIDWEEK,
                    homeTeamId = 30L,
                    awayTeamId = 10L,
                    competitionType = "COPA"
                ),
                Fixture(
                    id = 4L,
                    season = 2026,
                    week = 6,
                    matchSlot = MatchSlot.WEEKEND,
                    homeTeamId = 10L,
                    awayTeamId = 20L,
                    competitionType = "SERIE_A"
                )
            )
        )

        val next = repository.getNextFixtureForTeamFlow(
            season = 2026,
            week = 3,
            teamId = 10L
        ).first()

        assertEquals(3L, next?.id)
        assertEquals(5, next?.week)
    }
}
