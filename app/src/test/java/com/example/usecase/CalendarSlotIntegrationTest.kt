package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.FixtureScheduleValidator
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.MatchSlot
import com.example.data.Team
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarSlotIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var useCase: GenerateCalendarUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        useCase = GenerateCalendarUseCase(repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun freshTwentyClubBrazilianSeasonUsesBothSlotsWithoutCreatingReducedLibertadores() {
        val teams = brazilianUniverse(20)
        val fixtures = useCase.generateSeasonFixtures(
            season = 2026,
            teams = teams,
            userTeamId = teams.first().id,
            userCountry = "Brasil"
        )

        FixtureScheduleValidator.requireValid(fixtures)
        assertTrue(fixtures.all { it.week in 1..GameCalendar.WEEKS_PER_SEASON })
        assertTrue(fixtures.filter { it.competitionType == "SERIE_A" }.all { it.matchSlot == MatchSlot.WEEKEND })
        assertTrue(fixtures.filter { it.competitionType == "COPA" }.all { it.matchSlot == MatchSlot.MIDWEEK })
        assertFalse(fixtures.any { it.competitionType.startsWith("CONTINENTAL_") })

        val week24 = fixtures.filter { it.week == 24 }
        assertTrue(week24.any { it.matchSlot == MatchSlot.MIDWEEK })
        assertTrue(week24.any { it.matchSlot == MatchSlot.WEEKEND })

        val clubsWithBothSlots = teams.map { it.id }.filter { teamId ->
            week24.any { it.matchSlot == MatchSlot.MIDWEEK && (it.homeTeamId == teamId || it.awayTeamId == teamId) } &&
                week24.any { it.matchSlot == MatchSlot.WEEKEND && (it.homeTeamId == teamId || it.awayTeamId == teamId) }
        }
        assertTrue("Ao menos um clube deve usar MIDWEEK + WEEKEND na mesma semana", clubsWithBothSlots.isNotEmpty())
    }

    @Test
    fun calendarGenerationIsDeterministicIncludingSlots() {
        val teams = brazilianUniverse(20)

        val first = useCase.generateSeasonFixtures(2026, teams, teams.first().id, "Brasil")
        val second = useCase.generateSeasonFixtures(2026, teams, teams.first().id, "Brasil")

        fun signature(fixtures: List<com.example.data.Fixture>) = fixtures.map {
            listOf(
                it.season.toString(),
                it.week.toString(),
                it.matchSlot.name,
                it.homeTeamId.toString(),
                it.awayTeamId.toString(),
                it.competitionType
            )
        }

        assertEquals(signature(first), signature(second))
    }

    private fun brazilianUniverse(size: Int): List<Team> = (1L..size.toLong()).map { id ->
        Team(
            id = id,
            name = "Brasil Clube $id",
            city = "Cidade $id",
            state = "BR",
            country = "Brasil",
            division = 1,
            rating = 100 - id.toInt()
        )
    }
}
