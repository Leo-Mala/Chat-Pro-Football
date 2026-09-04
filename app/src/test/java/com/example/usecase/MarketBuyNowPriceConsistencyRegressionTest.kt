package com.example.usecase

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.AppDatabase
import com.example.data.GameRepository
import com.example.data.GameSave
import com.example.data.Player
import com.example.data.Team
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
class MarketBuyNowPriceConsistencyRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = GameRepository(db)
    }

    @After fun tearDown() { db.close() }

    @Test
    fun `buy now debits exactly the canonical price shown to the user`() = runTest {
        val buyerId = 1L
        val sellerId = 2L
        val save = GameSave(id = 1, playerTeamId = buyerId, bankBalance = 100_000_000L, coachReputation = 100)
        repository.saveTeams(listOf(
            Team(id = buyerId, name = "Buyer", city = "A", state = "AA", division = 1),
            Team(id = sellerId, name = "Seller", city = "B", state = "BB", division = 1)
        ))
        val player = Player(id = 77L, teamId = sellerId, name = "Igor Almeida", age = 25, position = "GOL", force = 99, potential = 99)
        repository.saveGameSave(save)
        repository.savePlayers(listOf(player))

        val shownPrice = player.calculateMarketValue()
        assertEquals(29_700_000L, shownPrice)
        val result = ProcessTransfersUseCase(repository).buyPlayer(save, player, shownPrice)
        assertTrue(result is ProcessTransfersUseCase.TransferResult.Success)
        assertEquals(save.bankBalance - shownPrice, repository.getGameSave()?.bankBalance)
        assertEquals(shownPrice, repository.getAllTransactions().single().amount)
        assertEquals(buyerId, repository.getPlayer(player.id)?.teamId)
    }
}
