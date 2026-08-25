package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SlotRecoveryRequiredException
import java.util.WeakHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A fila de seleção pertence à instância do ViewModel. Isso preserva a serialização de dois toques
 * rápidos na mesma tela sem permitir que uma instância antiga/descartada mantenha outra instância
 * bloqueada por um mutex global do processo (especialmente durante teardown/recriação).
 */
private val safeSlotSelectionMutexes = WeakHashMap<GameViewModel, Mutex>()

private fun GameViewModel.safeSlotSelectionMutex(): Mutex =
    synchronized(safeSlotSelectionMutexes) {
        safeSlotSelectionMutexes.getOrPut(this) { Mutex() }
    }

/**
 * Único entrypoint de UI para seleção de slot.
 *
 * Toda validação física/semântica e a abertura eager do Room acontecem em Dispatchers.IO. Assim,
 * migrations, WAL recovery e consultas de `game_save` nunca bloqueiam a thread de composição.
 * Se uma seleção enfileirada falhar depois de invalidar a geração, a sessão anterior é republicada
 * com uma geração nova antes de liberar o mutex, evitando deixar o slot anterior funcionalmente
 * obsoleto.
 */
fun GameViewModel.selectSaveSlotSafely(saveId: String) {
    val selectionMutex = safeSlotSelectionMutex()
    viewModelScope.launch(Dispatchers.IO) {
        try {
            selectionMutex.withLock {
                // Captura dentro da fila: em dois toques rápidos, a segunda tentativa vê a sessão
                // que a primeira acabou de publicar, mesmo que ambas tenham sido enfileiradas antes.
                val previousSaveId = currentSaveId.value
                try {
                    selectSaveSlot(saveId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    restorePreviousSelectionAfterFailedOpen(previousSaveId, saveId)
                    throw e
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SlotRecoveryRequiredException) {
            Log.w("GameViewModel", "Slot $saveId bloqueado antes da abertura: ${e.inspection.failureReason}")
            loadSaveSlots()
        } catch (e: Exception) {
            Log.e("GameViewModel", "Falha fail-closed ao selecionar slot $saveId", e)
            loadSaveSlots()
        }
    }
}

/**
 * `selectSaveSlot()` incrementa a geração antes de abrir o novo repositório. Se a abertura falhar,
 * a sessão anterior ainda pode estar publicada, porém sua geração ficou obsoleta.
 *
 * - outro slot anterior: reabre/publica esse slot com geração nova;
 * - o próprio slot que falhou: limpa a sessão, porque republicar o mesmo repositório que acabou de
 *   falhar no preflight quebraria a garantia fail-closed;
 * - nenhuma sessão anterior: nada precisa ser restaurado.
 */
private fun GameViewModel.restorePreviousSelectionAfterFailedOpen(
    previousSaveId: String?,
    failedSaveId: String
) {
    if (previousSaveId == null) return
    if (previousSaveId == failedSaveId) {
        exitToSavesMenu()
        return
    }

    try {
        selectSaveSlot(previousSaveId)
    } catch (restoreError: Exception) {
        Log.e(
            "GameViewModel",
            "Falha ao restaurar sessão anterior $previousSaveId após erro no slot $failedSaveId",
            restoreError
        )
        exitToSavesMenu()
    }
}
