package com.example.data

import android.content.res.AssetManager
import com.google.gson.Gson
import java.io.FileNotFoundException
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * Carrega o snapshot FC26 já normalizado no build. O CSV de 10+ MB nunca é lido pelo jogo.
 *
 * O manifest e o SHA-256 protegem o runtime contra assets truncados ou trocados sem regeneração.
 * A validação completa do CSV continua pertencendo ao pipeline tools/fc26.
 */
object Fc26NormalizedDatasetLoader {
    const val DEFAULT_BASE_PATH = "football/fc26"
    const val MANIFEST_FILE = "fc26_manifest.json"
    private val gson = Gson()

    private val requiredColumns = setOf(
        "source_player_id", "short_name", "full_name", "source_age", "dob", "height_cm", "weight_kg",
        "nationality", "positions", "overall", "potential", "value_eur", "wage_eur", "league_id",
        "league_name", "club_team_id", "club_name", "club_position", "club_loaned_from",
        "contract_until_year", "preferred_foot", "weak_foot", "skill_moves", "international_reputation",
        "work_rate", "release_clause_eur", "summary_pace", "summary_shooting", "summary_passing",
        "summary_dribbling", "summary_defending", "summary_physic",
        "reflexos", "pegada", "um_contra_um", "saida_de_gol", "lancamento", "desarme", "marcacao",
        "cabeceio", "passe_curto", "cruzamento", "drible", "passe", "primeiro_toque", "finalizacao",
        "chute_de_longe", "controle_bola", "posicionamento", "concentracao", "sangue_frio",
        "antecipacao", "bravura", "trabalho_equipe", "decisao", "sem_bola", "visao_jogo",
        "criatividade", "agressividade", "lideranca", "regularidade", "agilidade", "impulsao", "forca",
        "velocidade", "aceleracao", "resistencia"
    )

    /** Asset ausente mantém compatibilidade com o seed anterior; asset presente porém corrompido falha fechado. */
    fun loadValidatedOrNull(
        assets: AssetManager,
        basePath: String = DEFAULT_BASE_PATH
    ): Fc26Dataset? {
        val manifestText = try {
            assets.open("$basePath/$MANIFEST_FILE").bufferedReader().use { it.readText() }
        } catch (_: FileNotFoundException) {
            return null
        }
        val manifest = requireNotNull(gson.fromJson(manifestText, Fc26DatasetManifest::class.java)) {
            "FC26 manifest vazio."
        }
        if (manifest.validationStatus != "VALIDATED") return null
        return load(assets, basePath, manifest)
    }

    internal fun load(
        assets: AssetManager,
        basePath: String,
        manifest: Fc26DatasetManifest
    ): Fc26Dataset {
        require(manifest.schemaVersion == 1) { "FC26 schemaVersion não suportado: ${manifest.schemaVersion}" }
        require(manifest.datasetSource == "FC26") { "FC26 datasetSource inesperado: ${manifest.datasetSource}" }
        require(manifest.datasetVersion.isNotBlank()) { "FC26 datasetVersion vazio." }
        require(manifest.playerCount > 0) { "FC26 manifest sem jogadores." }
        require(manifest.assetFile.endsWith(".tsv.gz")) { "FC26 assetFile inválido: ${manifest.assetFile}" }
        Fc26MoneyPolicy.requireCompatible(manifest)

        val assetPath = "$basePath/${manifest.assetFile}"
        val actualSha = assets.open(assetPath).use(::sha256Hex)
        require(actualSha.equals(manifest.assetSha256, ignoreCase = true)) {
            "FC26 asset SHA-256 divergente: manifest=${manifest.assetSha256}, actual=$actualSha"
        }

        val players = assets.open(assetPath).use { raw ->
            GZIPInputStream(raw).bufferedReader(Charsets.UTF_8).use(::parsePlayers)
        }
        return Fc26Dataset(manifest, players)
    }

    internal fun parsePlayers(reader: java.io.BufferedReader): List<Fc26NormalizedPlayer> {
        val headerLine = reader.readLine() ?: error("FC26 TSV vazio.")
        val headers = headerLine.split('\t')
        require(headers.toSet().containsAll(requiredColumns)) {
            "FC26 TSV sem colunas obrigatórias: ${(requiredColumns - headers.toSet()).sorted()}"
        }
        val index = headers.withIndex().associate { it.value to it.index }

        fun Array<String>.text(key: String): String = get(index.getValue(key)).trim()
        fun Array<String>.long(key: String): Long = text(key).toLongOrNull()
            ?: throw IllegalArgumentException("FC26 $key inválido: '${text(key)}'")
        fun Array<String>.int(key: String): Int = text(key).toIntOrNull()
            ?: throw IllegalArgumentException("FC26 $key inválido: '${text(key)}'")
        fun Array<String>.nullableLong(key: String): Long? = long(key).takeIf { it > 0L }
        fun Array<String>.nullableInt(key: String): Int? = int(key).takeIf { it > 0 }
        fun Array<String>.nullableText(key: String): String? = text(key).takeIf { it.isNotBlank() }
        fun Array<String>.rating(key: String): Int = int(key).also { value ->
            require(value in 1..99) { "FC26 $key fora de 1..99: $value" }
        }

        val result = ArrayList<Fc26NormalizedPlayer>(18_500)
        var lineNumber = 1
        reader.forEachLine { line ->
            lineNumber += 1
            if (line.isBlank()) return@forEachLine
            val cells = line.split('\t').toTypedArray()
            require(cells.size == headers.size) {
                "FC26 TSV linha $lineNumber possui ${cells.size} colunas; esperado ${headers.size}."
            }
            val positions = cells.text("positions").split(',').map { it.trim().uppercase() }.filter { it.isNotEmpty() }
            result += Fc26NormalizedPlayer(
                sourcePlayerId = cells.long("source_player_id"),
                shortName = cells.text("short_name"),
                fullName = cells.text("full_name"),
                sourceAge = cells.int("source_age"),
                birthDateIso = cells.text("dob"),
                heightCm = cells.int("height_cm"),
                weightKg = cells.int("weight_kg"),
                nationality = cells.text("nationality"),
                positions = positions,
                overall = cells.rating("overall"),
                potential = cells.rating("potential"),
                valueEur = cells.long("value_eur"),
                wageEur = cells.long("wage_eur"),
                leagueId = cells.nullableLong("league_id"),
                leagueName = cells.nullableText("league_name"),
                clubTeamId = cells.nullableLong("club_team_id"),
                clubName = cells.nullableText("club_name"),
                clubPosition = cells.nullableText("club_position"),
                clubLoanedFrom = cells.nullableText("club_loaned_from"),
                contractUntilYear = cells.nullableInt("contract_until_year"),
                preferredFoot = cells.text("preferred_foot"),
                weakFoot = cells.int("weak_foot"),
                skillMoves = cells.int("skill_moves"),
                internationalReputation = cells.int("international_reputation"),
                workRate = cells.text("work_rate"),
                releaseClauseEur = cells.long("release_clause_eur"),
                summaryPace = cells.nullableInt("summary_pace"),
                summaryShooting = cells.nullableInt("summary_shooting"),
                summaryPassing = cells.nullableInt("summary_passing"),
                summaryDribbling = cells.nullableInt("summary_dribbling"),
                summaryDefending = cells.nullableInt("summary_defending"),
                summaryPhysic = cells.nullableInt("summary_physic"),
                atributos = Atributos(
                    reflexos = cells.rating("reflexos"),
                    pegada = cells.rating("pegada"),
                    umContraUm = cells.rating("um_contra_um"),
                    saidaDeGol = cells.rating("saida_de_gol"),
                    lancamento = cells.rating("lancamento"),
                    desarme = cells.rating("desarme"),
                    marcacao = cells.rating("marcacao"),
                    cabeceio = cells.rating("cabeceio"),
                    passeCurto = cells.rating("passe_curto"),
                    cruzamento = cells.rating("cruzamento"),
                    drible = cells.rating("drible"),
                    passe = cells.rating("passe"),
                    primeiroToque = cells.rating("primeiro_toque"),
                    finalizacao = cells.rating("finalizacao"),
                    chuteDeLonge = cells.rating("chute_de_longe"),
                    controleBola = cells.rating("controle_bola"),
                    posicionamento = cells.rating("posicionamento"),
                    concentracao = cells.rating("concentracao"),
                    sangueFrio = cells.rating("sangue_frio"),
                    antecipacao = cells.rating("antecipacao"),
                    bravura = cells.rating("bravura"),
                    trabalhoEquipe = cells.rating("trabalho_equipe"),
                    decisao = cells.rating("decisao"),
                    semBola = cells.rating("sem_bola"),
                    visaoJogo = cells.rating("visao_jogo"),
                    criatividade = cells.rating("criatividade"),
                    agressividade = cells.rating("agressividade"),
                    lideranca = cells.rating("lideranca"),
                    regularidade = cells.rating("regularidade"),
                    agilidade = cells.rating("agilidade"),
                    impulsao = cells.rating("impulsao"),
                    forca = cells.rating("forca"),
                    velocidade = cells.rating("velocidade"),
                    aceleracao = cells.rating("aceleracao"),
                    resistencia = cells.rating("resistencia")
                )
            )
        }
        return result
    }

    private fun sha256Hex(input: java.io.InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Inicializado uma vez no Application; o dataset só é materializado quando um novo save é preparado. */
object Fc26FactualAssetRuntime {
    @Volatile
    private var assetManager: AssetManager? = null

    fun initialize(assets: AssetManager) {
        assetManager = assets
    }

    fun loadValidatedOrNull(): Fc26Dataset? =
        assetManager?.let(Fc26NormalizedDatasetLoader::loadValidatedOrNull)

    internal fun clearForTesting() {
        assetManager = null
    }
}
