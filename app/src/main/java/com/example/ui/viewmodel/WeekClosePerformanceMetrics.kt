package com.example.ui.viewmodel

/**
 * Structured timings for the canonical weekly close. No Logcat output is emitted by production;
 * callers that need measurements (focused benchmarks/manual diagnostics) pass an explicit sink.
 */
data class WeekClosePerformanceMetrics(
    val season: Int,
    val week: Int,
    val tWeekFinanceMillis: Long,
    val tContractsMillis: Long,
    val tCpuSquadMillis: Long,
    val tTransfersMillis: Long,
    val tMonthlyPrepareMillis: Long,
    val tMonthlyCommitMillis: Long,
    val tCupsMillis: Long,
    val tWeekAdvanceMillis: Long,
    val tTotalWeekCloseMillis: Long,
    val monthlyPlayersCount: Int,
    val playersUpdatedCount: Int
) {
    fun asDiagnosticLine(prefix: String = "WEEK_CLOSE"): String =
        "$prefix " +
            "T_WEEK_FINANCE=$tWeekFinanceMillis " +
            "T_CONTRACTS=$tContractsMillis " +
            "T_CPU_SQUAD=$tCpuSquadMillis " +
            "T_TRANSFERS=$tTransfersMillis " +
            "T_MONTHLY_PREPARE=$tMonthlyPrepareMillis " +
            "T_MONTHLY_COMMIT=$tMonthlyCommitMillis " +
            "T_CUPS=$tCupsMillis " +
            "T_WEEK_ADVANCE=$tWeekAdvanceMillis " +
            "T_TOTAL_WEEK_CLOSE=$tTotalWeekCloseMillis " +
            "MONTHLY_PLAYERS_COUNT=$monthlyPlayersCount " +
            "PLAYERS_UPDATED_COUNT=$playersUpdatedCount"
}
