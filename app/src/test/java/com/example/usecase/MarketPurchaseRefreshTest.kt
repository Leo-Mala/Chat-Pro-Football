package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
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
class MarketPurchaseRefreshTest {
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
    fun `successful purchase refreshes persisted player flow to buyer ownership`() = runTest {
        val buyerId = 1L
        val sellerId = 2L
        val playerId = 10L
        val save = GameSave(id = 1, playerTeamId = buyerId, bankBalance = 20_000_000L)
        repository.saveGameSave(save)
        repository.saveTeams(
            listOf(
                Team(id = buyerId, name = "Buyer", city = "A", state = "AA", division = 1),
                Team(id = sellerId, name = "Seller", city = "B", state = "BB", division = 1)
            )
        )
        val player = Player(
            id = playerId,
            teamId = sellerId,
            name = "Target",
            age = 24,
            position = "ATA",
            force = 78,
            market_value = 3_000_000L
        )
        repository.savePlayers(listOf(player))

        val refreshed = async {
            repository.allPlayersFlow
                .dropWhile { players -> players.firstOrNull { it.id == playerId }?.teamId != buyerId }
                .first()
                .first { it.id == playerId }
        }

        val result = ProcessTransfersUseCase(repository).buyPlayerAdvanced(
            save = save,
            player = player,
            offerPrice = 3_000_000L,
            installments = 1
        )

        assertTrue(result is ProcessTransfersUseCase.TransferResult.Success)
        assertEquals(buyerId, refreshed.await().teamId)
        assertEquals(buyerId, repository.getPlayer(playerId)?.teamId)
    }
}
