package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CupCompetitionSystemTest {

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
    fun `aggregate game regions resolve to the correct confederation`() {
        assertEquals("CONCACAF", GlobalFootballSystem.getConfederationForCountry("Estados Unidos / México"))
        assertEquals("CONCACAF", GlobalFootballSystem.getConfederationForCountry("América Central"))
        assertEquals("CAF", GlobalFootballSystem.getConfederationForCountry("África"))
        assertEquals("AFC", GlobalFootballSystem.getConfederationForCountry("Ásia"))
        assertEquals("OFC", GlobalFootballSystem.getConfederationForCountry("Oceania"))
        assertEquals("MIXED", GlobalFootballSystem.getConfederationForCountry("África / Ásia / Oceania"))
        assertEquals("CONMEBOL", GlobalFootballSystem.getConfederationForCountry("Brasil"))
        assertEquals("UEFA", GlobalFootballSystem.getConfederationForCountry("Inglaterra"))
    }

    @Test
    fun `opening calendar creates disjoint CONMEBOL tier one and tier two fields`() {
        val teams = conmebolUniverse()
        val userTeam = teams.first { it.id == 1L }

        val fixtures = CupCompetitionSystem.generateSeasonOpeningFixtures(
            season = 2026,
            teams = teams,
            userTeamId = userTeam.id,
            userCountry = userTeam.country
        )

        val cup = fixtures.filter { it.competitionType == "COPA" }
        assertEquals(16, cup.size)
        assertEquals(23, cup.single { it.homeTeamId == userTeam.id || it.awayTeamId == userTeam.id }.week)
        assertTrue(cup.all { it.matchSlot == MatchSlot.MIDWEEK })

        val tier1Groups = fixtures.filter { it.competitionType.startsWith("CONTINENTAL_T1_GP_") }
        val tier2Groups = fixtures.filter { it.competitionType.startsWith("CONTINENTAL_T2_GP_") }
        val tier3 = fixtures.filter { it.competitionType == "CONTINENTAL_T3" }

        assertEquals(48, tier1Groups.size)
        assertEquals(48, tier2Groups.size)
        assertTrue(tier3.isEmpty())

        val tier1Participants = participantCounts(tier1Groups)
        val tier2Participants = participantCounts(tier2Groups)

        assertEquals(32, tier1Participants.size)
        assertTrue(tier1Participants.values.all { it == 3 })
        assertEquals(32, tier2Participants.size)
        assertTrue(tier2Participants.values.all { it == 3 })
        assertTrue(tier1Participants.keys.intersect(tier2Participants.keys).isEmpty())
        assertTrue(tier1Groups.all { it.week in 29..31 && it.matchSlot == MatchSlot.MIDWEEK })
        assertTrue(tier2Groups.all { it.week in 29..31 && it.matchSlot == MatchSlot.MIDWEEK })
        FixtureScheduleValidator.requireValid(fixtures)
    }

    @Test
    fun `CONMEBOL field selector uses quota policy and disables tier three`() {
        val candidates = conmebolUniverse()
            .sortedWith(
                compareBy<Team> { it.division }
                    .thenByDescending { it.rating }
                    .thenBy { it.id }
            )

        val fields = CupCompetitionSystem.selectContinentalFields(candidates, "CONMEBOL")

        assertEquals(32, fields.tier1.size)
        assertEquals(32, fields.tier2.size)
        assertTrue(fields.tier3.isEmpty())
        assertEquals(64, fields.allTeamIds.size)
        assertTrue(fields.tier1.map { it.id }.toSet().intersect(fields.tier2.map { it.id }.toSet()).isEmpty())
    }

    @Test
    fun `continental qualifier uses wins before goal difference on equal points`() {
        val fixtures = listOf(
            played(home = 3L, away = 1L, homeGoals = 5, awayGoals = 0),
            played(home = 3L, away = 2L, homeGoals = 1, awayGoals = 0),
            played(home = 3L, away = 4L, homeGoals = 1, awayGoals = 0),
            played(home = 1L, away = 4L, homeGoals = 1, awayGoals = 0),
            played(home = 3L, away = 1L, homeGoals = 5, awayGoals = 0),
            played(home = 2L, away = 4L, homeGoals = 0, awayGoals = 0),
            played(home = 2L, away = 4L, homeGoals = 1, awayGoals = 1),
            played(home = 2L, away = 4L, homeGoals = 2, awayGoals = 2)
        )

        val qualifiers = CupCompetitionSystem.calculateGroupQualifiers(fixtures)
        assertEquals(listOf(3L, 1L), qualifiers)
    }

    @Test
    fun `legacy tier three still progresses idempotently outside explicit quota policies`() = runBlocking {
        val teams = legacyUefaUniverse()
        repository.saveTeams(teams)

        val opening = CupCompetitionSystem.generateSeasonOpeningFixtures(
            season = 2026,
            teams = teams,
            userTeamId = 1L,
            userCountry = "Inglaterra"
        )
        repository.saveFixtures(opening)

        completeRound(week = 33, competitionType = "CONTINENTAL_T3", tied = false)
        CupCompetitionSystem.processProgression(2026, 33, repository)
        CupCompetitionSystem.processProgression(2026, 33, repository)
        assertEquals(4, round(34, "CONTINENTAL_T3").size)

        completeRound(week = 34, competitionType = "CONTINENTAL_T3", tied = false)
        CupCompetitionSystem.processProgression(2026, 34, repository)
        CupCompetitionSystem.processProgression(2026, 34, repository)
        assertEquals(2, round(35, "CONTINENTAL_T3").size)

        completeRound(week = 35, competitionType = "CONTINENTAL_T3", tied = false)
        CupCompetitionSystem.processProgression(2026, 35, repository)
        CupCompetitionSystem.processProgression(2026, 35, repository)
        assertEquals(1, round(36, "CONTINENTAL_T3").size)

        completeRound(week = 36, competitionType = "CONTINENTAL_T3", tied = true)
        CupCompetitionSystem.processProgression(2026, 36, repository)
        CupCompetitionSystem.processProgression(2026, 36, repository)

        val finalFixture = round(36, "CONTINENTAL_T3").single()
        assertNotNull(finalFixture.homePenalties)
        assertNotNull(finalFixture.awayPenalties)
        assertFalse(finalFixture.homePenalties == finalFixture.awayPenalties)
        assertEquals(MatchSlot.MIDWEEK, finalFixture.matchSlot)

        val records = repository.getAllHistoricalRecords()
        assertEquals(1, records.size)
        assertEquals(2026, records.single().season)
        assertTrue(records.single().championTeamName.isNotBlank())
        assertTrue(records.single().runnerUpTeamName.isNotBlank())
    }

    @Test
    fun `knockout tie resolution is deterministic`() {
        val fixture = Fixture(
            id = 77L,
            season = 2026,
            week = 25,
            matchSlot = MatchSlot.MIDWEEK,
            homeTeamId = 10L,
            awayTeamId = 20L,
            competitionType = "COPA",
            homeScore = 1,
            awayScore = 1,
            isPlayed = true
        )

        val first = CompetitionRules.ensureKnockoutDecision(fixture)
        val second = CompetitionRules.ensureKnockoutDecision(fixture)

        assertEquals(first.homePenalties, second.homePenalties)
        assertEquals(first.awayPenalties, second.awayPenalties)
        assertEquals(CompetitionRules.winnerOf(first), CompetitionRules.winnerOf(second))
        assertNotNull(CompetitionRules.winnerOf(first))
    }

    private fun played(
        home: Long,
        away: Long,
        homeGoals: Int,
        awayGoals: Int
    ): Fixture = Fixture(
        season = 2026,
        week = 31,
        matchSlot = MatchSlot.MIDWEEK,
        homeTeamId = home,
        awayTeamId = away,
        homeScore = homeGoals,
        awayScore = awayGoals,
        competitionType = "CONTINENTAL_T1_GP_A",
        isPlayed = true
    )

    private suspend fun completeRound(
        week: Int,
        competitionType: String,
        tied: Boolean
    ) {
        val fixtures = round(week, competitionType)
        assertTrue("Expected fixtures for $competitionType in week $week", fixtures.isNotEmpty())
        repository.updateFixtures(
            fixtures.mapIndexed { index, fixture ->
                fixture.copy(
                    homeScore = if (tied) 1 else 2 + (index % 2),
                    awayScore = if (tied) 1 else 0,
                    homePenalties = null,
                    awayPenalties = null,
                    isPlayed = true
                )
            }
        )
    }

    private suspend fun round(week: Int, competitionType: String): List<Fixture> =
        repository.getFixturesForWeek(2026, week).filter { it.competitionType == competitionType }

    private fun participantCounts(fixtures: List<Fixture>): Map<Long, Int> =
        fixtures.flatMap { listOf(it.homeTeamId, it.awayTeamId) }
            .groupingBy { it }
            .eachCount()

    private fun conmebolUniverse(): List<Team> {
        val brazil = (1L..40L).map { id ->
            Team(
                id = id,
                name = "Brasil Clube $id",
                city = "Cidade $id",
                state = "BR",
                country = "Brasil",
                division = if (id <= 20L) 1 else 2,
                rating = if (id == 1L) 99 else 95 - (id % 30).toInt()
            )
        }
        val argentina = (41L..80L).map { id ->
            Team(
                id = id,
                name = "Argentina Clube $id",
                city = "Cidade $id",
                state = "AR",
                country = "Argentina",
                division = if (id <= 60L) 1 else 2,
                rating = 94 - (id % 30).toInt()
            )
        }
        return brazil + argentina
    }

    private fun legacyUefaUniverse(): List<Team> = (1L..80L).map { id ->
        Team(
            id = id,
            name = "Inglaterra Clube $id",
            city = "Cidade $id",
            state = "ENG",
            country = "Inglaterra",
            division = 1,
            rating = 100 - (id % 50).toInt()
        )
    }
}
