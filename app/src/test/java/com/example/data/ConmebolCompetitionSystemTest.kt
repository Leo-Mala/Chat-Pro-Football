package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConmebolCompetitionSystemTest {

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
    fun `group stage is deterministic eight groups of four with home and away round robin`() {
        val teams = rankedConmebolField(1L)

        val first = ConmebolCompetitionSystem.generateGroupStage(
            season = 2026,
            teams = teams,
            competitionType = ConmebolCompetitionSystem.LIBERTADORES
        )
        val second = ConmebolCompetitionSystem.generateGroupStage(
            season = 2026,
            teams = teams,
            competitionType = ConmebolCompetitionSystem.LIBERTADORES
        )

        assertEquals(first, second)
        assertEquals(96, first.size)
        assertEquals(8, first.groupBy { it.competitionType }.size)
        assertEquals(ConmebolCompetitionSystem.GROUP_WEEKS.toSet(), first.map { it.week }.toSet())
        assertTrue(first.all { it.matchSlot == MatchSlot.MIDWEEK })

        first.groupBy { it.competitionType }.values.forEach { groupFixtures ->
            assertEquals(12, groupFixtures.size)
            val participants = groupFixtures
                .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
                .distinct()
            assertEquals(4, participants.size)

            val appearances = groupFixtures
                .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
                .groupingBy { it }
                .eachCount()
            assertTrue(appearances.values.all { it == 6 })

            participants.forEach { teamId ->
                assertEquals(3, groupFixtures.count { it.homeTeamId == teamId })
                assertEquals(3, groupFixtures.count { it.awayTeamId == teamId })
            }

            participants.indices.forEach { i ->
                for (j in i + 1 until participants.size) {
                    val a = participants[i]
                    val b = participants[j]
                    assertEquals(
                        2,
                        groupFixtures.count {
                            (it.homeTeamId == a && it.awayTeamId == b) ||
                                (it.homeTeamId == b && it.awayTeamId == a)
                        }
                    )
                }
            }
        }

        FixtureScheduleValidator.requireValid(first)
    }

    @Test
    fun `four seeded pots contribute exactly one club to every group`() {
        val teams = rankedConmebolField(1L)
        val ranked = teams.sortedWith(compareByDescending<Team> { it.rating }.thenBy { it.id })
        val potByTeamId = ranked.chunked(8)
            .flatMapIndexed { potIndex, pot -> pot.map { it.id to potIndex } }
            .toMap()

        val groups = ConmebolCompetitionSystem.drawGroups(
            season = 2026,
            teams = teams,
            competitionType = ConmebolCompetitionSystem.LIBERTADORES
        )

        assertEquals(8, groups.size)
        groups.forEach { group ->
            assertEquals(4, group.size)
            assertEquals(setOf(0, 1, 2, 3), group.map { potByTeamId.getValue(it.id) }.toSet())
        }
    }

    @Test
    fun `aggregate legs do not decide an isolated draw by penalties`() {
        val fixture = Fixture(
            season = 2026,
            week = ConmebolCompetitionSystem.ROUND_OF_16_LEG_1_WEEK,
            matchSlot = MatchSlot.MIDWEEK,
            homeTeamId = 1L,
            awayTeamId = 2L,
            competitionType = ConmebolCompetitionSystem.LIBERTADORES,
            homeScore = 1,
            awayScore = 1,
            isPlayed = true
        )

        val decided = CompetitionRules.ensureKnockoutDecision(fixture)

        assertNull(decided.homePenalties)
        assertNull(decided.awayPenalties)
        assertTrue(ConmebolCompetitionSystem.isAggregateLeg(decided))
    }

    @Test
    fun `full Libertadores and Sudamericana progress to champions with canonical match totals`() = runBlocking {
        val libertadores = rankedConmebolField(1L)
        val sudamericana = rankedConmebolField(101L)
        repository.saveTeams(libertadores + sudamericana)

        val opening = ConmebolCompetitionSystem.generateOpeningFixtures(
            season = 2026,
            libertadoresTeams = libertadores,
            sudamericanaTeams = sudamericana
        )
        assertEquals(192, opening.size)
        repository.saveFixtures(opening)

        completeGroupStages()

        val afterGroups = repository.getFixturesForSeason(2026)
        val libThirds = groupPositions(afterGroups, ConmebolCompetitionSystem.LIBERTADORES, 2)
        val sudRunners = groupPositions(afterGroups, ConmebolCompetitionSystem.SUDAMERICANA, 1)

        CupCompetitionSystem.processProgression(
            2026,
            ConmebolCompetitionSystem.GROUP_WEEKS.last(),
            repository
        )
        CupCompetitionSystem.processProgression(
            2026,
            ConmebolCompetitionSystem.GROUP_WEEKS.last(),
            repository
        )

        val libR16Leg1 = round(
            ConmebolCompetitionSystem.ROUND_OF_16_LEG_1_WEEK,
            ConmebolCompetitionSystem.LIBERTADORES
        )
        val libR16Leg2 = round(
            ConmebolCompetitionSystem.ROUND_OF_16_LEG_2_WEEK,
            ConmebolCompetitionSystem.LIBERTADORES
        )
        val sudPlayoffLeg1 = round(
            ConmebolCompetitionSystem.SUD_PLAYOFF_LEG_1_WEEK,
            ConmebolCompetitionSystem.SUDAMERICANA
        )
        val sudPlayoffLeg2 = round(
            ConmebolCompetitionSystem.SUD_PLAYOFF_LEG_2_WEEK,
            ConmebolCompetitionSystem.SUDAMERICANA
        )

        assertEquals(8, libR16Leg1.size)
        assertEquals(8, libR16Leg2.size)
        assertEquals(8, sudPlayoffLeg1.size)
        assertEquals(8, sudPlayoffLeg2.size)
        assertEquals(libThirds, sudPlayoffLeg1.map { it.homeTeamId }.toSet())
        assertEquals(sudRunners, sudPlayoffLeg2.map { it.homeTeamId }.toSet())

        completeTwoLegRound(
            ConmebolCompetitionSystem.SUD_PLAYOFF_LEG_1_WEEK,
            ConmebolCompetitionSystem.SUD_PLAYOFF_LEG_2_WEEK,
            ConmebolCompetitionSystem.SUDAMERICANA
        )
        CupCompetitionSystem.processProgression(
            2026,
            ConmebolCompetitionSystem.SUD_PLAYOFF_LEG_2_WEEK,
            repository
        )

        assertEquals(
            8,
            round(
                ConmebolCompetitionSystem.ROUND_OF_16_LEG_1_WEEK,
                ConmebolCompetitionSystem.SUDAMERICANA
            ).size
        )
        assertEquals(
            8,
            round(
                ConmebolCompetitionSystem.ROUND_OF_16_LEG_2_WEEK,
                ConmebolCompetitionSystem.SUDAMERICANA
            ).size
        )

        completeTwoLegRound(
            ConmebolCompetitionSystem.ROUND_OF_16_LEG_1_WEEK,
            ConmebolCompetitionSystem.ROUND_OF_16_LEG_2_WEEK,
            ConmebolCompetitionSystem.LIBERTADORES
        )
        completeTwoLegRound(
            ConmebolCompetitionSystem.ROUND_OF_16_LEG_1_WEEK,
            ConmebolCompetitionSystem.ROUND_OF_16_LEG_2_WEEK,
            ConmebolCompetitionSystem.SUDAMERICANA
        )
        CupCompetitionSystem.processProgression(
            2026,
            ConmebolCompetitionSystem.ROUND_OF_16_LEG_2_WEEK,
            repository
        )

        assertRoundSizes(
            week1 = ConmebolCompetitionSystem.QUARTERFINAL_LEG_1_WEEK,
            week2 = ConmebolCompetitionSystem.QUARTERFINAL_LEG_2_WEEK,
            perLeg = 4
        )

        completeTwoLegRound(
            ConmebolCompetitionSystem.QUARTERFINAL_LEG_1_WEEK,
            ConmebolCompetitionSystem.QUARTERFINAL_LEG_2_WEEK,
            ConmebolCompetitionSystem.LIBERTADORES
        )
        completeTwoLegRound(
            ConmebolCompetitionSystem.QUARTERFINAL_LEG_1_WEEK,
            ConmebolCompetitionSystem.QUARTERFINAL_LEG_2_WEEK,
            ConmebolCompetitionSystem.SUDAMERICANA
        )
        CupCompetitionSystem.processProgression(
            2026,
            ConmebolCompetitionSystem.QUARTERFINAL_LEG_2_WEEK,
            repository
        )

        assertRoundSizes(
            week1 = ConmebolCompetitionSystem.SEMIFINAL_LEG_1_WEEK,
            week2 = ConmebolCompetitionSystem.SEMIFINAL_LEG_2_WEEK,
            perLeg = 2
        )

        completeTwoLegRound(
            ConmebolCompetitionSystem.SEMIFINAL_LEG_1_WEEK,
            ConmebolCompetitionSystem.SEMIFINAL_LEG_2_WEEK,
            ConmebolCompetitionSystem.LIBERTADORES
        )
        completeTwoLegRound(
            ConmebolCompetitionSystem.SEMIFINAL_LEG_1_WEEK,
            ConmebolCompetitionSystem.SEMIFINAL_LEG_2_WEEK,
            ConmebolCompetitionSystem.SUDAMERICANA
        )
        CupCompetitionSystem.processProgression(
            2026,
            ConmebolCompetitionSystem.SEMIFINAL_LEG_2_WEEK,
            repository
        )

        val libFinal = round(
            ConmebolCompetitionSystem.FINAL_WEEK,
            ConmebolCompetitionSystem.LIBERTADORES
        )
        val sudFinal = round(
            ConmebolCompetitionSystem.FINAL_WEEK,
            ConmebolCompetitionSystem.SUDAMERICANA
        )
        assertEquals(1, libFinal.size)
        assertEquals(1, sudFinal.size)

        completeFinals()
        CupCompetitionSystem.processProgression(
            2026,
            ConmebolCompetitionSystem.FINAL_WEEK,
            repository
        )
        CupCompetitionSystem.processProgression(
            2026,
            ConmebolCompetitionSystem.FINAL_WEEK,
            repository
        )

        val completed = repository.getFixturesForSeason(2026)
        val libTotal = completed.count {
            it.competitionType == ConmebolCompetitionSystem.LIBERTADORES ||
                it.competitionType.startsWith("${ConmebolCompetitionSystem.LIBERTADORES}_GP_")
        }
        val sudTotal = completed.count {
            it.competitionType == ConmebolCompetitionSystem.SUDAMERICANA ||
                it.competitionType.startsWith("${ConmebolCompetitionSystem.SUDAMERICANA}_GP_")
        }

        assertEquals(125, libTotal)
        assertEquals(141, sudTotal)
        assertEquals(266, libTotal + sudTotal)
        assertTrue(completed.all { it.week in 1..GameCalendar.WEEKS_PER_SEASON })
        FixtureScheduleValidator.requireValid(completed)

        val records = repository.getAllHistoricalRecords()
        assertEquals(2, records.size)
        assertEquals(2, records.count { it.season == 2026 })
        assertTrue(records.all { it.championTeamName.isNotBlank() })
        assertTrue(records.all { it.runnerUpTeamName.isNotBlank() })
    }

    private suspend fun completeGroupStages() {
        val fixtures = repository.getFixturesForSeason(2026).filter {
            it.competitionType.contains("_GP_")
        }
        repository.updateFixtures(
            fixtures.map { fixture ->
                fixture.copy(homeScore = 1, awayScore = 0, isPlayed = true)
            }
        )
    }

    private suspend fun completeTwoLegRound(firstWeek: Int, secondWeek: Int, competitionType: String) {
        val firstLegs = round(firstWeek, competitionType)
        val secondLegs = round(secondWeek, competitionType)
        assertTrue(firstLegs.isNotEmpty())
        assertEquals(firstLegs.size, secondLegs.size)

        repository.updateFixtures(
            firstLegs.map { it.copy(homeScore = 1, awayScore = 0, isPlayed = true) } +
                secondLegs.map { it.copy(homeScore = 2, awayScore = 0, isPlayed = true) }
        )
    }

    private suspend fun completeFinals() {
        val finals = repository.getFixturesForWeek(2026, ConmebolCompetitionSystem.FINAL_WEEK)
            .filter {
                it.competitionType == ConmebolCompetitionSystem.LIBERTADORES ||
                    it.competitionType == ConmebolCompetitionSystem.SUDAMERICANA
            }
        assertEquals(2, finals.size)
        repository.updateFixtures(
            finals.map { it.copy(homeScore = 2, awayScore = 1, isPlayed = true) }
        )
    }

    private suspend fun round(week: Int, competitionType: String): List<Fixture> =
        repository.getFixturesForWeek(2026, week).filter { it.competitionType == competitionType }

    private fun groupPositions(fixtures: List<Fixture>, competitionType: String, index: Int): Set<Long> =
        fixtures
            .filter { it.competitionType.startsWith("${competitionType}_GP_") }
            .groupBy { it.competitionType }
            .toSortedMap()
            .values
            .map { ConmebolCompetitionSystem.calculateGroupRanking(it)[index] }
            .toSet()

    private suspend fun assertRoundSizes(week1: Int, week2: Int, perLeg: Int) {
        for (competitionType in listOf(
            ConmebolCompetitionSystem.LIBERTADORES,
            ConmebolCompetitionSystem.SUDAMERICANA
        )) {
            assertEquals(perLeg, round(week1, competitionType).size)
            assertEquals(perLeg, round(week2, competitionType).size)
        }
    }

    private fun rankedConmebolField(firstId: Long): List<Team> {
        val countries = listOf(
            "Brasil",
            "Argentina",
            "Bolívia",
            "Chile",
            "Colômbia",
            "Equador",
            "Paraguai",
            "Uruguai"
        )
        return (0 until 32).map { offset ->
            val id = firstId + offset
            Team(
                id = id,
                name = "CONMEBOL Clube $id",
                city = "Cidade $id",
                state = "SA",
                country = countries[offset % countries.size],
                division = 1,
                rating = 100 - offset
            )
        }
    }
}
