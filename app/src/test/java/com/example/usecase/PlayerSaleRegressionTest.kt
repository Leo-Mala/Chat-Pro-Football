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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private data class SaleHarness(
    val db: AppDatabase,
    val repository: GameRepository,
    val useCase: ProcessTransfersUseCase,
    val save: GameSave,
    val player: Player
)

private suspend fun createSaleHarness(buyerRosterSize: Int): SaleHarness {
    val db = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    ).allowMainThreadQueries().build()
    val repository = GameRepository(db)
    val useCase = ProcessTransfersUseCase(repository)
    val save = GameSave(id = 1, playerTeamId = 1L, bankBalance = 98_500_000L)
    repository.saveGameSave(save)
    repository.saveTeams(
        listOf(
  Team(id = 1L, name = "Cruzeiro", city = "Belo Horizonte", state = "MG", division = 1),
  Team(id = 2L, name = "Comprador", city = "São Paulo", state = "SP", division = 1)
        )
    )
    val sellerRoster = (1L..18L).map { id ->
        Player(id = id, teamId = 1L, name = if (id == 1L) "Arthur Silva" else "Seller $id", age = 24, position = "MEI", force = 75)
    }
    val buyerRoster = (1 until (1 + buyerRosterSize)).map { offset ->
        val id = 1_000L + offset
        Player(id = id, teamId = 2L, name = "Buyer $offset", age = 24, position = "MEI", force = 70)
    }
    repository.savePlayers(sellerRoster + buyerRoster)
    return SaleHarness(db, repository, useCase, save, sellerRoster.first())
}

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class PlayerSaleBuyerCapacityRegressionTest {
    @Test
    fun `cash buyer rosters 29 through 34 are allowed and 35 is blocked`() = runTest {
        for (size in 29..35) {
  val h = createSaleHarness(size)
  try {
      val result = h.useCase.sellPlayer(h.save, h.player, 5_117_099L)
      if (size < ProcessTransfersUseCase.MAX_ROSTER_SIZE) {
          assertTrue("size=$size", result is ProcessTransfersUseCase.TransferResult.Success)
          assertEquals(2L, h.repository.getPlayer(h.player.id)?.teamId)
          assertEquals(size + 1, h.repository.getPlayerCountByTeam(2L))
      } else {
          assertTrue("size=$size", result is ProcessTransfersUseCase.TransferResult.Error)
          assertEquals(1L, h.repository.getPlayer(h.player.id)?.teamId)
          assertEquals(size, h.repository.getPlayerCountByTeam(2L))
      }
  } finally {
      h.db.close()
  }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class InstallmentSaleBuyerCapacityRegressionTest {
    @Test
    fun `installment buyer rosters 29 through 34 are allowed and 35 is blocked`() = runTest {
        for (size in 29..35) {
  val h = createSaleHarness(size)
  try {
      val result = h.useCase.executeInstallmentSale(
          save = h.save,
          buyerTeamId = 2L,
          player = h.player,
          offerPrice = 5_117_099L,
          installments = ProcessTransfersUseCase.INSTALLMENT_COUNT
      )
      if (size < ProcessTransfersUseCase.MAX_ROSTER_SIZE) {
          assertTrue("size=$size", result is ProcessTransfersUseCase.TransferResult.Success)
          assertEquals(2L, h.repository.getPlayer(h.player.id)?.teamId)
          assertEquals(size + 1, h.repository.getPlayerCountByTeam(2L))
      } else {
          assertTrue("size=$size", result is ProcessTransfersUseCase.TransferResult.Error)
          assertEquals(1L, h.repository.getPlayer(h.player.id)?.teamId)
          assertEquals(size, h.repository.getPlayerCountByTeam(2L))
      }
  } finally {
      h.db.close()
  }
        }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class PlayerSaleImmediateRemovalRegressionTest {
    @Test
    fun `successful cash sale immediately changes ownership in Room`() = runTest {
        val h = createSaleHarness(30)
        try {
  val result = h.useCase.sellPlayer(h.save, h.player, 5_117_099L)
  assertTrue(result is ProcessTransfersUseCase.TransferResult.Success)
  assertFalse(h.repository.getPlayersByTeam(1L).any { it.id == h.player.id })
  assertTrue(h.repository.getPlayersByTeam(2L).any { it.id == h.player.id })
        } finally {
  h.db.close()
        }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class PlayerSalePersistenceRegressionTest {
    @Test
    fun `successful cash sale survives database close and reopen`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "player-sale-persistence-${System.nanoTime()}.db"
        context.deleteDatabase(dbName)
        var db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build()
        try {
  var repository = GameRepository(db)
  val save = GameSave(id = 1, playerTeamId = 1L, bankBalance = 98_500_000L)
  repository.saveGameSave(save)
  repository.saveTeams(
      listOf(
          Team(id = 1L, name = "Cruzeiro", city = "Belo Horizonte", state = "MG", division = 1),
          Team(id = 2L, name = "Comprador", city = "São Paulo", state = "SP", division = 1)
      )
  )
  val sellerRoster = (1L..18L).map { id ->
      Player(id = id, teamId = 1L, name = if (id == 1L) "Arthur Silva" else "Seller $id", age = 24, position = "MEI", force = 75)
  }
  val buyerRoster = (1L..30L).map { offset ->
      Player(id = 1_000L + offset, teamId = 2L, name = "Buyer $offset", age = 24, position = "MEI", force = 70)
  }
  repository.savePlayers(sellerRoster + buyerRoster)
  val result = ProcessTransfersUseCase(repository).sellPlayer(save, sellerRoster.first(), 5_117_099L)
  assertTrue(result is ProcessTransfersUseCase.TransferResult.Success)
  db.close()

  db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build()
  repository = GameRepository(db)
  assertEquals(2L, repository.getPlayer(1L)?.teamId)
  assertEquals(103_617_099L, repository.getGameSave()?.bankBalance)
        } finally {
  if (db.isOpen) db.close()
  context.deleteDatabase(dbName)
        }
    }
}

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class PlayerSaleNoDoubleCreditRegressionTest {
    @Test
    fun `retrying stale cash sale cannot credit the club twice`() = runTest {
        val h = createSaleHarness(30)
        try {
  val first = h.useCase.sellPlayer(h.save, h.player, 5_117_099L)
  assertTrue(first is ProcessTransfersUseCase.TransferResult.Success)
  val second = h.useCase.sellPlayer(h.save, h.player, 5_117_099L)
  assertTrue(second is ProcessTransfersUseCase.TransferResult.Error)
  assertEquals(103_617_099L, h.repository.getGameSave()?.bankBalance)
        } finally {
  h.db.close()
        }
    }
}
