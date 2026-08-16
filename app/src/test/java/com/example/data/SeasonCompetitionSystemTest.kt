package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class SeasonCompetitionSystemTest {

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
    fun initialCalendar_createsCupAndThreeContinentalTiersWithGenericUiCodes() {
        val england = (1L..40L).map { id ->
            team(
                id = id,
                country = "Inglaterra",
                division = if (id <= 20L) 1 else 2,
                rating = (100 - id.toInt()).coerceAtLeast(50)
            )
        }
        val continentalPool = listOf("Espanha", "Itália", "Alemanha", "França")
            .flatMapIndexed { countryIndex, country ->
                (1L..20L).map { localId ->
                    val id = 100L + countryIndex * 20L + localId
                    team(
                        id = id,
                        country = country,
                        division = 1,
                        rating = (95 - countryIndex * 4 - localId.toInt() / 3).coerceAtLeast(55)
                    )
                }
            }
        val teams = england + continentalPool
        val userTeamId = 40L // clube de divisão inferior deve continuar incluído na Copa nacional

        val fixtures = SeasonCompetitionSystem.generateInitialFixtures(
            season = 2026,
            teams = teams,
            userTeamId = userTeamId,
            userCountry = "Inglaterra"
        )

        val cupOpening = fixtures.filter { it.competitionType == SeasonCompetitionSystem.DOMESTIC_CUP }
        assertEquals(16, cupOpening.size)
        assertTrue(cupOpening.all { it.week == 31 })
        assertTrue(cupOpening.any { it.homeTeamId == userTeamId || it.awayTeamId == userTeamId })

        val tier1Groups = fixtures.filter { it.competitionType.startsWith("CONTINENTAL_T1_GP_") }
        val tier2Groups = fixtures.filter { it.competitionType.startsWith("CONTINENTAL_T2_GP_") }
        val tier3Opening = fixtures.filter { it.competitionType == SeasonCompetitionSystem.CONTINENTAL_T3 }

        assertEquals(48, tier1Groups.size)
        assertEquals(48, tier2Groups.size)
        assertEquals(8, tier3Opening.size)
        assertEquals(setOf(28, 29, 30), tier1Groups.map { it.week }.toSet())
        assertEquals(setOf(28, 29, 30), tier2Groups.map { it.week }.toSet())
        assertTrue(tier3Opening.all { it.week == 32 })

        val tier1Teams = tier1Groups.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
        val tier2Teams = tier2Groups.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
        val tier3Teams = tier3Opening.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet()
        assertEquals(32, tier1Teams.size)
        assertEquals(32, tier2Teams.size)
        assertEquals(16, tier3Teams.size)
        assertTrue(tier1Teams.intersect(tier2Teams).isEmpty())
        assertTrue(tier1Teams.intersect(tier3Teams).isEmpty())
        assertTrue(tier2Teams.intersect(tier3Teams).isEmpty())
    }

    @Test
    fun continentalSelection_isConfederationScopedAndDoesNotFallbackUnknownCountry() {
        val uefa = (1L..40L).map { id -> team(id, "Inglaterra", 1, 80) }
        val conmebol = (101L..140L).map { id -> team(id, "Brasil", 1, 99) }
        val teams = uefa + conmebol

        val selected = SeasonCompetitionSystem.selectContinentalTierParticipants(
            teams = teams,
            userCountry = "Inglaterra",
            tier = 1
        )

        assertEquals(32, selected.size)
        assertTrue(selected.all { it.country == "Inglaterra" })
        assertTrue(
            SeasonCompetitionSystem.selectContinentalTierParticipants(
                teams = teams,
                userCountry = "País Inexistente",
                tier = 1
            ).isEmpty()
        )
    }

    @Test
    fun domesticCup_progressesToWeek35AndRecordsChampionWithShootoutWhenNeeded() = runTest {
        val teams = (1L..32L).map { id ->
            team(id = id, country = "Brasil", division = 1, rating = 90 - (id % 20).toInt())
        }
        repository.saveTeams(teams)
        repository.saveFixtures(
            SeasonCompetitionSystem.generateDomesticCupOpeningFixtures(
                season = 2026,
                teams = teams,
                userTeamId = 1L,
                userCountry = "Brasil"
            )
        )

        for (week in 31..34) {
            val round = repository.getFixturesForWeek(2026, week)
                .filter { it.competitionType == SeasonCompetitionSystem.DOMESTIC_CUP }
            assertTrue("Expected cup round in week $week", round.isNotEmpty())
            repository.updateFixtures(
                round.map { it.copy(homeScore = 1, awayScore = 0, isPlayed = true) }
            )
            SeasonCompetitionSystem.processProgression(2026, week, repository)
        }

        val final = repository.getFixturesForWeek(2026, 35)
            .single { it.competitionType == SeasonCompetitionSystem.DOMESTIC_CUP }
        repository.updateFixture(final.copy(homeScore = 0, awayScore = 0, isPlayed = true))

        SeasonCompetitionSystem.processProgression(2026, 35, repository)

        val persistedFinal = repository.getFixturesForWeek(2026, 35)
            .single { it.competitionType == SeasonCompetitionSystem.DOMESTIC_CUP }
        assertNotNull(persistedFinal.homePenalties)
        assertNotNull(persistedFinal.awayPenalties)
        assertFalse(persistedFinal.homePenalties == persistedFinal.awayPenalties)

        val records = repository.getAllHistoricalRecords()
        assertEquals(1, records.size)
        assertEquals(2026, records.single().season)
        assertTrue(records.single().competitionName.contains("Copa"))
    }

    @Test
    fun completedContinentalGroups_createEightRoundOf16Fixtures() = runTest {
        val participants = listOf("Inglaterra", "Espanha")
            .flatMapIndexed { countryIndex, country ->
                (1L..16L).map { localId ->
                    team(
                        id = countryIndex * 100L + localId,
                        country = country,
                        division = 1,
                        rating = 90 - localId.toInt()
                    )
                }
            }
        repository.saveTeams(participants)
        val groupFixtures = SeasonCompetitionSystem.generateContinentalGroupStageFixtures(
            season = 2026,
            participants = participants,
            competitionType = SeasonCompetitionSystem.CONTINENTAL_T1
        )
        repository.saveFixtures(groupFixtures)
        repository.updateFixtures(
            repository.getFixturesForSeason(2026).map { fixture ->
                fixture.copy(homeScore = 1, awayScore = 0, isPlayed = true)
            }
        )

        SeasonCompetitionSystem.processProgression(2026, 30, repository)

        val roundOf16 = repository.getFixturesForWeek(2026, 32)
            .filter { it.competitionType == SeasonCompetitionSystem.CONTINENTAL_T1 }
        assertEquals(8, roundOf16.size)
        assertEquals(16, roundOf16.flatMap { listOf(it.homeTeamId, it.awayTeamId) }.toSet().size)
    }

    private fun team(id: Long, country: String, division: Int, rating: Int): Team {
        return Team(
            id = id,
            name = "$country Clube $id",
            city = "Cidade $id",
            state = "ST",
            country = country,
            division = division,
            rating = rating,
            stadiumName = "Estádio $id"
        )
    }
}
