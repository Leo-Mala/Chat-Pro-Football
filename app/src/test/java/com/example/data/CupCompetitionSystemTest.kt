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
    fun `opening calendar creates disjoint national and continental fields`() {
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
        assertEquals(31, cup.single { it.homeTeamId == userTeam.id || it.awayTeamId == userTeam.id }.week)

        val tier1Groups = fixtures.filter { it.competitionType.startsWith("CONTINENTAL_T1_GP_") }
        val tier2Groups = fixtures.filter { it.competitionType.startsWith("CONTINENTAL_T2_GP_") }
        val tier3 = fixtures.filter { it.competitionType == "CONTINENTAL_T3" }

        assertEquals(48, tier1Groups.size)
        assertEquals(48, tier2Groups.size)
        assertEquals(8, tier3.size)

        val tier1Participants = participantCounts(tier1Groups)
        val tier2Participants = participantCounts(tier2Groups)
        val tier3Participants = participantCounts(tier3)

        assertEquals(32, tier1Participants.size)
        assertTrue(tier1Participants.values.all { it == 3 })
        assertEquals(32, tier2Participants.size)
        assertTrue(tier2Participants.values.all { it == 3 })
        assertEquals(16, tier3Participants.size)

        assertTrue(tier1Participants.keys.intersect(tier2Participants.keys).isEmpty())
        assertTrue(tier1Participants.keys.intersect(tier3Participants.keys).isEmpty())
        assertTrue(tier2Participants.keys.intersect(tier3Participants.keys).isEmpty())
        assertTrue(tier1Groups.all { it.week in 29..31 })
        assertTrue(tier2Groups.all { it.week in 29..31 })
        assertTrue(tier3.all { it.week == 33 })
    }

    @Test
    fun `continental qualifier uses wins before goal difference on equal points`() {
        // Team 3 dominates the synthetic table. Teams 1 and 2 both finish with 3 points,
        // but Team 1 has one win while Team 2 has only draws. Team 2 deliberately owns the
        // better goal difference so this test fails if the backend skips the UI's wins tiebreak.
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
    fun `tier three progresses idempotently to a recorded champion`() = runBlocking {
        val teams = conmebolUniverse()
        repository.saveTeams(teams)

        val opening = CupCompetitionSystem.generateSeasonOpeningFixtures(
            season = 2026,
            teams = teams,
            userTeamId = 1L,
            userCountry = "Brasil"
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
            week = 35,
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
}
