package com.example.data.model

import androidx.annotation.Keep

@Keep
data class SaveSlotMetadata(
    val id: String,
    val exists: Boolean = false,
    val coachName: String = "",
    val teamName: String = "",
    val season: Int = 2026,
    val week: Int = 1,
    val balance: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * True quando há um banco físico que não pôde ser classificado com segurança como vazio ou
     * como carreira válida. O slot permanece ocupado/fail-closed para impedir reseed destrutivo.
     */
    val recoveryRequired: Boolean = false,
    val recoveryMessage: String? = null
)
