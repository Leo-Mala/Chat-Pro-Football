package com.example.ui.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonSimulationContinuousRegressionTest {
    @Test
    fun seasonSimulationDoesNotStopForExpiringControlledContracts() {
        val source = File("src/main/java/com/example/ui/viewmodel/GameViewModel.kt").readText()
        assertFalse(source.contains("shouldPauseSeasonSimulationForExpiringContracts"))
        assertFalse(source.contains("getControlledRosterExpiringContractCount"))
        assertFalse(source.contains("Stop before playing/closing the week"))
    }

    @Test
    fun simulationFailureRemainsDiagnosableAfterReturningToIdle() {
        val source = File("src/main/java/com/example/ui/viewmodel/GameViewModel.kt").readText()
        assertTrue(source.contains("android.util.Log.e(\"GameViewModel\", \"Erro durante a simulação de temporada\", e)"))
        assertTrue(source.contains("_toastMessage.emit(\"Simulação interrompida:"))
        assertFalse(source.contains("if (com.example.BuildConfig.DEBUG)"))
        assertTrue(source.contains("internal val _lastSimulationError = MutableStateFlow<String?>(null)"))
        assertTrue(source.contains("val lastSimulationError: StateFlow<String?> = _lastSimulationError.asStateFlow()"))
        assertTrue(source.contains("fun dismissLastSimulationError()"))
        assertTrue(source.contains("_lastSimulationError.value = null"))
        assertTrue(source.contains("val errorSave = runCatching { repo.getGameSave() }.getOrNull()"))
        assertTrue(source.contains("val errorType = e.javaClass.simpleName.ifBlank { e.javaClass.name }"))
        assertTrue(source.contains("val errorDetail ="))
        assertTrue(source.contains("Temp. "))
        assertTrue(source.contains(" | Sem. "))
        assertTrue(source.contains("_lastSimulationError.value = errorDetail"))

        val dashboard = File("src/main/java/com/example/ui/components/dashboard/DashboardTabContent.kt").readText()
        assertTrue(dashboard.contains("val lastSimulationError by viewModel.lastSimulationError.collectAsStateWithLifecycle()"))
        assertTrue(dashboard.contains("lastSimulationError?.let { error ->"))
        assertTrue(dashboard.contains("SIMULAÇÃO INTERROMPIDA"))
        assertTrue(dashboard.contains("viewModel.dismissLastSimulationError()"))
        assertTrue(dashboard.contains("season_simulation_error_card"))
    }
}
