package com.example.usecase

import android.content.Context
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
class MarketPurchasePersistenceTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val databaseName = "market-purchase-persistence.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        if (::db.isInitialized && db.isOpen) db.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun `successful purchase survives repository and database reopen`() = runTest {
        val buyerId = 1L
        val sellerId = 2L
        val playerId = 20L
        var repository = GameRepository(db)
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
            name = "Persistent Target",
            age = 25,
            position = "MEI",
            force = 76,
            market_value = 2_000_000L
        )
        repository.savePlayers(listOf(player))

        val result = ProcessTransfersUseCase(repository).buyPlayerAdvanced(
            save = save,
            player = player,
            offerPrice = 2_000_000L,
            installments = 1
        )
        assertTrue(result is ProcessTransfersUseCase.TransferResult.Success)
        assertEquals(buyerId, repository.getPlayer(playerId)?.teamId)

        db.close()
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        repository = GameRepository(db)

        assertEquals(buyerId, repository.getPlayer(playerId)?.teamId)
        assertEquals(18_000_000L, repository.getGameSave()?.bankBalance)
    }
}
