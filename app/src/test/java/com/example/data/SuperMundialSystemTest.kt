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
    fun everyClubPlaysExactlyThreeGroupMatches() {
        val season = 2026
        val userTeam = Team(
            id = 999L,
            name = "Clube Teste",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 80
        )

        val fixtures = SuperMundialSystem.generateGroupStageFixtures(
            season = season,
            allTeams = listOf(userTeam),
            userTeamId = userTeam.id
        )

        assertEquals("8 grupos x 6 partidas = 48 jogos na fase de grupos.", 48, fixtures.size)

        val appearancesByTeam = mutableMapOf<Long, Int>()
        fixtures.forEach { fixture ->
            appearancesByTeam[fixture.homeTeamId] = appearancesByTeam.getOrDefault(fixture.homeTeamId, 0) + 1
            appearancesByTeam[fixture.awayTeamId] = appearancesByTeam.getOrDefault(fixture.awayTeamId, 0) + 1
        }

        assertEquals("O torneio deve ter exatamente 32 clubes.", 32, appearancesByTeam.size)
        assertTrue(
            "Cada clube deve disputar exatamente 3 partidas na fase de grupos.",
            appearancesByTeam.values.all { it == 3 }
        )
    }

    @Test
    fun finalistPathContainsExactlySevenMatches() = runTest {
        val season = 2026
        val trackedTeam = Team(
            id = 999L,
            name = "Finalista Teste",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 99
        )
        repository.saveTeams(listOf(trackedTeam))

        val groupFixtures = SuperMundialSystem.generateGroupStageFixtures(
            season = season,
            allTeams = listOf(trackedTeam),
            userTeamId = trackedTeam.id
        )
        repository.saveFixtures(groupFixtures)

        // O clube acompanhado vence seus 3 jogos; os demais jogos terminam empatados.
        val playedGroupFixtures = repository.getFixturesForSeason(season)
            .filter { it.competitionType.startsWith("WORLD_CUP_GP_") }
            .map { fixture ->
                when (trackedTeam.id) {
                    fixture.homeTeamId -> fixture.copy(homeScore = 2, awayScore = 0, isPlayed = true)
                    fixture.awayTeamId -> fixture.copy(homeScore = 0, awayScore = 2, isPlayed = true)
                    else -> fixture.copy(homeScore = 0, awayScore = 0, isPlayed = true)
                }
            }
        repository.updateFixtures(playedGroupFixtures)

        assertEquals(
            "O clube deve sair da fase de grupos com exatamente 3 partidas disputadas.",
            3,
            playedGroupFixtures.count { it.homeTeamId == trackedTeam.id || it.awayTeamId == trackedTeam.id }
        )

        SuperMundialSystem.processProgression(season, 36, repository)

        for (week in 37..GameCalendar.WEEKS_PER_SEASON) {
            val roundFixtures = repository.getFixturesForWeek(season, week)
                .filter { it.competitionType == "WORLD_CUP" }

            assertTrue("A fase eliminatória da semana $week deve existir.", roundFixtures.isNotEmpty())
            assertEquals(
                "O finalista deve disputar exatamente uma partida na semana $week.",
                1,
                roundFixtures.count { it.homeTeamId == trackedTeam.id || it.awayTeamId == trackedTeam.id }
            )

            repository.updateFixtures(
                roundFixtures.map { fixture ->
                    when (trackedTeam.id) {
                        fixture.homeTeamId -> fixture.copy(homeScore = 1, awayScore = 0, isPlayed = true)
                        fixture.awayTeamId -> fixture.copy(homeScore = 0, awayScore = 1, isPlayed = true)
                        else -> fixture.copy(homeScore = 1, awayScore = 0, isPlayed = true)
                    }
                }
            )

            SuperMundialSystem.processProgression(season, week, repository)
        }

        val allWorldCupFixtures = repository.getFixturesForSeason(season)
            .filter {
                it.competitionType.startsWith("WORLD_CUP_GP_") || it.competitionType == "WORLD_CUP"
            }
        val trackedTeamMatches = allWorldCupFixtures.filter {
            it.homeTeamId == trackedTeam.id || it.awayTeamId == trackedTeam.id
        }

        assertEquals(
            "Caminho completo do finalista: 3 grupos + oitavas + quartas + semifinal + final = 7 jogos.",
            7,
            trackedTeamMatches.size
        )
        assertEquals(3, trackedTeamMatches.count { it.competitionType.startsWith("WORLD_CUP_GP_") })
        assertEquals(1, trackedTeamMatches.count { it.week == 37 && it.competitionType == "WORLD_CUP" })
        assertEquals(1, trackedTeamMatches.count { it.week == 38 && it.competitionType == "WORLD_CUP" })
        assertEquals(1, trackedTeamMatches.count { it.week == 39 && it.competitionType == "WORLD_CUP" })
        assertEquals(1, trackedTeamMatches.count { it.week == 40 && it.competitionType == "WORLD_CUP" })
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
