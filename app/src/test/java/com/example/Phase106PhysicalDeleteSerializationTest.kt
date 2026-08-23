package com.example

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106PhysicalDeleteSerializationTest {

    private lateinit var baseContext: Context
    private val slotId = "5"

    @Before
    fun setUp() {
        baseContext = ApplicationProvider.getApplicationContext()
        clearSlot()
    }

    @After
    fun tearDown() {
        clearSlot()
    }

    @Test
    fun sameSlotCannotReopenBetweenCloseAndPhysicalDelete() {
        val blockingContext = BlockingDeleteContext(
            baseContext,
            SlotDatabaseFactory.databaseNameForSlot(slotId)
        )
        val saveRepository = GameSaveRepository(blockingContext, SlotDatabaseFactory(blockingContext))
        val oldRepository = saveRepository.getRepositoryForSlot(slotId)
        val team = Team(
            id = 95_500L,
            name = "Carreira a Excluir",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 80,
            isPlayerControlled = true
        )
        runBlocking {
            oldRepository.saveTeams(listOf(team))
            oldRepository.saveGameSave(GameSave(coachName = "Excluir", playerTeamId = team.id))
        }

        val deleteFailure = AtomicReference<Throwable?>(null)
        val deleteResult = AtomicReference<Boolean?>(null)
        val deleteThread = Thread {
            try {
                deleteResult.set(runBlocking { saveRepository.deleteSlotDatabase(slotId) })
            } catch (t: Throwable) {
                deleteFailure.set(t)
            }
        }.apply { name = "phase106-delete" }

        blockingContext.blockDelete = true
        deleteThread.start()
        assertTrue(
            "Delete precisa alcançar Context.deleteDatabase mantendo o lock do slot",
            blockingContext.deleteEntered.await(5, TimeUnit.SECONDS)
        )

        val reopenedRepository = AtomicReference<com.example.data.GameRepository?>(null)
        val reopenFailure = AtomicReference<Throwable?>(null)
        val reopenStarted = CountDownLatch(1)
        val reopenThread = Thread {
            reopenStarted.countDown()
            try {
                reopenedRepository.set(saveRepository.getRepositoryForSlot(slotId))
            } catch (t: Throwable) {
                reopenFailure.set(t)
            }
        }.apply { name = "phase106-reopen" }
        reopenThread.start()
        assertTrue(reopenStarted.await(5, TimeUnit.SECONDS))

        // Sem sleeps: esperamos a JVM informar que a thread está BLOCKED no monitor do slot.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (reopenThread.state != Thread.State.BLOCKED &&
            reopenThread.state != Thread.State.TERMINATED &&
            System.nanoTime() < deadline
        ) {
            Thread.yield()
        }
        assertTrue(
            "Reopen do mesmo slot deve bloquear no monitor até a exclusão terminar; state=${reopenThread.state}",
            reopenThread.state == Thread.State.BLOCKED
        )
        assertNull("Nenhum repositório pode ser publicado enquanto delete está no meio", reopenedRepository.get())

        blockingContext.releaseDelete.countDown()
        deleteThread.join(5_000)
        reopenThread.join(5_000)

        deleteFailure.get()?.let { throw AssertionError("Delete falhou", it) }
        reopenFailure.get()?.let { throw AssertionError("Reopen falhou", it) }
        assertTrue("Exclusão explícita precisa concluir", deleteResult.get() == true)

        val newRepository = reopenedRepository.get()
        assertTrue("Reopen posterior deve criar um repositório novo", newRepository != null)
        assertNotSame("Nunca reutilizar o Room fechado/desvinculado", oldRepository, newRepository)
        assertNull("Carreira explicitamente removida não pode ressurgir na conexão nova", runBlocking {
            newRepository!!.getGameSave()
        })
        assertFalse("Clube antigo não pode sobreviver em conexão órfã", runBlocking {
            newRepository!!.getTeam(team.id) != null
        })

        saveRepository.closeAllDatabases()
    }

    private fun clearSlot() {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        val file = baseContext.getDatabasePath(name)
        baseContext.deleteDatabase(name)
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File(file.path + suffix).delete() }
        file.delete()
    }

    private class BlockingDeleteContext(
        base: Context,
        private val blockedName: String
    ) : ContextWrapper(base) {
        @Volatile
        var blockDelete: Boolean = false
        val deleteEntered = CountDownLatch(1)
        val releaseDelete = CountDownLatch(1)

        override fun deleteDatabase(name: String): Boolean {
            if (name == blockedName && blockDelete) {
                blockDelete = false
                deleteEntered.countDown()
                check(releaseDelete.await(5, TimeUnit.SECONDS)) {
                    "Timeout aguardando liberação do delete físico"
                }
            }
            return super.deleteDatabase(name)
        }
    }
}
