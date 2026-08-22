package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.data.repository.SlotRecoveryRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializa somente o trecho de seleção que valida/abre/publica a sessão. O trabalho posterior já
 * usa `sessionGeneration`; esta fila evita que dois toques rápidos em slots frios invertam a ordem
 * de publicação enquanto as aberturas acontecem em IO.
 */
private val safeSlotSelectionMutex = Mutex()

/**
 * Único entrypoint de UI para seleção de slot.
 *
 * Toda validação física/semântica e a abertura eager do Room acontecem em Dispatchers.IO. Assim,
 * migrations, WAL recovery e consultas de `game_save` nunca bloqueiam a thread de composição.
 * `GameViewModel.selectSaveSlot()` só publica a sessão depois que `getRepositoryForSlot()` retorna
 * com sucesso; qualquer falha fail-closed é reconciliada sem tocar nos artefatos recuperáveis.
 */
fun GameViewModel.selectSaveSlotSafely(saveId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            safeSlotSelectionMutex.withLock {
                selectSaveSlot(saveId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SlotRecoveryRequiredException) {
            Log.w("GameViewModel", "Slot $saveId bloqueado antes da abertura: ${e.inspection.failureReason}")
            loadSaveSlots()
        } catch (e: Exception) {
            // Falha de migration/open também precisa voltar à inspeção semântica; loadSaveSlots()
            // classificará o banco como RECOVERY_REQUIRED sem apagá-lo.
            Log.e("GameViewModel", "Falha fail-closed ao selecionar slot $saveId", e)
            loadSaveSlots()
        }
    }
}
