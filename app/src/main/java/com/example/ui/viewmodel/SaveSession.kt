package com.example.ui.viewmodel

import com.example.data.GameRepository

data class SaveSession(
    val slotId: String,
    val repository: GameRepository,
    val generation: Long,
    var hasSelfHealed: Boolean = false
)
