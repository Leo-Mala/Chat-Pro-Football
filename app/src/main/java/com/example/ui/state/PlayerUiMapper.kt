package com.example.ui.state

import androidx.compose.runtime.Immutable
import com.example.data.Player
import com.example.data.Team

@Immutable
data class UiPlayerItem(
    val id: Long,
    val teamId: Long?,
    val name: String,
    val age: Int,
    val position: String,
    val force: Int,
    val energy: Int,
    val moral: Int,
    val salary: Long,
    val isStarter: Boolean,
    val averageRating: Double
)

@Immutable
data class UiTeamItem(
    val id: Long,
    val name: String,
    val city: String,
    val state: String,
    val division: Int,
    val totalForce: Int
)

fun Player.toUiItem(): UiPlayerItem {
    return UiPlayerItem(
        id = id,
        teamId = teamId,
        name = name,
        age = age,
        position = position,
        force = force,
        energy = energy,
        moral = moral,
        salary = salary,
        isStarter = isStarter,
        averageRating = mediaNotas
    )
}

fun Team.toUiItem(totalForce: Int = 0): UiTeamItem {
    return UiTeamItem(
        id = id,
        name = name,
        city = city,
        state = state,
        division = division,
        totalForce = totalForce
    )
}
