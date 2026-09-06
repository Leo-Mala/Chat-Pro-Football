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
    fun simulationFailureIsDiagnosableOutsideDebugBuilds() {
        val source = File("src/main/java/com/example/ui/viewmodel/GameViewModel.kt").readText()
        assertTrue(source.contains("android.util.Log.e(\"GameViewModel\", \"Erro durante a simulação de temporada\", e)"))
        assertTrue(source.contains("_toastMessage.emit(\"Simulação interrompida:"))
        assertFalse(source.contains("if (com.example.BuildConfig.DEBUG) {\n                            android.util.Log.e(\"GameViewModel\", \"Erro durante a simulação de temporada\", e)"))
    }
}
