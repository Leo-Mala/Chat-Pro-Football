package com.example.ui.screens

import com.example.data.GameCalendar

internal fun isCompetitionFinalWeek(selectedLeague: String, week: Int): Boolean {
    val isContinental =
        selectedLeague.startsWith("CONTINENTAL_T") ||
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

internal fun competitionPhaseTitle(selectedLeague: String, week: Int): String {
    val isContinental =
        selectedLeague.startsWith("CONTINENTAL_T") ||
            selectedLeague == "LIBERTADORES" ||
            selectedLeague == "SULAMERICANA"

    return when {
        isContinental -> when (week) {
            32 -> "Oitavas de Final"
            33 -> "Quartas de Final"
            34 -> "Semifinais"
            36 -> "🏆 GRANDE FINAL (Jogo Único)"
            else -> "Fase Eliminatória (Semana $week)"
        }

        selectedLeague == "COPA" -> when (week) {
            31 -> "16 avos de Final"
            32 -> "Oitavas de Final"
            33 -> "Quartas de Final"
            34 -> "Semifinais"
            35 -> "🏆 GRANDE FINAL (Jogo Único)"
            else -> "Copa Nacional (Semana $week)"
        }

        selectedLeague == "WORLD" || selectedLeague == "WORLD_CUP" -> when (week) {
            37 -> "Oitavas de Final"
            38 -> "Quartas de Final"
            39 -> "Semifinais"
            40 -> "🏆 GRANDE FINAL DO SUPER MUNDIAL DE CLUBES 🌍"
            else -> "Super Mundial de Clubes (Semana $week)"
        }

        selectedLeague.startsWith("SERIE_D_") -> when (selectedLeague) {
            "SERIE_D_O64" -> "Segunda Fase (64 Avos)"
            "SERIE_D_O32" -> "Terceira Fase (32 Avos)"
            "SERIE_D_O16" -> "Oitavas de Final"
            "SERIE_D_QF" -> "Quartas de Final (Acesso)"
            "SERIE_D_SF" -> "Semifinais"
            "SERIE_D_F" -> "🏆 GRANDE FINAL"
            "SERIE_D_PE" -> "Repescagem de Acesso"
            else -> "Série D - Fase Eliminatória"
        }

        else -> "Rodada / Fase (Semana $week)"
    }
}
