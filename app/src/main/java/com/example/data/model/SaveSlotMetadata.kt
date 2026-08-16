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
    val updatedAt: Long = System.currentTimeMillis()
)
