package com.example.ui.screens

import com.example.data.ConmebolCompetitionSystem
import com.example.data.CupCompetitionSystem
import com.example.data.GameCalendar
import com.example.data.SuperMundialSystem
import com.example.data.UefaCompetitionSystem

internal fun isCompetitionFinalWeek(
    selectedLeague: String,
    week: Int,
    confederation: String? = null
): Boolean = when {
    selectedLeague in setOf(
        UefaCompetitionSystem.CHAMPIONS_LEAGUE,
        UefaCompetitionSystem.EUROPA_LEAGUE,
        UefaCompetitionSystem.CONFERENCE_LEAGUE
    ) -> week == UefaCompetitionSystem.FINAL_WEEK

    selectedLeague in setOf("CONTINENTAL_T1", "CONTINENTAL_T2", "LIBERTADORES", "SULAMERICANA") &&
        confederation.equals("CONMEBOL", ignoreCase = true) ->
        week == ConmebolCompetitionSystem.FINAL_WEEK

    selectedLeague in setOf("CONTINENTAL_T1", "CONTINENTAL_T2", "CONTINENTAL_T3", "LIBERTADORES", "SULAMERICANA") ->
        week == CupCompetitionSystem.CONTINENTAL_FINAL_WEEK

    selectedLeague == "COPA" -> week == CupCompetitionSystem.NATIONAL_CUP_FINAL_WEEK
    selectedLeague == "WORLD" || selectedLeague == "WORLD_CUP" ->
        week == GameCalendar.WEEKS_PER_SEASON
    else -> false
}

internal fun competitionPhaseTitle(
    selectedLeague: String,
    week: Int,
    confederation: String? = null
): String {
    if (selectedLeague in setOf(
            UefaCompetitionSystem.CHAMPIONS_LEAGUE,
            UefaCompetitionSystem.EUROPA_LEAGUE,
            UefaCompetitionSystem.CONFERENCE_LEAGUE
        )
    ) {
        return when (UefaCompetitionSystem.phaseFor(selectedLeague, week)) {
            UefaCompetitionSystem.Phase.LEAGUE_PHASE -> "Fase de Liga — Jornada UEFA"
            UefaCompetitionSystem.Phase.KNOCKOUT_PLAYOFF -> when (week) {
                UefaCompetitionSystem.PLAYOFF_LEG_1_WEEK -> "Playoff Eliminatório — Jogo de Ida"
                else -> "Playoff Eliminatório — Jogo de Volta"
            }
            UefaCompetitionSystem.Phase.ROUND_OF_16 -> when (week) {
                UefaCompetitionSystem.ROUND_OF_16_LEG_1_WEEK -> "Oitavas de Final — Jogo de Ida"
                else -> "Oitavas de Final — Jogo de Volta"
            }
            UefaCompetitionSystem.Phase.QUARTERFINAL -> when (week) {
                UefaCompetitionSystem.QUARTERFINAL_LEG_1_WEEK -> "Quartas de Final — Jogo de Ida"
                else -> "Quartas de Final — Jogo de Volta"
            }
            UefaCompetitionSystem.Phase.SEMIFINAL -> when (week) {
                UefaCompetitionSystem.SEMIFINAL_LEG_1_WEEK -> "Semifinais — Jogo de Ida"
                else -> "Semifinais — Jogo de Volta"
            }
            UefaCompetitionSystem.Phase.FINAL -> "🏆 GRANDE FINAL — Jogo Único"
            null -> "Competição UEFA (Semana $week)"
        }
    }

    val isTier1 = selectedLeague == "CONTINENTAL_T1" || selectedLeague == "LIBERTADORES"
    val isTier2 = selectedLeague == "CONTINENTAL_T2" || selectedLeague == "SULAMERICANA"
    val isConmebol = confederation.equals("CONMEBOL", ignoreCase = true)

    if (isConmebol && (isTier1 || isTier2)) {
        return when (week) {
            ConmebolCompetitionSystem.SUD_PLAYOFF_LEG_1_WEEK ->
                if (isTier2) "Playoff das Oitavas — Jogo de Ida" else "Fase Eliminatória (Semana $week)"
            ConmebolCompetitionSystem.SUD_PLAYOFF_LEG_2_WEEK ->
                if (isTier2) "Playoff das Oitavas — Jogo de Volta" else "Fase Eliminatória (Semana $week)"
            ConmebolCompetitionSystem.ROUND_OF_16_LEG_1_WEEK -> "Oitavas de Final — Jogo de Ida"
            ConmebolCompetitionSystem.ROUND_OF_16_LEG_2_WEEK -> "Oitavas de Final — Jogo de Volta"
            ConmebolCompetitionSystem.QUARTERFINAL_LEG_1_WEEK -> "Quartas de Final — Jogo de Ida"
            ConmebolCompetitionSystem.QUARTERFINAL_LEG_2_WEEK -> "Quartas de Final — Jogo de Volta"
            ConmebolCompetitionSystem.SEMIFINAL_LEG_1_WEEK -> "Semifinais — Jogo de Ida"
            ConmebolCompetitionSystem.SEMIFINAL_LEG_2_WEEK -> "Semifinais — Jogo de Volta"
            ConmebolCompetitionSystem.FINAL_WEEK -> "🏆 GRANDE FINAL — Jogo Único"
            else -> "Fase Eliminatória (Semana $week)"
        }
    }

    if (isTier1 || isTier2) {
        return when (week) {
            33 -> "Oitavas de Final"
            34 -> "Quartas de Final"
            35 -> "Semifinais"
            CupCompetitionSystem.CONTINENTAL_FINAL_WEEK -> "🏆 GRANDE FINAL"
            else -> "Fase Eliminatória (Semana $week)"
        }
    }

    return when (selectedLeague) {
        "CONTINENTAL_T3" -> when (week) {
            33 -> "Oitavas de Final"
            34 -> "Quartas de Final"
            35 -> "Semifinais"
            CupCompetitionSystem.CONTINENTAL_FINAL_WEEK -> "🏆 GRANDE FINAL"
            else -> "Fase Eliminatória (Semana $week)"
        }
        "COPA" -> when (week) {
            23 -> "1ª Fase"
            24 -> "Oitavas de Final"
            25 -> "Quartas de Final"
            26 -> "Semifinais"
            CupCompetitionSystem.NATIONAL_CUP_FINAL_WEEK -> "🏆 GRANDE FINAL"
            else -> "Copa Nacional (Semana $week)"
        }
        "WORLD", "WORLD_CUP" -> when (week) {
            SuperMundialSystem.ROUND_OF_16_WEEK -> "Oitavas de Final"
            SuperMundialSystem.QUARTERFINAL_WEEK -> "Quartas de Final"
            SuperMundialSystem.SEMIFINAL_WEEK -> "Semifinais"
            SuperMundialSystem.FINAL_WEEK -> "🏆 GRANDE FINAL DO SUPER MUNDIAL DE CLUBES 🌍"
            else -> "Super Mundial de Clubes (Semana $week)"
        }
        else -> "Rodada / Fase (Semana $week)"
    }
}
