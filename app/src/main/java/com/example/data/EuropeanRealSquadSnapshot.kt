package com.example.data

/**
 * Snapshot factual de elenco usado apenas para inicializar novos saves.
 *
 * Um snapshot registra quando e de onde os dados foram conferidos. Como janelas de transferências
 * podem estar abertas, `verifiedAsOfIso` é parte do contrato e evita chamar um elenco corrente de
 * "final" sem evidência. O jogo nunca deve reaplicar este snapshot sobre um save em andamento.
 */
data class EuropeanRealSquadSnapshot(
    val country: String,
    val clubName: String,
    val domesticSeasonLabel: String,
    val verifiedAsOfIso: String,
    val sourceRefs: List<String>,
    val players: List<EuropeanRealPlayerTemplate>
) {
    init {
        val baseline = requireNotNull(EuropeanDomesticBaseline2026_27.forCountry(country)) {
            "Associação UEFA fora do baseline: $country"
        }
        require(baseline.verifiedTopFlightClubs.any { it.equals(clubName, ignoreCase = true) }) {
            "Clube fora da primeira divisão factual ${baseline.domesticSeasonLabel}: $country/$clubName"
        }
        requireNotNull(StableTeamIdentityRegistry.idFor(country, clubName)) {
            "Clube sem teamId estável: $country/$clubName"
        }
        require(domesticSeasonLabel == baseline.domesticSeasonLabel) {
            "Temporada doméstica divergente para $country/$clubName: $domesticSeasonLabel != ${baseline.domesticSeasonLabel}"
        }
        require(ISO_DATE.matches(verifiedAsOfIso)) {
            "verifiedAsOfIso deve usar YYYY-MM-DD: $verifiedAsOfIso"
        }
        require(sourceRefs.isNotEmpty() && sourceRefs.none { it.isBlank() }) {
            "Snapshot factual precisa registrar ao menos uma fonte."
        }
        val playerIds = players.map { it.stableId }
        require(playerIds.size == playerIds.distinct().size) {
            "Jogador factual duplicado no elenco de $clubName."
        }
    }

    val teamId: Long
        get() = requireNotNull(StableTeamIdentityRegistry.idFor(country, clubName))

    fun coverage(): EuropeanSquadCoverage {
        val positions = players.groupingBy { it.position }.eachCount()
        val defenders = positions.getOrDefault("ZAG", 0) + positions.getOrDefault("LAT", 0)
        val midfielders = positions.getOrDefault("VOL", 0) + positions.getOrDefault("MEI", 0)
        val forwards = positions.getOrDefault("ATA", 0)
        val goalkeepers = positions.getOrDefault("GOL", 0)

        return if (
            players.size >= MIN_GAMEPLAY_READY_PLAYERS &&
            goalkeepers >= 2 &&
            defenders >= 6 &&
            midfielders >= 5 &&
            forwards >= 3
        ) {
            EuropeanSquadCoverage.GAMEPLAY_READY_FACTUAL_SNAPSHOT
        } else {
            EuropeanSquadCoverage.PARTIAL_FACTUAL_SNAPSHOT
        }
    }

    fun toGameplayPlayers(teamRating: Int): List<Player> =
        players.map { it.toGameplayPlayer(teamId = teamId, teamRating = teamRating) }

    companion object {
        const val MIN_GAMEPLAY_READY_PLAYERS = 18
        private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}

enum class EuropeanSquadCoverage {
    PARTIAL_FACTUAL_SNAPSHOT,
    GAMEPLAY_READY_FACTUAL_SNAPSHOT
}

/**
 * Catálogo incremental. Um clube só deve entrar aqui quando seu snapshot factual tiver sido
 * transcrito e revisado. A ausência continua visível e nunca é mascarada como elenco completo.
 */
object EuropeanRealSquadCatalog {
    private val snapshots = linkedMapOf<Pair<String, String>, EuropeanRealSquadSnapshot>()

    fun register(snapshot: EuropeanRealSquadSnapshot) {
        val key = canonicalKey(snapshot.country, snapshot.clubName)
        require(snapshots.putIfAbsent(key, snapshot) == null) {
            "Snapshot duplicado para ${snapshot.country}/${snapshot.clubName}"
        }
    }

    fun find(country: String, clubName: String): EuropeanRealSquadSnapshot? =
        snapshots[canonicalKey(country, clubName)]

    fun all(): List<EuropeanRealSquadSnapshot> = snapshots.values.toList()

    fun gameplayReadyClubs(): Set<Pair<String, String>> = snapshots.values
        .filter { it.coverage() == EuropeanSquadCoverage.GAMEPLAY_READY_FACTUAL_SNAPSHOT }
        .mapTo(linkedSetOf()) { canonicalKey(it.country, it.clubName) }

    internal fun clearForTests() {
        snapshots.clear()
    }

    private fun canonicalKey(country: String, clubName: String): Pair<String, String> {
        val canonicalCountry = CountryFootballRulesRegistry.resolve(country)?.canonicalCountry ?: country
        val canonicalClub = StableTeamIdentityRegistry.canonicalNameFor(canonicalCountry, clubName) ?: clubName
        return canonicalCountry.trim().lowercase() to canonicalClub.trim().lowercase()
    }
}
