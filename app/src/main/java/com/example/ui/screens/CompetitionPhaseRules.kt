package com.example.ui.screens

import com.example.data.GameCalendar

internal fun isCompetitionFinalWeek(selectedLeague: String, week: Int): Boolean {
    val isContinental =
        selectedLeague.contains("CONTINENTAL_T1") ||
            selectedLeague.contains("CONTINENTAL_T2") ||
            selectedLeague == "LIBERTADORES" ||
            selectedLeague == "SULAMERICANA"

    return when {
        isContinental -> week == 36
        selectedLeague == "COPA" -> week == 35
        selectedLeague == "WORLD" || selectedLeague == "WORLD_CUP" ->
            week == GameCalendar.WEEKS_PER_SEASON
        else -> false
    }
}
