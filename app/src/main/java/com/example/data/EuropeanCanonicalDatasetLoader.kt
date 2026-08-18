package com.example.data

import android.content.res.AssetManager
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import java.io.Reader
import java.io.StringReader

data class EuropeanDatasetManifest(
    val provider: String,
    val season: String,
    val generatedAt: String,
    val countries: List<String>,
    val leagues: List<String>,
    val clubCount: Int,
    val playerCount: Int,
    val loanCount: Int,
    val validationStatus: String,
    val datasetFiles: List<String>
)

data class EuropeanCanonicalClubFact(
    val teamId: Long,
    val country: String,
    val name: String,
    val city: String,
    val stadium: String
)

data class EuropeanCanonicalDataset(
    val manifest: EuropeanDatasetManifest,
    val clubFacts: List<EuropeanCanonicalClubFact>,
    val squads: List<EuropeanRealSquadSnapshot>,
    val loans: List<EuropeanRealLoanSnapshot>
) {
    val squadCatalog: EuropeanRealSquadCatalog = EuropeanRealSquadCatalog(squads)
    val loanCatalog: EuropeanRealLoanCatalog = EuropeanRealLoanCatalog(loans)

    fun applyClubFacts(teams: List<Team>): List<Team> {
        val factsById = clubFacts.associateBy { it.teamId }
        return teams.map { team ->
            val fact = factsById[team.id] ?: return@map team
            require(team.country.equals(fact.country, ignoreCase = true)) {
                "Dataset factual aponta country divergente para teamId=${team.id}: ${team.country} vs ${fact.country}"
            }
            team.copy(
                city = fact.city.ifBlank { team.city },
                stadiumName = fact.stadium.ifBlank { team.stadiumName }
            )
        }
    }

    fun toSeedTeams(ratingResolver: (EuropeanCanonicalClubFact) -> Int = { 70 }): List<Team> =
        clubFacts.map { fact ->
            Team(
                id = fact.teamId,
                name = fact.name,
                city = fact.city,
                state = "EU",
                country = fact.country,
                division = 1,
                rating = ratingResolver(fact),
                stadiumName = fact.stadium,
                logoUrl = null
            )
        }

    fun buildSeedPlan(
        teams: List<Team>,
        proceduralRosterFactory: (Team) -> List<Player>
    ): EuropeanFactualSeedPlanner.Plan =
        EuropeanFactualSeedPlanner.build(
            teams = teams,
            squadCatalog = squadCatalog,
            loanCatalog = loanCatalog,
            proceduralRosterFactory = proceduralRosterFactory
        )
}

object EuropeanCanonicalDatasetLoader {
    const val DEFAULT_BASE_PATH = "football/europe/2026_27"
    private const val MANIFEST_FILE = "dataset_manifest.json"
    private val gson = Gson()
    private val forbiddenCanonicalKeys = setOf(
        "teamid", "playerid", "force", "finishing", "passing", "pace", "strength", "vision", "defense",
        "potential", "market_value", "salary", "rating", "ratings", "photo", "photos", "logo", "logos",
        "kit", "kits", "uniform", "uniforms", "image", "imageurl", "image_path"
    )

    fun loadValidatedFactualOrNull(
        assets: AssetManager,
        basePath: String = DEFAULT_BASE_PATH
    ): EuropeanCanonicalDataset? {
        val manifestJson = assets.open("$basePath/$MANIFEST_FILE").bufferedReader().use { it.readText() }
        val manifestDto = parseManifestDto(StringReader(manifestJson))
        if (manifestDto.validationStatus != "VALIDATED") return null
        return loadFromAssets(assets, basePath, allowFixture = false, manifestJson = manifestJson)
    }

    fun loadForTesting(
        assets: AssetManager,
        basePath: String = DEFAULT_BASE_PATH
    ): EuropeanCanonicalDataset = loadFromAssets(assets, basePath, allowFixture = true)

    internal fun loadFromStrings(
        manifestJson: String,
        datasetJsonByFile: Map<String, String>,
        allowFixture: Boolean
    ): EuropeanCanonicalDataset {
        val manifestDto = parseManifestDto(StringReader(manifestJson))
        val datasetFiles = manifestDto.datasetFiles.orEmpty().map { it.required("manifest.datasetFile") }
        val datasetDtos = datasetFiles.map { file ->
            val json = requireNotNull(datasetJsonByFile[file]) { "Dataset file ausente no teste: $file" }
            validateCanonicalJsonSafety(json)
            gson.fromJson(StringReader(json), DatasetDto::class.java) ?: error("JSON canônico vazio: $file")
        }
        return materialize(manifestDto, datasetDtos, allowFixture)
    }

    private fun loadFromAssets(
        assets: AssetManager,
        basePath: String,
        allowFixture: Boolean,
        manifestJson: String? = null
    ): EuropeanCanonicalDataset {
        val manifestText = manifestJson
            ?: assets.open("$basePath/$MANIFEST_FILE").bufferedReader().use { it.readText() }
        val manifestDto = parseManifestDto(StringReader(manifestText))
        val datasetFiles = manifestDto.datasetFiles.orEmpty().map { it.required("manifest.datasetFile") }
        val datasets = datasetFiles.map { file ->
            val json = assets.open("$basePath/$file").bufferedReader().use { it.readText() }
            validateCanonicalJsonSafety(json)
            gson.fromJson(StringReader(json), DatasetDto::class.java) ?: error("JSON canônico vazio: $file")
        }
        return materialize(manifestDto, datasets, allowFixture)
    }

    private fun parseManifestDto(reader: Reader): ManifestDto =
        requireNotNull(gson.fromJson(reader, ManifestDto::class.java)) { "dataset_manifest.json vazio" }

    private fun validateCanonicalJsonSafety(json: String) = walkJson(JsonParser.parseString(json))

    private fun walkJson(element: JsonElement) {
        when {
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                require(key.lowercase() !in forbiddenCanonicalKeys) { "Campo proibido no CANONICAL JSON: $key" }
                walkJson(value)
            }
            element.isJsonArray -> element.asJsonArray.forEach(::walkJson)
        }
    }

    private fun materialize(
        manifestDto: ManifestDto,
        datasets: List<DatasetDto>,
        allowFixture: Boolean
    ): EuropeanCanonicalDataset {
        val manifest = manifestDto.toModel()
        require(manifest.season == "2026/27") { "Temporada canônica inesperada: ${manifest.season}" }
        require(manifest.datasetFiles.isNotEmpty()) { "Manifest sem datasetFiles" }
        if (!allowFixture) {
            require(manifest.validationStatus == "VALIDATED") {
                "Dataset não validado não pode entrar em save: ${manifest.validationStatus}"
            }
        }

        val facts = mutableListOf<EuropeanCanonicalClubFact>()
        val squads = mutableListOf<EuropeanRealSquadSnapshot>()
        val loans = mutableListOf<EuropeanRealLoanSnapshot>()
        val seenTeamIds = mutableSetOf<Long>()
        val seenClubKeys = mutableSetOf<String>()
        val seenPlayerIds = mutableMapOf<Long, String>()

        datasets.forEach { dataset ->
            require(dataset.schemaVersion == 1) { "schemaVersion canônico não suportado: ${dataset.schemaVersion}" }
            require(dataset.season == manifest.season) { "Dataset/manifest com seasons divergentes" }
            require(dataset.provider == manifest.provider) { "Dataset/manifest com providers divergentes" }
            require(dataset.datasetKind == "FACTUAL" || (allowFixture && dataset.datasetKind == "FIXTURE")) {
                "datasetKind não permitido: ${dataset.datasetKind}"
            }
            requireNotNull(dataset.leagues).forEach { league ->
                val country = league.country.required("league.country")
                val leagueName = league.name.required("league.name")
                val domesticSeason = league.domesticSeasonLabel.required("league.domesticSeasonLabel")
                val verifiedAsOf = league.verifiedAsOfIso.required("league.verifiedAsOfIso")
                val sourceRefs = league.sourceRefs.orEmpty().map { it.required("sourceRef") }
                require(sourceRefs.isNotEmpty()) { "Liga $leagueName sem sourceRefs" }

                league.clubs.orEmpty().forEach { club ->
                    val clubName = club.name.required("club.name")
                    val teamId = requireNotNull(StableTeamIdentityRegistry.idFor(country, clubName)) {
                        "Clube canônico sem StableTeamIdentityRegistry: $country/$clubName"
                    }
                    require(seenTeamIds.add(teamId)) { "teamId duplicado no dataset canônico: $teamId" }
                    require(seenClubKeys.add("${country.lowercase()}|${clubName.lowercase()}")) {
                        "Clube duplicado no dataset canônico: $country/$clubName"
                    }
                    facts += EuropeanCanonicalClubFact(teamId, country, clubName, club.city.orEmpty(), club.stadium.orEmpty())

                    val templates = club.players.orEmpty().map { player ->
                        val template = player.toTemplate()
                        val previous = seenPlayerIds.putIfAbsent(template.stableId, "$country/$clubName")
                        require(previous == null) {
                            "playerId duplicado/jogador simultaneamente em dois clubes: ${template.stableId} ($previous e $country/$clubName)"
                        }
                        template
                    }
                    val snapshot = EuropeanRealSquadSnapshot(
                        country = country,
                        clubName = clubName,
                        domesticSeasonLabel = domesticSeason,
                        verifiedAsOfIso = verifiedAsOf,
                        sourceRefs = sourceRefs,
                        players = templates
                    )
                    require(snapshot.coverage() == EuropeanSquadCoverage.GAMEPLAY_READY_FACTUAL_SNAPSHOT) {
                        "Elenco canônico incompleto para $country/$clubName: ${snapshot.coverage()}"
                    }
                    squads += snapshot
                }
            }

            dataset.loans.orEmpty().forEach { loan ->
                val template = requireNotNull(loan.player) { "Empréstimo sem player" }.toTemplate()
                require(seenPlayerIds.putIfAbsent(template.stableId, "loan") == null) {
                    "Jogador de empréstimo também aparece em elenco canônico: ${template.fullName}"
                }
                loans += EuropeanRealLoanSnapshot(
                    player = template,
                    ownerCountry = loan.ownerCountry.required("loan.ownerCountry"),
                    ownerClubName = loan.ownerClubName.required("loan.ownerClubName"),
                    borrowerCountry = loan.borrowerCountry.required("loan.borrowerCountry"),
                    borrowerClubName = loan.borrowerClubName.required("loan.borrowerClubName"),
                    season = requireNotNull(loan.season) { "loan.season ausente" },
                    startWeek = requireNotNull(loan.startWeek) { "loan.startWeek ausente" },
                    durationWeeks = requireNotNull(loan.durationWeeks) { "loan.durationWeeks ausente" },
                    verifiedAsOfIso = loan.verifiedAsOfIso.required("loan.verifiedAsOfIso"),
                    sourceRefs = loan.sourceRefs.orEmpty().map { it.required("loan.sourceRef") }
                )
            }
        }

        val factIds = facts.map { it.teamId }.toSet()
        loans.forEach { loan ->
            require(loan.ownerTeamId in factIds) {
                "Empréstimo inconsistente: owner fora do dataset ${loan.ownerCountry}/${loan.ownerClubName}"
            }
            require(loan.borrowerTeamId in factIds) {
                "Empréstimo inconsistente: borrower fora do dataset ${loan.borrowerCountry}/${loan.borrowerClubName}"
            }
        }

        val actualClubCount = facts.size
        val actualLoanCount = loans.size
        val actualPlayerCount = squads.sumOf { it.players.size } + loans.size
        require(actualClubCount == manifest.clubCount) { "Manifest clubCount=${manifest.clubCount}, carregado=$actualClubCount" }
        require(actualPlayerCount == manifest.playerCount) { "Manifest playerCount=${manifest.playerCount}, carregado=$actualPlayerCount" }
        require(actualLoanCount == manifest.loanCount) { "Manifest loanCount=${manifest.loanCount}, carregado=$actualLoanCount" }
        require(manifest.countries.toSet() == facts.map { it.country }.toSet()) { "Manifest countries divergente do conteúdo" }
        val actualLeagues = datasets.flatMap { it.leagues.orEmpty() }.mapNotNull { it.name?.trim() }.toSet()
        require(manifest.leagues.toSet() == actualLeagues) { "Manifest leagues divergente do conteúdo" }

        return EuropeanCanonicalDataset(manifest, facts, squads, loans)
    }

    private fun String?.required(label: String): String {
        val value = this?.trim().orEmpty()
        require(value.isNotEmpty()) { "$label ausente" }
        return value
    }

    private fun PlayerDto.toTemplate(): EuropeanRealPlayerTemplate = EuropeanRealPlayerTemplate(
        fullName = fullName.required("player.fullName"),
        birthDateIso = birthDateIso.required("player.birthDateIso"),
        nationality = nationality.required("player.nationality"),
        position = position.required("player.position"),
        shirtNumber = shirtNumber,
        identityDisambiguator = identityDisambiguator.orEmpty()
    )

    private fun ManifestDto.toModel(): EuropeanDatasetManifest = EuropeanDatasetManifest(
        provider = provider.required("manifest.provider"),
        season = season.required("manifest.season"),
        generatedAt = generatedAt.required("manifest.generatedAt"),
        countries = countries.orEmpty().map { it.required("manifest.country") },
        leagues = leagues.orEmpty().map { it.required("manifest.league") },
        clubCount = requireNotNull(clubCount) { "manifest.clubCount ausente" },
        playerCount = requireNotNull(playerCount) { "manifest.playerCount ausente" },
        loanCount = requireNotNull(loanCount) { "manifest.loanCount ausente" },
        validationStatus = validationStatus.required("manifest.validationStatus"),
        datasetFiles = datasetFiles.orEmpty().map { it.required("manifest.datasetFile") }
    )

    private data class ManifestDto(
        var provider: String? = null, var season: String? = null, var generatedAt: String? = null,
        var countries: List<String?>? = null, var leagues: List<String?>? = null,
        var clubCount: Int? = null, var playerCount: Int? = null, var loanCount: Int? = null,
        var validationStatus: String? = null, var datasetFiles: List<String?>? = null
    )
    private data class DatasetDto(
        var schemaVersion: Int? = null, var datasetKind: String? = null, var provider: String? = null,
        var season: String? = null, var generatedAt: String? = null,
        var leagues: List<LeagueDto>? = null, var loans: List<LoanDto>? = null
    )
    private data class LeagueDto(
        var country: String? = null, var name: String? = null, var domesticSeasonLabel: String? = null,
        var verifiedAsOfIso: String? = null, var sourceRefs: List<String?>? = null, var clubs: List<ClubDto>? = null
    )
    private data class ClubDto(
        var name: String? = null, var city: String? = null, var stadium: String? = null,
        var players: List<PlayerDto>? = null
    )
    private data class PlayerDto(
        var fullName: String? = null, var birthDateIso: String? = null, var nationality: String? = null,
        var position: String? = null, var shirtNumber: Int? = null, var identityDisambiguator: String? = null
    )
    private data class LoanDto(
        var player: PlayerDto? = null, var ownerCountry: String? = null, var ownerClubName: String? = null,
        var borrowerCountry: String? = null, var borrowerClubName: String? = null,
        var season: Int? = null, var startWeek: Int? = null, var durationWeeks: Int? = null,
        var verifiedAsOfIso: String? = null, var sourceRefs: List<String?>? = null
    )
}
