package com.example.data

import com.google.gson.Gson
import kotlin.math.roundToLong

data class Fc26MoneyManifest(
    val sourceCurrency: String,
    val gameCurrency: String,
    val eurToBrl: Double,
    val referenceDate: String,
    val referenceSource: String
)

data class Fc26DatasetManifest(
    val schemaVersion: Int,
    val datasetSource: String,
    val datasetVersion: String,
    val sourceFile: String,
    val sourceSha256: String,
    val assetFile: String,
    val assetSha256: String,
    val playerCount: Int,
    val clubCount: Int,
    val leagueCount: Int,
    val nationalityCount: Int,
    val freeAgentCount: Int,
    val loanedPlayerCount: Int,
    val validationStatus: String,
    val money: Fc26MoneyManifest
)

data class Fc26NormalizedPlayer(
    val sourcePlayerId: Long,
    val shortName: String,
    val fullName: String,
    val sourceAge: Int,
    val birthDateIso: String,
    val heightCm: Int,
    val weightKg: Int,
    val nationality: String,
    val positions: List<String>,
    val overall: Int,
    val potential: Int,
    val valueEur: Long,
    val wageEur: Long,
    val leagueId: Long?,
    val leagueName: String?,
    val clubTeamId: Long?,
    val clubName: String?,
    val clubPosition: String?,
    val clubLoanedFrom: String?,
    val contractUntilYear: Int?,
    val preferredFoot: String,
    val weakFoot: Int,
    val skillMoves: Int,
    val internationalReputation: Int,
    val workRate: String,
    val releaseClauseEur: Long,
    val summaryPace: Int?,
    val summaryShooting: Int?,
    val summaryPassing: Int?,
    val summaryDribbling: Int?,
    val summaryDefending: Int?,
    val summaryPhysic: Int?,
    val atributos: Atributos
) {
    init {
        require(sourcePlayerId > 0L) { "FC26 player_id deve ser positivo." }
        require(fullName.isNotBlank()) { "FC26 long_name não pode ser vazio." }
        require(nationality.isNotBlank()) { "FC26 nationality_name não pode ser vazio." }
        require(positions.isNotEmpty()) { "FC26 player_positions não pode ser vazio: $fullName" }
        require(overall in 1..99) { "FC26 overall inválido para $fullName: $overall" }
        require(potential in 1..99) { "FC26 potential inválido para $fullName: $potential" }
        RealPlayerIdentityKey(fullName, birthDateIso)
    }

    // These values are pure functions of immutable constructor fields. Persist them once per
    // normalized player so club sorting and mapping do not repeatedly redo Unicode normalization,
    // regex cleanup and ISO-date validation while preserving the exact same factual identity.
    val stableId: Long = StableRealPlayerIdentity.idFor(fullName, birthDateIso)
    val primaryPosition: String = positions.first()
    val alternativePositions: List<String> = positions.drop(1)
}

data class Fc26Dataset(
    val manifest: Fc26DatasetManifest,
    val players: List<Fc26NormalizedPlayer>
) {
    init {
        require(players.size == manifest.playerCount) {
            "FC26 manifest/playerCount divergente: ${manifest.playerCount} != ${players.size}"
        }
        require(players.map { it.sourcePlayerId }.distinct().size == players.size) {
            "FC26 contém sourcePlayerId duplicado."
        }
        require(players.map { it.stableId }.distinct().size == players.size) {
            "FC26 contém colisão no namespace StableRealPlayerIdentity."
        }
    }

    val sourceClubs: List<Fc26SourceClub> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        players.asSequence()
            .filter { it.clubTeamId != null && !it.clubName.isNullOrBlank() }
            .groupBy { requireNotNull(it.clubTeamId) }
            .map { (sourceClubTeamId, clubPlayers) ->
                val names = clubPlayers.mapNotNull { it.clubName?.trim() }.distinct()
                require(names.size == 1) {
                    "FC26 club_team_id=$sourceClubTeamId possui nomes divergentes: $names"
                }
                val leagueIds = clubPlayers.mapNotNull { it.leagueId }.distinct()
                val leagueNames = clubPlayers.mapNotNull { it.leagueName?.trim() }.filter { it.isNotBlank() }.distinct()
                Fc26SourceClub(
                    sourceClubTeamId = sourceClubTeamId,
                    clubName = names.single(),
                    leagueId = leagueIds.singleOrNull(),
                    leagueName = leagueNames.singleOrNull(),
                    players = clubPlayers.sortedWith(compareByDescending<Fc26NormalizedPlayer> { it.overall }.thenBy { it.stableId })
                )
            }
            .sortedBy { it.sourceClubTeamId }
    }

    val freeAgents: List<Fc26NormalizedPlayer> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        players.filter { it.clubTeamId == null }.sortedWith(compareByDescending<Fc26NormalizedPlayer> { it.overall }.thenBy { it.stableId })
    }
}

data class Fc26SourceClub(
    val sourceClubTeamId: Long,
    val clubName: String,
    val leagueId: Long?,
    val leagueName: String?,
    val players: List<Fc26NormalizedPlayer>
)

object Fc26MoneyPolicy {
    const val SOURCE_CURRENCY = "EUR"
    const val GAME_CURRENCY = "BRL"
    const val EUR_TO_BRL = 6.2567
    const val REFERENCE_DATE = "2025-09-19"

    fun requireCompatible(manifest: Fc26DatasetManifest) {
        require(manifest.money.sourceCurrency == SOURCE_CURRENCY) {
            "Moeda de origem FC26 inesperada: ${manifest.money.sourceCurrency}"
        }
        require(manifest.money.gameCurrency == GAME_CURRENCY) {
            "Moeda de destino FC26 inesperada: ${manifest.money.gameCurrency}"
        }
        require(kotlin.math.abs(manifest.money.eurToBrl - EUR_TO_BRL) < 0.000001) {
            "Taxa EUR/BRL do asset não corresponde à política runtime."
        }
        require(manifest.money.referenceDate == REFERENCE_DATE) {
            "Data de referência monetária inesperada: ${manifest.money.referenceDate}"
        }
    }

    fun eurToGameCurrency(valueEur: Long): Long {
        if (valueEur <= 0L) return 0L
        return (valueEur.toDouble() * EUR_TO_BRL).roundToLong().coerceAtLeast(1L)
    }
}

object Fc26PositionMapper {
    private val supported = setOf("GK", "CB", "LB", "RB", "LWB", "RWB", "CDM", "CM", "CAM", "LM", "RM", "LW", "RW", "CF", "ST")

    fun simplified(positions: List<String>): String {
        val primary = positions.firstOrNull()?.uppercase()?.trim().orEmpty()
        require(primary in supported) { "Posição FC26 principal não suportada: '$primary'" }
        return when (primary) {
            "GK" -> "GOL"
            "CB" -> "ZAG"
            "LB", "RB", "LWB", "RWB" -> "LAT"
            "CDM" -> "VOL"
            "CM", "CAM", "LM", "RM" -> "MEI"
            "LW", "RW", "CF", "ST" -> "ATA"
            else -> error("Posição FC26 sem mapeamento: $primary")
        }
    }
}

object Fc26ImportMetadata {
    private val gson = Gson()

    /**
     * `Player.atributos` continua sendo a source of truth dos 35 atributos de gameplay. O JSON
     * legado é usado como envelope persistido para preservar identidade externa e campos FC26 sem
     * exigir uma migração Room somente por metadados. Os atributos também ficam no topo do JSON,
     * mantendo compatibilidade com `AtributosConverter` se algum save legado precisar do fallback.
     */
    fun toJson(player: Fc26NormalizedPlayer): String {
        val a = player.atributos
        val root = linkedMapOf<String, Any?>(
            "reflexos" to a.reflexos,
            "pegada" to a.pegada,
            "umContraUm" to a.umContraUm,
            "saidaDeGol" to a.saidaDeGol,
            "lancamento" to a.lancamento,
            "desarme" to a.desarme,
            "marcacao" to a.marcacao,
            "cabeceio" to a.cabeceio,
            "passeCurto" to a.passeCurto,
            "cruzamento" to a.cruzamento,
            "drible" to a.drible,
            "passe" to a.passe,
            "primeiroToque" to a.primeiroToque,
            "finalizacao" to a.finalizacao,
            "chuteDeLonge" to a.chuteDeLonge,
            "controleBola" to a.controleBola,
            "posicionamento" to a.posicionamento,
            "concentracao" to a.concentracao,
            "sangueFrio" to a.sangueFrio,
            "antecipacao" to a.antecipacao,
            "bravura" to a.bravura,
            "trabalhoEquipe" to a.trabalhoEquipe,
            "decisao" to a.decisao,
            "semBola" to a.semBola,
            "visaoJogo" to a.visaoJogo,
            "criatividade" to a.criatividade,
            "agressividade" to a.agressividade,
            "lideranca" to a.lideranca,
            "regularidade" to a.regularidade,
            "agilidade" to a.agilidade,
            "impulsao" to a.impulsao,
            "forca" to a.forca,
            "velocidade" to a.velocidade,
            "aceleracao" to a.aceleracao,
            "resistencia" to a.resistencia,
            "import" to linkedMapOf(
                "source" to "FC26",
                "sourcePlayerId" to player.sourcePlayerId,
                "datasetVersion" to "2025-09-19",
                "shortName" to player.shortName,
                "sourceAge" to player.sourceAge,
                "birthDateIso" to player.birthDateIso,
                "heightCm" to player.heightCm,
                "weightKg" to player.weightKg,
                "preferredFoot" to player.preferredFoot,
                "primaryPosition" to player.primaryPosition,
                "alternativePositions" to player.alternativePositions,
                "leagueId" to player.leagueId,
                "leagueName" to player.leagueName,
                "sourceClubTeamId" to player.clubTeamId,
                "sourceClubName" to player.clubName,
                "clubPosition" to player.clubPosition,
                "clubLoanedFrom" to player.clubLoanedFrom,
                "valueEur" to player.valueEur,
                "wageEur" to player.wageEur,
                "releaseClauseEur" to player.releaseClauseEur,
                "weakFoot" to player.weakFoot,
                "skillMoves" to player.skillMoves,
                "internationalReputation" to player.internationalReputation,
                "workRate" to player.workRate
            )
        )
        return gson.toJson(root)
    }
}
