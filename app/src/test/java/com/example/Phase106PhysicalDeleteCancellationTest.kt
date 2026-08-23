package com.example

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
@Config(manifest = Config.NONE)
class Phase106PhysicalDeleteCancellationTest {

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
    fun cancellationImmediatelyBeforePhysicalDeletePreservesDatabase() = runBlocking {
        // Materializa um Room canônico e não vazio fisicamente. A metadata já pode ter sido
        // removida neste ponto; o próprio banco precisa continuar sendo a fonte recuperável.
        val setupFactory = SlotDatabaseFactory(baseContext)
        val setupRepository = GameSaveRepository(baseContext, setupFactory)
        setupRepository.getRepositoryForSlot(slotId)
        setupRepository.closeAllDatabases()

        val databaseFile = baseContext.getDatabasePath(SlotDatabaseFactory.databaseNameForSlot(slotId))
        assertTrue(databaseFile.exists())
        assertTrue(databaseFile.length() > 0L)

        val blockingContext = BlockingDatabasePathContext(
            base = baseContext,
            blockedDatabaseName = SlotDatabaseFactory.databaseNameForSlot(slotId)
        )
        val deletingRepository = GameSaveRepository(
            blockingContext,
            SlotDatabaseFactory(blockingContext)
        )

        blockingContext.blockNextDatabasePathLookup = true
        val deletion = async(Dispatchers.IO) {
            deletingRepository.deleteSlotDatabase(slotId)
        }

        assertTrue(
            "Teste precisa alcançar a fronteira imediatamente anterior ao delete físico",
            blockingContext.entered.await(5, TimeUnit.SECONDS)
        )

        // Reproduz lifecycle/viewModelScope cancelado depois da etapa de metadata, enquanto a
        // exclusão física já foi solicitada mas ainda não atravessou o ensureActive final.
        deletion.cancel(CancellationException("phase-10.6 forced cancellation before physical delete"))
        blockingContext.release.countDown()

        var cancelled = false
        try {
            deletion.await()
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue("A exclusão precisa propagar o cancelamento", cancelled)
        assertTrue("Cancelamento não pode apagar o banco do slot", databaseFile.exists())
        assertTrue("Cancelamento não pode truncar o banco", databaseFile.length() > 0L)

        // O arquivo preservado continua semanticamente utilizável: era um Room pré-carreira vazio
        // e deve permanecer assim, em vez de virar MISSING por uma exclusão tardia.
        val reopened = GameSaveRepository(baseContext, SlotDatabaseFactory(baseContext))
        try {
            assertEquals(
                com.example.data.repository.SlotDatabaseState.EMPTY,
                reopened.inspectSlot(slotId).state
            )
        } finally {
            reopened.closeAllDatabases()
        }
    }

    private fun clearSlot() {
        val name = SlotDatabaseFactory.databaseNameForSlot(slotId)
        val file = baseContext.getDatabasePath(name)
        baseContext.deleteDatabase(name)
        listOf("-wal", "-shm", "-journal").forEach { suffix -> File(file.path + suffix).delete() }
        file.delete()
    }

    private class BlockingDatabasePathContext(
        base: Context,
        private val blockedDatabaseName: String
    ) : ContextWrapper(base) {
        @Volatile
        var blockNextDatabasePathLookup: Boolean = false

        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)

        override fun getDatabasePath(name: String): File {
            if (name == blockedDatabaseName && blockNextDatabasePathLookup) {
                blockNextDatabasePathLookup = false
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS)) {
                    "Timeout aguardando liberação do getDatabasePath bloqueado"
                }
            }
            return super.getDatabasePath(name)
        }
    }
}
