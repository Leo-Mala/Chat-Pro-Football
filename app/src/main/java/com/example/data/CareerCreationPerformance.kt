package com.example.data

/**
 * Snapshot leve da criação de uma carreira. Os tempos são medidos no fluxo real de Novo Jogo e
 * mantidos somente em memória para diagnóstico/testes; nenhum dado esportivo ou save é alterado.
 */
data class CareerCreationPerformanceSnapshot(
    val databaseBootstrapMs: Long,
    val rosterMaterializationMs: Long,
    val clubSetupMs: Long,
    val competitionCalendarMs: Long,
    val persistenceMs: Long,
    val totalMs: Long,
    val teamCount: Int,
    val playerCount: Int,
    val fixtureCount: Int
)

object CareerCreationPerformanceMonitor {
    @Volatile
    var latest: CareerCreationPerformanceSnapshot? = null
        private set

    fun record(snapshot: CareerCreationPerformanceSnapshot) {
        require(snapshot.databaseBootstrapMs >= 0L)
        require(snapshot.rosterMaterializationMs >= 0L)
        require(snapshot.clubSetupMs >= 0L)
        require(snapshot.competitionCalendarMs >= 0L)
        require(snapshot.persistenceMs >= 0L)
        require(snapshot.totalMs >= 0L)
        require(snapshot.totalMs >= snapshot.databaseBootstrapMs)
        require(snapshot.totalMs >= snapshot.persistenceMs)
        latest = snapshot
    }

    fun clear() {
        latest = null
    }
}
