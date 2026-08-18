package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameCalendar
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Team
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorldFootballRulesHardeningTransitionTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository
    private lateinit var transition: SeasonTransitionUseCase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = GameRepository(db)
        transition = SeasonTransitionUseCase(
            repository = repository,
            generateCalendarUseCase = GenerateCalendarUseCase(repository),
            databaseIntegrityUseCase = DatabaseIntegrityUseCase(repository)
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `Mundial clubs remain persisted but never enter domestic standings or movement`() = runBlocking {
        val brazil = listOf(
            team(1L, "Brasil User", "Brasil", 1, 90),
            team(2L, "Brasil CPU", "Brasil", 1, 80)
        )
        val mundialUpper = (100L..103L).mapIndexed { index, id ->
            team(id, "Mundial A ${index + 1}", "Mundial", 1, 90 - index)
        }
        val mundialLower = (200L..203L).mapIndexed { index, id ->
            team(id, "Mundial B ${index + 1}", "Mundial", 2, 70 - index)
        }
        val unknownUpper = (300L..303L).mapIndexed { index, id ->
            team(id, "Unknown A ${index + 1}", "País Inexistente", 1, 80 - index)
        }
        val unknownLower = (400L..403L).mapIndexed { index, id ->
            team(id, "Unknown B ${index + 1}", "País Inexistente", 2, 60 - index)
        }
        val allTeams = brazil + mundialUpper + mundialLower + unknownUpper + unknownLower
        repository.saveTeams(allTeams)

        val initialNonDomesticDivisions = (mundialUpper + mundialLower + unknownUpper + unknownLower)
            .associate { it.id to it.division }

        val save = GameSave(
            currentSeason = 2026,
            currentWeek = GameCalendar.WEEKS_PER_SEASON,
            playerTeamId = brazil.first().id
        )
        repository.saveGameSave(save)

        val result = transition.advanceToNextSeason(save)

        assertEquals(2027, result.currentSeason)
        assertEquals(1, result.currentWeek)

        val snapshot = repository.getGlobalStandingsForSeason(2026)
        assertEquals(setOf("Brasil"), snapshot.map { it.country }.toSet())
        assertTrue(snapshot.none { it.country == "Mundial" })
        assertTrue(snapshot.none { it.country == "País Inexistente" })

        val persistedTeams = repository.getAllTeams().associateBy { it.id }
        initialNonDomesticDivisions.forEach { (teamId, expectedDivision) ->
            assertEquals(
                "Clube não doméstico $teamId não pode sofrer promoção/rebaixamento.",
                expectedDivision,
                persistedTeams.getValue(teamId).division
            )
        }
        assertTrue(mundialUpper.all { persistedTeams.containsKey(it.id) })
        assertTrue(mundialLower.all { persistedTeams.containsKey(it.id) })
    }

    private fun team(
        id: Long,
        name: String,
        country: String,
        division: Int,
        rating: Int
    ) = Team(
        id = id,
        name = name,
        city = name,
        state = "ST",
        country = country,
        division = division,
        rating = rating
    )
}
