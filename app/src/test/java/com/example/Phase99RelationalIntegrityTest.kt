package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.Fixture
import com.example.data.GameRepository
import com.example.data.MatchSlot
import com.example.data.Player
import com.example.data.Team
import com.example.support.RelationalIntegrityAssertions
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class Phase99RelationalIntegrityTest {

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
    fun `V21 blocks orphan players and fixtures and keeps canonical free agents`() = runTest {
        repository.saveTeams(
            listOf(
                team(1L, "A"),
                team(2L, "B"),
                team(3L, "Somente Player")
            )
        )
        repository.savePlayers(
            listOf(
                player(11L, 1L, "Com Clube"),
                player(12L, null, "Livre"),
                player(13L, 3L, "SET NULL")
            )
        )
        repository.saveFixtures(
            listOf(
                Fixture(
                    season = 2026,
                    week = 1,
                    homeTeamId = 1L,
                    awayTeamId = 2L,
                    competitionType = "SERIE_A",
                    matchSlot = MatchSlot.WEEKEND
                )
            )
        )

        assertNull(requireNotNull(repository.getPlayer(12L)).teamId)

        var orphanPlayerRejected = false
        try {
            repository.savePlayers(listOf(player(99L, 99_999L, "Órfão")))
        } catch (_: Exception) {
            orphanPlayerRejected = true
        }
        assertTrue("FK Player->Team deve rejeitar clube inexistente", orphanPlayerRejected)

        var orphanFixtureRejected = false
        try {
            db.fixtureDao().insertFixtures(
                listOf(
                    Fixture(
                        season = 2026,
                        week = 2,
                        homeTeamId = 1L,
                        awayTeamId = 88_888L,
                        competitionType = "SERIE_A"
                    )
                )
            )
        } catch (_: Exception) {
            orphanFixtureRejected = true
        }
        assertTrue("FK Fixture->Team deve rejeitar clube inexistente", orphanFixtureRejected)

        repository.deleteTeam(3L)
        assertNull("ON DELETE SET NULL deve liberar o atleta sem apagar Player", requireNotNull(repository.getPlayer(13L)).teamId)

        RelationalIntegrityAssertions.assertRepositoryReferences(repository)
        RelationalIntegrityAssertions.assertDatabasePragmas(db)
    }

    @Test
    fun `team upsert does not detach players and generated virtual fixtures are materialized`() = runTest {
        repository.saveTeams(listOf(team(1L, "Original"), team(2L, "Visitante")))
        repository.savePlayers(listOf(player(21L, 1L, "Vinculado")))

        repository.saveTeams(listOf(team(1L, "Atualizado")))
        assertEquals("Atualizado", requireNotNull(repository.getTeam(1L)).name)
        assertEquals(1L, requireNotNull(repository.getPlayer(21L)).teamId)

        val virtualId = 900_321L
        repository.saveFixtures(
            listOf(
                Fixture(
                    season = 2029,
                    week = 42,
                    homeTeamId = virtualId,
                    awayTeamId = 2L,
                    competitionType = "WORLD_CUP",
                    matchSlot = MatchSlot.MIDWEEK
                )
            )
        )

        assertNotNull("Participante virtual conhecido deve virar Team persistido antes do fixture", repository.getTeam(virtualId))
        assertEquals("Mundial", requireNotNull(repository.getTeam(virtualId)).country)

        var unknownLowIdRejected = false
        try {
            repository.saveFixtures(
                listOf(
                    Fixture(
                        season = 2029,
                        week = 43,
                        homeTeamId = 123_456L,
                        awayTeamId = 2L,
                        competitionType = "WORLD_CUP",
                        matchSlot = MatchSlot.MIDWEEK
                    )
                )
            )
        } catch (_: IllegalArgumentException) {
            unknownLowIdRejected = true
        }
        assertTrue("ID baixo desconhecido não pode ser inventado como clube virtual", unknownLowIdRejected)

        RelationalIntegrityAssertions.assertRepositoryReferences(repository)
        RelationalIntegrityAssertions.assertDatabasePragmas(db)
    }

    private fun team(id: Long, name: String) = Team(
        id = id,
        name = name,
        city = name,
        state = "BR",
        country = "Brasil",
        division = 1,
        rating = 70
    )

    private fun player(id: Long, teamId: Long?, name: String) = Player(
        id = id,
        teamId = teamId,
        name = name,
        age = 24,
        position = "MEI",
        force = 70,
        contractDurationWeeks = if (teamId == null) 0 else 52,
        salary = if (teamId == null) 0L else 10_000L
    )
}
