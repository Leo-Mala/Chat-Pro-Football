package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class UefaCompetitionSystemIntegrationTest {

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
    fun `Champions League progresses from 36 club league phase to one recorded champion`() = runTest {
        val season = 2027
        val teams = uefaTeams()
        repository.saveTeams(teams)

        repository.saveFixtures(
            UefaCompetitionSystem.generateLeaguePhase(
                season = season,
                teams = teams,
                competitionType = UefaCompetitionSystem.CHAMPIONS_LEAGUE
            )
        )
        assertEquals(144, repository.getFixturesForSeason(season).size)

        // Toda a fase de liga termina jogada; placares determinísticos produzem uma classificação
        // completa sem exigir qualquer regra especial do GameEngine.
        val leagueFixtures = repository.getFixturesForSeason(season)
        repository.updateFixtures(
            leagueFixtures.mapIndexed { index, fixture ->
                fixture.copy(
                    homeScore = if (index % 3 == 0) 2 else 1,
                    awayScore = if (index % 5 == 0) 1 else 0,
                    isPlayed = true
                )
            }
        )

        UefaCompetitionSystem.processProgression(
            season,
            UefaCompetitionSystem.CHAMPIONS_EUROPA_LEAGUE_WEEKS.last(),
            repository
        )
        assertRound(UefaCompetitionSystem.PLAYOFF_LEG_1_WEEK, 8)
        assertRound(UefaCompetitionSystem.PLAYOFF_LEG_2_WEEK, 8)

        playTwoLegRound(
            UefaCompetitionSystem.PLAYOFF_LEG_1_WEEK,
            UefaCompetitionSystem.PLAYOFF_LEG_2_WEEK
        )
        UefaCompetitionSystem.processProgression(
            season,
            UefaCompetitionSystem.PLAYOFF_LEG_2_WEEK,
            repository
        )
        assertRound(UefaCompetitionSystem.ROUND_OF_16_LEG_1_WEEK, 8)
        assertRound(UefaCompetitionSystem.ROUND_OF_16_LEG_2_WEEK, 8)

        playTwoLegRound(
            UefaCompetitionSystem.ROUND_OF_16_LEG_1_WEEK,
            UefaCompetitionSystem.ROUND_OF_16_LEG_2_WEEK
        )
        UefaCompetitionSystem.processProgression(
            season,
            UefaCompetitionSystem.ROUND_OF_16_LEG_2_WEEK,
            repository
        )
        assertRound(UefaCompetitionSystem.QUARTERFINAL_LEG_1_WEEK, 4)
        assertRound(UefaCompetitionSystem.QUARTERFINAL_LEG_2_WEEK, 4)

        playTwoLegRound(
            UefaCompetitionSystem.QUARTERFINAL_LEG_1_WEEK,
            UefaCompetitionSystem.QUARTERFINAL_LEG_2_WEEK
        )
        UefaCompetitionSystem.processProgression(
            season,
            UefaCompetitionSystem.QUARTERFINAL_LEG_2_WEEK,
            repository
        )
        assertRound(UefaCompetitionSystem.SEMIFINAL_LEG_1_WEEK, 2)
        assertRound(UefaCompetitionSystem.SEMIFINAL_LEG_2_WEEK, 2)

        playTwoLegRound(
            UefaCompetitionSystem.SEMIFINAL_LEG_1_WEEK,
            UefaCompetitionSystem.SEMIFINAL_LEG_2_WEEK
        )
        UefaCompetitionSystem.processProgression(
            season,
            UefaCompetitionSystem.SEMIFINAL_LEG_2_WEEK,
            repository
        )

        val final = repository.getFixturesForWeek(season, UefaCompetitionSystem.FINAL_WEEK)
            .single { it.competitionType == UefaCompetitionSystem.CHAMPIONS_LEAGUE }
        repository.updateFixture(
            final.copy(homeScore = 1, awayScore = 1, isPlayed = true)
        )

        UefaCompetitionSystem.processProgression(
            season,
            UefaCompetitionSystem.FINAL_WEEK,
            repository
        )

        val decidedFinal = repository.getFixturesForWeek(season, UefaCompetitionSystem.FINAL_WEEK)
            .single { it.competitionType == UefaCompetitionSystem.CHAMPIONS_LEAGUE }
        assertNotNull(decidedFinal.homePenalties)
        assertNotNull(decidedFinal.awayPenalties)
        assertTrue(decidedFinal.homePenalties != decidedFinal.awayPenalties)

        val records = repository.getAllHistoricalRecords().filter {
            it.season == season && it.competitionName == "UEFA Champions League"
        }
        assertEquals(1, records.size)
        assertTrue(records.single().championTeamName.isNotBlank())
        assertTrue(records.single().runnerUpTeamName.isNotBlank())

        // Reprocessar a final é idempotente: não duplica histórico.
        UefaCompetitionSystem.processProgression(
            season,
            UefaCompetitionSystem.FINAL_WEEK,
            repository
        )
        assertEquals(
            1,
            repository.getAllHistoricalRecords().count {
                it.season == season && it.competitionName == "UEFA Champions League"
            }
        )
        FixtureScheduleValidator.requireValid(repository.getFixturesForSeason(season))
    }

    private suspend fun playTwoLegRound(firstLegWeek: Int, secondLegWeek: Int) {
        val firstLegs = repository.getFixturesForWeek(2027, firstLegWeek)
            .filter { it.competitionType == UefaCompetitionSystem.CHAMPIONS_LEAGUE }
        val secondLegs = repository.getFixturesForWeek(2027, secondLegWeek)
            .filter { it.competitionType == UefaCompetitionSystem.CHAMPIONS_LEAGUE }
        assertEquals(firstLegs.size, secondLegs.size)
        assertTrue(firstLegs.isNotEmpty())

        repository.updateFixtures(firstLegs.map { it.copy(homeScore = 0, awayScore = 0, isPlayed = true) })
        repository.updateFixtures(secondLegs.map { it.copy(homeScore = 1, awayScore = 0, isPlayed = true) })
    }

    private suspend fun assertRound(week: Int, expectedMatches: Int) {
        val fixtures = repository.getFixturesForWeek(2027, week)
            .filter { it.competitionType == UefaCompetitionSystem.CHAMPIONS_LEAGUE }
        assertEquals(expectedMatches, fixtures.size)
        assertTrue(fixtures.all { it.matchSlot == MatchSlot.MIDWEEK })
    }

    private fun uefaTeams(): List<Team> {
        val countries = listOf(
            "Inglaterra", "Espanha", "Itália", "Alemanha", "França",
            "Portugal", "Países Baixos", "Bélgica", "Turquia", "Escócia",
            "Áustria", "Suíça", "Dinamarca", "Noruega", "Suécia",
            "Polônia", "Tchéquia", "Croácia", "Sérvia", "Grécia"
        )
        return (0 until 36).map { index ->
            val country = countries[index % countries.size]
            Team(
                id = 10_000L + index,
                name = "UEFA QA ${index + 1}",
                city = "Cidade ${index + 1}",
                state = country.take(2),
                country = country,
                division = 1,
                rating = 70 + (index % 25)
            )
        }
    }
}
