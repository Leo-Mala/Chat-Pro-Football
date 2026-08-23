package com.example

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.data.GameSave
import com.example.data.Team
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import com.example.data.repository.SlotDatabaseInspection
import com.example.data.repository.SlotDatabaseState
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Phase106InspectDeleteSerializationTest {

    private lateinit var baseContext: Context
    private val slotId = "4"

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
    fun inspectionWaitingBehindPhysicalDeleteReturnsMissingWithoutRecreatingRoom() {
        val blockingContext = BlockingDeleteContext(
            baseContext,
            SlotDatabaseFactory.databaseNameForSlot(slotId)
        )
        val saveRepository = GameSaveRepository(blockingContext, SlotDatabaseFactory(blockingContext))
        val repository = saveRepository.getRepositoryForSlot(slotId)
        val team = Team(
            id = 94_400L,
            name = "Carreira em Exclusão",
            city = "Belo Horizonte",
            state = "MG",
            country = "Brasil",
            division = 1,
            rating = 80,
            isPlayerControlled = true
        )
        runBlocking {
            repository.saveTeams(listOf(team))
            repository.saveGameSave(GameSave(coachName = "Excluir", playerTeamId = team.id))
        }

        val deleteFailure = AtomicReference<Throwable?>(null)
        val deleteResult = AtomicReference<Boolean?>(null)
        blockingContext.blockDelete = true
        val deleteThread = Thread {
            try {
                deleteResult.set(runBlocking { saveRepository.deleteSlotDatabase(slotId) })
            } catch (t: Throwable) {
                deleteFailure.set(t)
            }
        }.apply { name = "phase106-delete-vs-inspect" }
        deleteThread.start()

        assertTrue(
            "Delete precisa chegar à fronteira física mantendo o lifecycle lock",
            blockingContext.deleteEntered.await(5, TimeUnit.SECONDS)
        )

        val inspection = AtomicReference<SlotDatabaseInspection?>(null)
        val inspectionFailure = AtomicReference<Throwable?>(null)
        val inspectionStarted = CountDownLatch(1)
        val inspectionThread = Thread {
            inspectionStarted.countDown()
            try {
                inspection.set(runBlocking { saveRepository.inspectSlot(slotId) })
            } catch (t: Throwable) {
                inspectionFailure.set(t)
            }
        }.apply { name = "phase106-inspect-during-delete" }
        inspectionThread.start()
        assertTrue(inspectionStarted.await(5, TimeUnit.SECONDS))

        // A inspeção deve esperar o mesmo monitor antes de decidir que o arquivo existente pode
        // ser aberto. Sem esta barreira ela observa o arquivo antigo, espera depois no reopen e
        // recria um Room vazio assim que o delete libera o monitor.
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (inspectionThread.state != Thread.State.BLOCKED &&
            inspectionThread.state != Thread.State.TERMINATED &&
            System.nanoTime() < deadline
        ) {
            Thread.yield()
        }
        assertEquals(
            "Inspeção do mesmo slot precisa bloquear durante exclusão física",
            Thread.State.BLOCKED,
            inspectionThread.state
        )

        blockingContext.releaseDelete.countDown()
        deleteThread.join(5_000)
        inspectionThread.join(5_000)

        deleteFailure.get()?.let { throw AssertionError("Delete falhou", it) }
        inspectionFailure.get()?.let { throw AssertionError("Inspeção falhou", it) }
        assertTrue("Exclusão explícita precisa concluir", deleteResult.get() == true)
        assertEquals(
            "Inspeção retomada depois do delete deve observar ausência, não criar DB vazio",
            SlotDatabaseState.MISSING,
            inspection.get()?.state
        )

        val databaseFile = saveRepository.databaseFileForSlot(slotId)
        assertFalse("Inspect não pode recriar o arquivo principal após exclusão", databaseFile.exists())
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            assertFalse("Inspect não pode recriar sidecar $suffix", File(databaseFile.path + suffix).exists())
        }

        saveRepository.closeAllDatabases()
        assertNull(deleteFailure.get())
        assertNull(inspectionFailure.get())
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
