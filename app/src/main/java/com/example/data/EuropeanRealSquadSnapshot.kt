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
        parseStrictIsoDate(verifiedAsOfIso, "verifiedAsOfIso")
        require(sourceRefs.isNotEmpty() && sourceRefs.none { it.isBlank() }) {
            "Snapshot factual precisa registrar ao menos uma fonte."
        }
        val playerIds = players.map { it.stableId }
        require(playerIds.size == playerIds.distinct().size) {
            "Jogador factual duplicado no elenco de $clubName."
        }
        val assignedShirtNumbers = players.mapNotNull { it.shirtNumber }
        require(assignedShirtNumbers.size == assignedShirtNumbers.distinct().size) {
            "Número de camisa factual duplicado no elenco de $clubName."
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
    }
}

enum class EuropeanSquadCoverage {
    PARTIAL_FACTUAL_SNAPSHOT,
    GAMEPLAY_READY_FACTUAL_SNAPSHOT
}

/**
 * Catálogo imutável de snapshots factuais. Os lotes por país são concatenados na construção;
 * nenhuma execução de save pode registrar/alterar dados globais em runtime.
 *
 * IMPORTANTE: aliases do StableTeamIdentityRegistry não são aceitos para localizar snapshots.
 * Um clube procedural pode, por coincidência, receber um nome que seja alias histórico de um clube
 * real (ex.: "Liverpool"). Esse placeholder não pode herdar o elenco factual de "Liverpool FC".
 * Só o nome canônico factual, dentro do país correto, entra neste catálogo.
 */
class EuropeanRealSquadCatalog(
    snapshots: List<EuropeanRealSquadSnapshot>
) {
    private val snapshotsByClub: Map<Pair<String, String>, EuropeanRealSquadSnapshot>

    init {
        val entries = snapshots.map { snapshot ->
            canonicalKey(snapshot.country, snapshot.clubName) to snapshot
        }
        require(entries.map { it.first }.distinct().size == entries.size) {
            "Há snapshots duplicados para o mesmo clube factual."
        }

        val globalPlayerIds = snapshots.flatMap { it.players }.map { it.stableId }
        require(globalPlayerIds.distinct().size == globalPlayerIds.size) {
            "Um jogador factual aparece simultaneamente em mais de um snapshot de clube."
        }

        snapshotsByClub = entries.toMap()
    }

    fun find(country: String, clubName: String): EuropeanRealSquadSnapshot? =
        snapshotsByClub[exactLookupKey(country, clubName)]

    fun all(): List<EuropeanRealSquadSnapshot> = snapshotsByClub.values.toList()

    fun gameplayReadyClubs(): Set<Pair<String, String>> = snapshotsByClub
        .filterValues { it.coverage() == EuropeanSquadCoverage.GAMEPLAY_READY_FACTUAL_SNAPSHOT }
        .keys

    fun missingTopFlightClubs(): Set<Pair<String, String>> {
        val expected = EuropeanDomesticBaseline2026_27.associations.flatMap { baseline ->
            baseline.verifiedTopFlightClubs.map { club -> canonicalKey(baseline.country, club) }
        }.toSet()
        return expected - snapshotsByClub.keys
    }

    private fun exactLookupKey(country: String, clubName: String): Pair<String, String> {
        val canonicalCountry = CountryFootballRulesRegistry.resolve(country)?.canonicalCountry ?: country
        return canonicalCountry.trim().lowercase() to clubName.trim().lowercase()
    }

    private fun canonicalKey(country: String, clubName: String): Pair<String, String> {
        val canonicalCountry = CountryFootballRulesRegistry.resolve(country)?.canonicalCountry ?: country
        val canonicalClub = StableTeamIdentityRegistry.canonicalNameFor(canonicalCountry, clubName) ?: clubName
        return canonicalCountry.trim().lowercase() to canonicalClub.trim().lowercase()
    }
}

/**
 * Source of truth incremental do seed factual legado. O loader canônico dos assets `FACTUAL`
 * constrói seu próprio catálogo para novos saves; este objeto permanece para testes/compatibilidade.
 */
object EuropeanRealSquads {
    val catalog = EuropeanRealSquadCatalog(
        listOf(
            ManchesterUnitedSquad2026_27.snapshot
        )
    )
}
