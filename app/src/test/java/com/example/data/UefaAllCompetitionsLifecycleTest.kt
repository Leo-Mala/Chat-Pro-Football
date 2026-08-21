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
class UefaAllCompetitionsLifecycleTest {
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
    fun tearDown() = db.close()

    @Test
    fun `Champions completes league playoff knockout and records one champion`() = runTest {
        assertFullLifecycle(UefaCompetitionSystem.CHAMPIONS_LEAGUE, 2027, 144, "UEFA Champions League")
    }

    @Test
    fun `Europa completes league playoff knockout and records one champion`() = runTest {
        assertFullLifecycle(UefaCompetitionSystem.EUROPA_LEAGUE, 2028, 144, "UEFA Europa League")
    }

    @Test
    fun `Conference completes six match league playoff knockout and records one champion`() = runTest {
        assertFullLifecycle(UefaCompetitionSystem.CONFERENCE_LEAGUE, 2029, 108, "UEFA Conference League")
    }

    private suspend fun assertFullLifecycle(
        competitionType: String,
        season: Int,
        expectedLeagueFixtures: Int,
        expectedCompetitionName: String
    ) {
        val teams = uefaTeams(season)
        repository.saveTeams(teams)
        val league = UefaCompetitionSystem.generateLeaguePhase(season, teams, competitionType)
        assertEquals(expectedLeagueFixtures, league.size)
        repository.saveFixtures(league)
        repository.updateFixtures(
            repository.getFixturesForSeason(season).mapIndexed { index, fixture ->
                fixture.copy(
                    homeScore = if (index % 4 == 0) 2 else 1,
                    awayScore = if (index % 7 == 0) 1 else 0,
                    isPlayed = true
                )
            }
        )

        val lastLeagueWeek = if (competitionType == UefaCompetitionSystem.CONFERENCE_LEAGUE) {
            UefaCompetitionSystem.CONFERENCE_LEAGUE_WEEKS.last()
        } else {
            UefaCompetitionSystem.CHAMPIONS_EUROPA_LEAGUE_WEEKS.last()
        }
        UefaCompetitionSystem.processProgression(season, lastLeagueWeek, repository)
        playTwoLegRound(season, competitionType, UefaCompetitionSystem.PLAYOFF_LEG_1_WEEK, UefaCompetitionSystem.PLAYOFF_LEG_2_WEEK, 8)
        UefaCompetitionSystem.processProgression(season, UefaCompetitionSystem.PLAYOFF_LEG_2_WEEK, repository)
        playTwoLegRound(season, competitionType, UefaCompetitionSystem.ROUND_OF_16_LEG_1_WEEK, UefaCompetitionSystem.ROUND_OF_16_LEG_2_WEEK, 8)
        UefaCompetitionSystem.processProgression(season, UefaCompetitionSystem.ROUND_OF_16_LEG_2_WEEK, repository)
        playTwoLegRound(season, competitionType, UefaCompetitionSystem.QUARTERFINAL_LEG_1_WEEK, UefaCompetitionSystem.QUARTERFINAL_LEG_2_WEEK, 4)
        UefaCompetitionSystem.processProgression(season, UefaCompetitionSystem.QUARTERFINAL_LEG_2_WEEK, repository)
        playTwoLegRound(season, competitionType, UefaCompetitionSystem.SEMIFINAL_LEG_1_WEEK, UefaCompetitionSystem.SEMIFINAL_LEG_2_WEEK, 2)
        UefaCompetitionSystem.processProgression(season, UefaCompetitionSystem.SEMIFINAL_LEG_2_WEEK, repository)

        val final = repository.getFixturesForWeek(season, UefaCompetitionSystem.FINAL_WEEK)
            .single { it.competitionType == competitionType }
        repository.updateFixture(final.copy(homeScore = 1, awayScore = 1, isPlayed = true))
        UefaCompetitionSystem.processProgression(season, UefaCompetitionSystem.FINAL_WEEK, repository)

        val records = repository.getAllHistoricalRecords().filter {
            it.season == season && it.competitionName == expectedCompetitionName
        }
        assertEquals(1, records.size)
        assertTrue(records.single().championTeamName.isNotBlank())
        assertTrue(records.single().runnerUpTeamName.isNotBlank())

        UefaCompetitionSystem.processProgression(season, UefaCompetitionSystem.FINAL_WEEK, repository)
        assertEquals(
            1,
            repository.getAllHistoricalRecords().count {
                it.season == season && it.competitionName == expectedCompetitionName
            }
        )
        FixtureScheduleValidator.requireValid(repository.getFixturesForSeason(season))
    }

    private suspend fun playTwoLegRound(
        season: Int,
        competitionType: String,
        firstWeek: Int,
        secondWeek: Int,
        expectedMatchesPerLeg: Int
    ) {
        val first = repository.getFixturesForWeek(season, firstWeek).filter { it.competitionType == competitionType }
        val second = repository.getFixturesForWeek(season, secondWeek).filter { it.competitionType == competitionType }
        assertEquals(expectedMatchesPerLeg, first.size)
        assertEquals(expectedMatchesPerLeg, second.size)
        repository.updateFixtures(first.map { it.copy(homeScore = 0, awayScore = 0, isPlayed = true) })
        repository.updateFixtures(second.map { it.copy(homeScore = 1, awayScore = 0, isPlayed = true) })
    }

    private fun uefaTeams(season: Int): List<Team> {
        val countries = listOf(
            "Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal",
            "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça",
            "Dinamarca", "Noruega", "Suécia", "Polônia", "Tchéquia", "Croácia", "Sérvia", "Grécia"
        )
        return (0 until 36).map { index ->
            val country = countries[index % countries.size]
            Team(
                id = season.toLong() * 100L + index,
                name = "UEFA lifecycle $season ${index + 1}",
                city = "Cidade",
                state = country.take(2),
                country = country,
                division = 1,
                rating = 70 + (index % 25)
            )
        }
    }
}
