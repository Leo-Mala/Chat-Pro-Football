package com.example.data

import android.content.Context
import androidx.annotation.Keep
import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okio.buffer
import okio.source
import java.io.InputStream
import java.util.Calendar

@Keep
@JsonClass(generateAdapter = true)
data class PlayerJson(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "team_id") val teamId: Long? = null,
    @Json(name = "name") val name: String,
    @Json(name = "age") val age: Int? = null,
    @Json(name = "nationality") val nationality: String? = "Brasil",
    @Json(name = "position") val position: String? = "MEI",
    @Json(name = "force") val force: Int? = 50,
    @Json(name = "energy") val energy: Int? = 100,
    @Json(name = "moral") val moral: Int? = 75,
    @Json(name = "salary") val salary: Long? = null,
    @Json(name = "contract_duration_weeks") val contractDurationWeeks: Int? = 52,
    @Json(name = "is_from_academy") val isFromAcademy: Boolean? = false,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "market_value") val marketValue: Long? = null,
    @Json(name = "min_price") val minPrice: Long? = null,
    @Json(name = "max_price") val maxPrice: Long? = null,
    @Json(name = "demand_level") val demandLevel: String? = "medium",
    @Json(name = "attributes") val attributes: PlayerAttributesJson? = null,
    @Json(name = "finishing") val finishing: Int? = null,
    @Json(name = "passing") val passing: Int? = null,
    @Json(name = "pace") val pace: Int? = null,
    @Json(name = "strength") val strength: Int? = null,
    @Json(name = "vision") val vision: Int? = null,
    @Json(name = "defense") val defense: Int? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class PlayerAttributesJson(
    @Json(name = "finishing") val finishing: Int? = 50,
    @Json(name = "passing") val passing: Int? = 50,
    @Json(name = "pace") val pace: Int? = 50,
    @Json(name = "strength") val strength: Int? = 50,
    @Json(name = "vision") val vision: Int? = 50,
    @Json(name = "defense") val defense: Int? = 50
)

@Keep
@JsonClass(generateAdapter = true)
data class TeamJson(
    @Json(name = "id") val id: Long? = null,
    @Json(name = "name") val name: String,
    @Json(name = "country") val country: String? = "Brasil",
    @Json(name = "budget") val budget: Long? = 50000000L,
    @Json(name = "reputation") val reputation: Int? = 70,
    @Json(name = "badge_url") val badgeUrl: String? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class FullJsonDatabaseResponse(
    @Json(name = "teams") val teams: List<TeamJson>? = null,
    @Json(name = "players") val players: List<PlayerJson>? = null
)

@Keep
data class ParsedJsonResult(
    val teams: List<Team> = emptyList(),
    val players: List<Player> = emptyList()
)

@Keep
@JsonClass(generateAdapter = true)
data class PlayerListJsonResponse(
    @Json(name = "players") val players: List<PlayerJson>? = null,
    @Json(name = "teams") val teams: List<TeamJson>? = null
)

@Keep
@JsonClass(generateAdapter = true)
data class ApiPlayer(
    @Json(name = "idPlayer") val idPlayer: String? = null,
    @Json(name = "idTeam") val idTeam: String? = null,
    @Json(name = "strPlayer") val strPlayer: String? = null,
    @Json(name = "strNationality") val strNationality: String? = null,
    @Json(name = "strPosition") val strPosition: String? = null,
    @Json(name = "dateBorn") val dateBorn: String? = null,
    @Json(name = "strCutout") val strCutout: String? = null,
    @Json(name = "strThumb") val strThumb: String? = null
)

fun ApiPlayer.toDomainPlayer(teamId: Long): Player {
    val calculatedAge = try {
        if (!dateBorn.isNullOrEmpty()) {
            val parts = dateBorn.split("-")
            if (parts.size == 3) {
                val birthYear = parts[0].toIntOrNull() ?: 2000
                val birthMonth = parts[1].toIntOrNull() ?: 1
                val birthDay = parts[2].toIntOrNull() ?: 1
                val today = Calendar.getInstance()
                var age = today.get(Calendar.YEAR) - birthYear
                val currentMonth = today.get(Calendar.MONTH) + 1
                val currentDay = today.get(Calendar.DAY_OF_MONTH)
                if (currentMonth < birthMonth || (currentMonth == birthMonth && currentDay < birthDay)) {
                    age--
                }
                if (age in 15..45) age else 24
            } else 24
        } else 24
    } catch (e: Exception) {
        24
    }

    val mappedPos = when {
        strPosition?.contains("Goalkeeper", ignoreCase = true) == true -> "GOL"
        strPosition?.contains("Defender", ignoreCase = true) == true -> "ZAG"
        strPosition?.contains("Midfield", ignoreCase = true) == true -> "MEI"
        strPosition?.contains("Forward", ignoreCase = true) == true || strPosition?.contains("Attacker", ignoreCase = true) == true -> "ATA"
        else -> "MEI"
    }

    val p = Player(
        id = idPlayer?.toLongOrNull() ?: 0L,
        teamId = teamId,
        name = strPlayer ?: "Jogador",
        age = calculatedAge,
        nationality = strNationality ?: "Brasil",
        position = mappedPos,
        force = 70,
        imageUrl = strCutout ?: strThumb
    )
    return p.copy(market_value = p.calculateMarketValue())
}

class PlayerMoshiAdapter {

    @FromJson
    fun fromJson(json: PlayerJson): Player {
        val finishing = json.attributes?.finishing ?: json.finishing ?: 50
        val passing = json.attributes?.passing ?: json.passing ?: 50
        val pace = json.attributes?.pace ?: json.pace ?: 50
        val strength = json.attributes?.strength ?: json.strength ?: 50
        val vision = json.attributes?.vision ?: json.vision ?: 50
        val defense = json.attributes?.defense ?: json.defense ?: 50

        val computedForce = json.force ?: ((finishing + passing + pace + strength + vision + defense) / 6)

        val player = Player(
            id = json.id ?: 0L,
            teamId = json.teamId ?: 0L,
            name = json.name,
            age = json.age ?: 24,
            nationality = json.nationality ?: "Brasil",
            position = mapPosition(json.position),
            force = computedForce.coerceIn(1, 99),
            energy = json.energy ?: 100,
            moral = json.moral ?: 75,
            salary = json.salary ?: 10000L,
            contractDurationWeeks = json.contractDurationWeeks ?: 52,
            isFromAcademy = json.isFromAcademy ?: false,
            imageUrl = json.imageUrl,
            market_value = json.marketValue ?: 0L,
            min_price = json.minPrice ?: 0L,
            max_price = json.maxPrice ?: 0L,
            demand_level = json.demandLevel ?: "medium",
            finishing = finishing,
            passing = passing,
            pace = pace,
            strength = strength,
            vision = vision,
            defense = defense
        )

        val calculatedMv = if (player.market_value == 0L) player.calculateMarketValue() else player.market_value
        val calculatedMin = if (player.min_price == 0L) (calculatedMv * 0.85).toLong().coerceAtLeast(30000L) else player.min_price
        val calculatedMax = if (player.max_price == 0L) (calculatedMv * 1.35).toLong().coerceAtLeast(50000L) else player.max_price

        return player.copy(
            market_value = calculatedMv,
            min_price = calculatedMin,
            max_price = calculatedMax
        )
    }

    @ToJson
    fun toJson(player: Player): PlayerJson {
        return PlayerJson(
            id = player.id,
            teamId = player.teamId,
            name = player.name,
            age = player.age,
            nationality = player.nationality,
            position = player.position,
            force = player.force,
            energy = player.energy,
            moral = player.moral,
            salary = player.salary,
            contractDurationWeeks = player.contractDurationWeeks,
            isFromAcademy = player.isFromAcademy,
            imageUrl = player.imageUrl,
            marketValue = player.market_value,
            minPrice = player.min_price,
            maxPrice = player.max_price,
            demandLevel = player.demand_level,
            attributes = PlayerAttributesJson(
                finishing = player.finishing,
                passing = player.passing,
                pace = player.pace,
                strength = player.strength,
                vision = player.vision,
                defense = player.defense
            )
        )
    }

    private fun mapPosition(pos: String?): String {
        if (pos.isNullOrEmpty()) return "MEI"
        return when (pos.uppercase().trim()) {
            "GK", "GOALKEEPER", "GOLEIRO", "GOL" -> "GOL"
            "CB", "DEFENDER", "ZAGUEIRO", "ZAG" -> "ZAG"
            "LB", "RB", "LWB", "RWB", "LATERAL", "LAT" -> "LAT"
            "DM", "CDM", "VOLANTE", "VOL" -> "VOL"
            "CM", "MC", "AM", "CAM", "MIDFIELDER", "MEIA", "MEI", "RM", "LM" -> "MEI"
            "ST", "CF", "RW", "LW", "FW", "FORWARD", "ATTACKER", "ATACANTE", "ATA" -> "ATA"
            else -> pos.uppercase().take(3)
        }
    }
}

class PlayerDataParser(private val moshi: Moshi = defaultMoshi) {

    companion object {
        val defaultMoshi: Moshi by lazy {
            Moshi.Builder()
                .add(PlayerMoshiAdapter())
                .addLast(KotlinJsonAdapterFactory())
                .build()
        }
    }

    private val playerAdapter: JsonAdapter<Player> = moshi.adapter(Player::class.java)
    private val playerJsonAdapter: JsonAdapter<PlayerJson> = moshi.adapter(PlayerJson::class.java)
    private val playerListType = Types.newParameterizedType(List::class.java, Player::class.java)
    private val playerListAdapter: JsonAdapter<List<Player>> = moshi.adapter(playerListType)
    private val playerListJsonResponseAdapter: JsonAdapter<PlayerListJsonResponse> = moshi.adapter(PlayerListJsonResponse::class.java)

    private val teamJsonAdapter: JsonAdapter<TeamJson> = moshi.adapter(TeamJson::class.java)

    /**
     * Stream-based JSON parsing using JsonReader to process arbitrary size files (e.g. 18,000+ players)
     * token-by-token without loading entire JSON strings or object DOM trees into memory at once, preventing OOM crashes.
     */
    fun parseFullDatabaseStream(inputStream: InputStream): ParsedJsonResult {
        val teams = mutableListOf<Team>()
        val players = mutableListOf<Player>()
        val playerMoshiAdapter = PlayerMoshiAdapter()

        val source = inputStream.source().buffer()
        val reader = JsonReader.of(source)

        fun parsePlayerItem(r: JsonReader) {
            runCatching {
                val playerJson = playerJsonAdapter.fromJson(r)
                if (playerJson != null) {
                    val p = playerMoshiAdapter.fromJson(playerJson)
                    players.add(p)
                }
            }.onFailure {
                runCatching { r.skipValue() }
            }
        }

        fun parseTeamItem(r: JsonReader) {
            runCatching {
                val teamJson = teamJsonAdapter.fromJson(r)
                if (teamJson != null) {
                    teams.add(
                        Team(
                            id = teamJson.id ?: 0L,
                            name = teamJson.name,
                            city = "Cidade",
                            state = "UF",
                            country = teamJson.country ?: "Brasil",
                            division = 1,
                            rating = teamJson.reputation ?: 70,
                            logoUrl = teamJson.badgeUrl
                        )
                    )
                }
            }.onFailure {
                runCatching { r.skipValue() }
            }
        }

        try {
            reader.use { r ->
                when (r.peek()) {
                    JsonReader.Token.BEGIN_OBJECT -> {
                        r.beginObject()
                        while (r.hasNext()) {
                            val name = r.nextName().lowercase()
                            when (name) {
                                "players", "player", "data" -> {
                                    if (r.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                        r.beginArray()
                                        while (r.hasNext()) {
                                            parsePlayerItem(r)
                                        }
                                        r.endArray()
                                    } else if (r.peek() == JsonReader.Token.BEGIN_OBJECT) {
                                        parsePlayerItem(r)
                                    } else {
                                        r.skipValue()
                                    }
                                }
                                "teams", "team" -> {
                                    if (r.peek() == JsonReader.Token.BEGIN_ARRAY) {
                                        r.beginArray()
                                        while (r.hasNext()) {
                                            parseTeamItem(r)
                                        }
                                        r.endArray()
                                    } else if (r.peek() == JsonReader.Token.BEGIN_OBJECT) {
                                        parseTeamItem(r)
                                    } else {
                                        r.skipValue()
                                    }
                                }
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                    }
                    JsonReader.Token.BEGIN_ARRAY -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            parsePlayerItem(r)
                        }
                        r.endArray()
                    }
                    else -> {
                        parsePlayerItem(r)
                    }
                }
            }
        } catch (_: Exception) {
        }

        return ParsedJsonResult(teams = teams, players = players)
    }

    /**
     * Parse full database JSON string using stream-based JsonReader.
     */
    fun parseFullDatabase(jsonString: String): ParsedJsonResult {
        if (jsonString.isBlank()) return ParsedJsonResult()
        return parseFullDatabaseStream(jsonString.byteInputStream(Charsets.UTF_8))
    }

    /**
     * Deserialize a JSON string representing a single player into a domain [Player] object.
     */
    fun parsePlayer(jsonString: String): Player? {
        return runCatching { playerAdapter.fromJson(jsonString) }.getOrNull()
            ?: runCatching {
                val jsonDto = playerJsonAdapter.fromJson(jsonString)
                jsonDto?.let { PlayerMoshiAdapter().fromJson(it) }
            }.getOrNull()
    }

    /**
     * Deserialize a JSON string representing a list of players into domain [Player] objects.
     */
    fun parsePlayerList(jsonString: String): List<Player> {
        return runCatching {
            playerListAdapter.fromJson(jsonString)
        }.getOrNull()
            ?: runCatching {
                playerListJsonResponseAdapter.fromJson(jsonString)?.players?.map {
                    PlayerMoshiAdapter().fromJson(it)
                }
            }.getOrNull()
            ?: emptyList()
    }

    /**
     * Load players directly from an asset JSON file (e.g., "sample_players.json") using streaming.
     */
    fun loadPlayersFromAssets(context: Context, fileName: String = "sample_players.json"): List<Player> {
        return try {
            context.assets.open(fileName).use { inputStream ->
                parseFullDatabaseStream(inputStream).players
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Serialize a [Player] domain object to JSON string using Moshi.
     */
    fun toJson(player: Player): String {
        return playerAdapter.toJson(player)
    }

    /**
     * Serialize a list of [Player] domain objects to JSON string using Moshi.
     */
    fun toJson(players: List<Player>): String {
        return playerListAdapter.toJson(players)
    }
}
