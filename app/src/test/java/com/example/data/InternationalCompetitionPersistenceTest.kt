package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class InternationalCompetitionPersistenceTest {

    @Test
    fun `UEFA and world fixtures survive database reopen without regeneration`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "phase103-reopen.db"
        context.deleteDatabase(dbName)

        val uefaTeams = uefaTeams()
        val worldTeams = worldTeams()
        val worldStandings = standings(worldTeams)
        val uefaFixtures = UefaCompetitionSystem.generateLeaguePhase(
            2027,
            uefaTeams,
            UefaCompetitionSystem.CHAMPIONS_LEAGUE
        )
        val worldFixtures = SuperMundialSystem.generateGroupStageFixtures(2029, worldTeams, worldStandings)
        assertEquals(144, uefaFixtures.size)
        assertEquals(48, worldFixtures.size)

        var db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        var repository = GameRepository(db)
        repository.saveTeams(uefaTeams + worldTeams)
        repository.saveFixtures(uefaFixtures + worldFixtures)
        val before = scheduleSignature(repository.getAllFixtures())
        db.close()

        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .allowMainThreadQueries()
            .build()
        repository = GameRepository(db)
        val after = scheduleSignature(repository.getAllFixtures())

        assertEquals(before, after)
        assertEquals(144, repository.getFixturesForSeason(2027).count { it.competitionType == UefaCompetitionSystem.CHAMPIONS_LEAGUE })
        assertEquals(48, repository.getFixturesForSeason(2029).count { it.competitionType.startsWith("WORLD_CUP_GP_") })
        db.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `international competition state is isolated across database slots`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbAName = "phase103-slot-a.db"
        val dbBName = "phase103-slot-b.db"
        context.deleteDatabase(dbAName)
        context.deleteDatabase(dbBName)

        val teams = uefaTeams()
        val dbA = Room.databaseBuilder(context, AppDatabase::class.java, dbAName).allowMainThreadQueries().build()
        val dbB = Room.databaseBuilder(context, AppDatabase::class.java, dbBName).allowMainThreadQueries().build()
        val repoA = GameRepository(dbA)
        val repoB = GameRepository(dbB)
        repoA.saveTeams(teams)
        repoB.saveTeams(teams)

        repoA.saveFixtures(UefaCompetitionSystem.generateLeaguePhase(2027, teams, UefaCompetitionSystem.CHAMPIONS_LEAGUE))
        repoB.saveFixtures(UefaCompetitionSystem.generateLeaguePhase(2030, teams, UefaCompetitionSystem.CONFERENCE_LEAGUE))

        assertEquals(144, repoA.getAllFixtures().size)
        assertEquals(108, repoB.getAllFixtures().size)
        assertTrue(repoA.getAllFixtures().all { it.competitionType == UefaCompetitionSystem.CHAMPIONS_LEAGUE })
        assertTrue(repoB.getAllFixtures().all { it.competitionType == UefaCompetitionSystem.CONFERENCE_LEAGUE })
        assertTrue(repoA.getFixturesForSeason(2030).isEmpty())
        assertTrue(repoB.getFixturesForSeason(2027).isEmpty())

        dbA.close()
        dbB.close()
        context.deleteDatabase(dbAName)
        context.deleteDatabase(dbBName)
    }

    private fun scheduleSignature(fixtures: List<Fixture>): List<String> = fixtures
        .sortedWith(FixtureScheduleValidator.chronologicalComparator())
        .map { "${it.season}:${it.week}:${it.matchSlot}:${it.competitionType}:${it.homeTeamId}:${it.awayTeamId}" }

    private fun uefaTeams(): List<Team> {
        val countries = listOf(
            "Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal",
            "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça",
            "Dinamarca", "Noruega", "Suécia", "Polônia", "Tchéquia", "Croácia", "Sérvia", "Grécia"
        )
        return (0 until 36).map { index ->
            val country = countries[index % countries.size]
            Team(70_000L + index, "UEFA persist ${index + 1}", "Cidade", country.take(2), country, 1, rating = 80)
        }
    }

    private fun worldTeams(): List<Team> {
        val countries = buildList {
            addAll(listOf("Inglaterra", "Espanha", "Itália", "Alemanha", "França", "Portugal", "Países Baixos", "Bélgica", "Turquia", "Escócia", "Áustria", "Suíça", "Dinamarca"))
            addAll(listOf("Brasil", "Brasil", "Argentina", "Colômbia", "Chile", "Uruguai", "Paraguai", "Venezuela"))
            addAll(listOf("Japão", "Coreia do Sul", "Arábia Saudita", "Emirados Árabes Unidos", "Catar"))
            addAll(listOf("Egito", "Marrocos", "Tunísia", "África do Sul", "África"))
            addAll(listOf("México", "Estados Unidos / Canadá", "Costa Rica", "Guatemala", "Honduras"))
            addAll(listOf("Oceania", "Oceania"))
        }
        return countries.mapIndexed { index, country ->
            Team(80_000L + index, "World persist ${index + 1}", "Cidade", country.take(2), country, 1, rating = 75)
        }
    }

    private fun standings(teams: List<Team>): List<GlobalLeagueStanding> =
        teams.groupBy { it.country }.flatMap { (_, associationTeams) ->
            associationTeams.sortedBy { it.id }.mapIndexed { index, team ->
                GlobalLeagueStanding(
                    season = 2028,
                    country = team.country,
                    division = 1,
                    teamId = team.id,
                    position = index + 1,
                    points = 90 - index,
                    played = 38,
                    wins = 0,
                    draws = 0,
                    losses = 0,
                    goalsFor = 0,
                    goalsAgainst = 0,
                    goalDifference = 0
                )
            }
        }
}
