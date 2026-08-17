package com.example.data

import java.util.Calendar

object GameCalendar {
    /** Duração total da temporada de carreira. */
    const val WEEKS_PER_SEASON = 48

    /**
     * Limite esportivo do calendário detalhado das ligas nacionais.
     *
     * É deliberadamente independente de [WEEKS_PER_SEASON]: aumentar a duração da temporada
     * não pode fazer uma divisão gigante abandonar os grupos balanceados da Fase 9.5 apenas
     * porque passaram a existir mais datas para copas e continentais.
     */
    const val MAX_DOMESTIC_LEAGUE_ROUNDS = 40

    /**
     * Avança uma quantidade de semanas usando o calendário canônico da temporada.
     * A temporada possui 48 semanas e cada semana pode conter MIDWEEK e WEEKEND.
     */
    fun advanceWeeks(season: Int, week: Int, deltaWeeks: Int = 1): Pair<Int, Int> {
        require(week in 1..WEEKS_PER_SEASON) {
            "Semana inválida: $week. Esperado 1..$WEEKS_PER_SEASON."
        }
        require(deltaWeeks >= 0) { "deltaWeeks deve ser >= 0." }

        val zeroBasedWeek = (week - 1) + deltaWeeks
        val seasonOffset = zeroBasedWeek / WEEKS_PER_SEASON
        val normalizedWeek = (zeroBasedWeek % WEEKS_PER_SEASON) + 1
        return Pair(season + seasonOffset, normalizedWeek)
    }

    fun requireValidWeek(week: Int) {
        require(week in 1..WEEKS_PER_SEASON) {
            "Semana inválida: $week. Esperado 1..$WEEKS_PER_SEASON."
        }
    }

    private fun getCalendarForWeek(season: Int, week: Int): Calendar {
        requireValidWeek(week)
        val cal = Calendar.getInstance()
        cal.set(season, Calendar.JANUARY, 10, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, (week - 1) * 7)
        return cal
    }

    fun isTransferWindowOpen(season: Int, week: Int): Boolean {
        val cal = getCalendarForWeek(season, week)
        val month = cal.get(Calendar.MONTH) + 1 // 1 to 12
        return month == 1 || month == 2 || month == 7 || month == 8
    }

    fun getFormattedDate(season: Int, week: Int): String {
        val cal = getCalendarForWeek(season, week)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val monthStr = when (month) {
            1 -> "Jan"
            2 -> "Fev"
            3 -> "Mar"
            4 -> "Abr"
            5 -> "Mai"
            6 -> "Jun"
            7 -> "Jul"
            8 -> "Ago"
            9 -> "Set"
            10 -> "Out"
            11 -> "Nov"
            12 -> "Dez"
            else -> ""
        }
        return "$day/$monthStr/$year"
    }

    fun getLongFormattedDate(season: Int, week: Int): String {
        val cal = getCalendarForWeek(season, week)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = cal.get(Calendar.MONTH) + 1
        val year = cal.get(Calendar.YEAR)
        val monthStr = when (month) {
            1 -> "Janeiro"
            2 -> "Fevereiro"
            3 -> "Março"
            4 -> "Abril"
            5 -> "Maio"
            6 -> "Junho"
            7 -> "Julho"
            8 -> "Agosto"
            9 -> "Setembro"
            10 -> "Outubro"
            11 -> "Novembro"
            12 -> "Dezembro"
            else -> ""
        }
        return "$day de $monthStr de $year"
    }
}
