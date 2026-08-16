package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import kotlinx.coroutines.runBlocking
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
class AcademyPromotionAtomicityTest {

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
    fun `promotion writes player and academy list in one transaction`() = runBlocking {
        val originalAcademy = "prospect-a|prospect-b"
        repository.saveGameSave(
            GameSave(
                coachName = "Atomic QA",
                playerTeamId = 10L,
                academyProspects = originalAcademy
            )
        )

        val committed = repository.promoteAcademyPlayerAtomically(
            expectedPlayerTeamId = 10L,
            expectedAcademyProspects = originalAcademy,
            player = Player(
                teamId = 10L,
                name = "Novo da Base",
                age = 16,
                position = "MEI",
                force = 48,
                potential = 80,
                isFromAcademy = true
            ),
            updatedAcademyProspects = "prospect-b"
        )

        assertTrue(committed)
        assertEquals("prospect-b", repository.getGameSave()?.academyProspects)
        val promoted = repository.getPlayersByTeam(10L).single()
        assertEquals("Novo da Base", promoted.name)
        assertTrue(promoted.isFromAcademy)
    }

    @Test
    fun `stale academy snapshot writes neither player nor save`() = runBlocking {
        repository.saveGameSave(
            GameSave(
                coachName = "Conflict QA",
                playerTeamId = 10L,
                academyProspects = "newer-state"
            )
        )

        val committed = repository.promoteAcademyPlayerAtomically(
            expectedPlayerTeamId = 10L,
            expectedAcademyProspects = "stale-state",
            player = Player(
                teamId = 10L,
                name = "Não Deve Entrar",
                age = 16,
                position = "ATA",
                force = 45,
                potential = 78,
                isFromAcademy = true
            ),
            updatedAcademyProspects = ""
        )

        assertFalse(committed)
        assertEquals("newer-state", repository.getGameSave()?.academyProspects)
        assertTrue(repository.getPlayersByTeam(10L).isEmpty())
    }
}
