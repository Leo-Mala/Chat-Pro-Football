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
    val fixtureCount: Int,
    /**
     * Tempo factual gasto no seed lazy que realmente substituiu o roster procedural antes do Room.
     * Campo separado evita atribuir Fc26SeedPlanner/asset load apenas à persistência do banco.
     */
    val factualSeedMaterializationMs: Long = 0L
)

object CareerCreationPerformanceMonitor {
    @Volatile
    var latest: CareerCreationPerformanceSnapshot? = null
        private set

    @Volatile
    private var pendingPersistedPlayerCount: Int? = null

    @Volatile
    private var pendingFactualSeedMaterializationMs: Long? = null

    /**
     * Chamado no ponto em que o seed de jogadores efetivamente usado por Room é conhecido.
     * Isso evita associar a medição ao roster procedural que pode ter sido substituído pelo FC26.
     */
    fun notePersistedPlayerCount(count: Int) {
        require(count >= 0)
        pendingPersistedPlayerCount = count
    }

    /** Registra separadamente o custo do asset/planner factual materializado lazy em saveTeams. */
    fun noteFactualSeedMaterialization(durationMs: Long) {
        require(durationMs >= 0L)
        pendingFactualSeedMaterializationMs = durationMs
    }

    fun record(snapshot: CareerCreationPerformanceSnapshot) {
        require(snapshot.databaseBootstrapMs >= 0L)
        require(snapshot.rosterMaterializationMs >= 0L)
        require(snapshot.clubSetupMs >= 0L)
        require(snapshot.competitionCalendarMs >= 0L)
        require(snapshot.persistenceMs >= 0L)
        require(snapshot.totalMs >= 0L)
        require(snapshot.totalMs >= snapshot.databaseBootstrapMs)
        require(snapshot.totalMs >= snapshot.persistenceMs)
        val persistedCount = pendingPersistedPlayerCount
        val factualSeedMaterializationMs = pendingFactualSeedMaterializationMs
        pendingPersistedPlayerCount = null
        pendingFactualSeedMaterializationMs = null
        latest = snapshot.copy(
            playerCount = persistedCount ?: snapshot.playerCount,
            factualSeedMaterializationMs = factualSeedMaterializationMs ?: snapshot.factualSeedMaterializationMs
        )
    }

    fun clear() {
        latest = null
        pendingPersistedPlayerCount = null
        pendingFactualSeedMaterializationMs = null
    }
}
