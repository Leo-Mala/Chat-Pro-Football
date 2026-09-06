from pathlib import Path
import re

vm_path = Path('app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt')
vm = vm_path.read_text(encoding='utf-8')
helper = "\ninternal fun shouldPauseSeasonSimulationForExpiringContracts(expiringContractCount: Int): Boolean =\n    expiringContractCount > 0\n"
if vm.count(helper) != 1:
    raise SystemExit('contract pause helper not found exactly once')
vm = vm.replace(helper, '\n', 1)

guard = re.compile(r'\n\s*// Auto-simulation must never make a contract-renewal decision for the\n\s*// human manager\. Stop before playing/closing the week so no fixture,\n\s*// finance or contract mutation for this week has been committed yet\.\n\s*val expiringControlledContracts =\n\s*repo\.getControlledRosterExpiringContractCount\(save\.playerTeamId\)\n\s*if \(shouldPauseSeasonSimulationForExpiringContracts\(expiringControlledContracts\)\) \{\n.*?\n\s*break\n\s*\}\n', re.S)
vm, count = guard.subn('\n', vm, count=1)
if count != 1:
    raise SystemExit('pre-week break guard not found exactly once')

debug_only = re.compile(r'\s*if \(com\.example\.BuildConfig\.DEBUG\) \{\n\s*android\.util\.Log\.e\("GameViewModel", "Erro durante a simulação de temporada", e\)\n\s*\}')
vm, count = debug_only.subn('\n                        android.util.Log.e("GameViewModel", "Erro durante a simulação de temporada", e)', vm, count=1)
if count != 1:
    raise SystemExit('debug-only simulation error log not found exactly once')

state_line = '_simulationLogs.value = listOf("Erro na simulação: ${e.localizedMessage ?: "Erro desconhecido"}") + _simulationLogs.value'
if vm.count(state_line) != 1:
    raise SystemExit('simulation error state line not found exactly once')
vm = vm.replace(state_line, state_line + '\n                        _toastMessage.emit("Simulação interrompida: ${e.localizedMessage ?: "Erro desconhecido"}")', 1)
vm_path.write_text(vm, encoding='utf-8')

repo_path = Path('app/src/main/java/com/example/data/repository.kt')
repo = repo_path.read_text(encoding='utf-8')
start_token = '    /**\n     * Counts only non-loaned players currently owned by the controlled sporting roster whose\n'
end_token = '    suspend fun getFreeAgents(): List<Player> = db.playerDao().getFreeAgents()\n'
start = repo.find(start_token)
end = repo.find(end_token, start)
if start < 0 or end < 0:
    raise SystemExit('repository contract helper boundaries not found')
removed = repo[start:end]
if removed.count('suspend fun getControlledRosterExpiringContractCount') != 1:
    raise SystemExit('unexpected repository removal range')
repo_path.write_text(repo[:start] + repo[end:], encoding='utf-8')

obsolete = Path('app/src/test/java/com/example/ui/viewmodel/SeasonSimulationContractGuardRegressionTest.kt')
if not obsolete.exists():
    raise SystemExit('obsolete regression test missing')
obsolete.unlink()

test_path = Path('app/src/test/java/com/example/ui/viewmodel/SeasonSimulationContinuousRegressionTest.kt')
test_path.write_text('''package com.example.ui.viewmodel

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
        assertTrue(source.contains("android.util.Log.e(\\\"GameViewModel\\\", \\\"Erro durante a simulação de temporada\\\", e)"))
        assertTrue(source.contains("_toastMessage.emit(\\\"Simulação interrompida:"))
        assertFalse(source.contains("if (com.example.BuildConfig.DEBUG) {\\n                            android.util.Log.e(\\\"GameViewModel\\\", \\\"Erro durante a simulação de temporada\\\", e)"))
    }
}
''', encoding='utf-8')
