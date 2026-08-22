package com.example.ui.viewmodel

import android.util.Log
import com.example.data.repository.SlotRecoveryRequiredException

/**
 * Único entrypoint de UI para seleção de slot.
 *
 * `GameSaveRepository` valida e materializa o SQLite antes de devolver uma instância. Se o
 * filesystem, migration ou a tabela `game_save` exigirem recuperação, a abertura falha antes de
 * `GameViewModel.selectSaveSlot()` conseguir criar sessão ou executar seed. Este wrapper transforma
 * essa falha síncrona em uma nova reconciliação visível, preservando os artefatos e mantendo Novo
 * Jogo bloqueado.
 */
fun GameViewModel.selectSaveSlotSafely(saveId: String) {
    try {
        selectSaveSlot(saveId)
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
