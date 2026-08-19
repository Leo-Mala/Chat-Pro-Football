package com.example.data

import java.text.Normalizer
import java.util.Locale

enum class Fc26ClubMatchStatus { MATCHED, UNMATCHED, AMBIGUOUS }

data class Fc26ClubMatch(
    val sourceClubTeamId: Long,
    val sourceClubName: String,
    val leagueId: Long?,
    val leagueName: String?,
    val playerCount: Int,
    val status: Fc26ClubMatchStatus,
    val targetTeamId: Long? = null,
    val targetTeamName: String? = null,
    val reason: String
)

/**
 * Matching conservador: exact/alias primeiro; depois uma variante que remove apenas designadores
 * de clube (FC/AFC/CF/SC/AC). Não existe fuzzy distance e nenhum candidato múltiplo é escolhido.
 */
object Fc26ClubMatcher {
    private val clubDesignators = setOf("fc", "afc", "cf", "sc", "ac")

    /** Overrides pequenos e auditáveis para grafias conhecidas; chave/valor passam pela normalização. */
    private val explicitAliases = mapOf(
        "atletico madrid" to "atletico de madrid",
        "paris saint germain" to "paris saint germain",
        "inter" to "internazionale",
        "inter milan" to "internazionale",
        "bayern munich" to "bayern munchen",
        "borussia monchengladbach" to "borussia monchengladbach"
    ).mapKeys { normalize(it.key) }.mapValues { normalize(it.value) }

    fun match(dataset: Fc26Dataset, teams: List<Team>): List<Fc26ClubMatch> {
        val targets = teams.map { team -> Target(team, targetNames(team)) }
        val sourceCoreFrequency = dataset.sourceClubs.groupingBy { core(it.clubName) }.eachCount()

        return dataset.sourceClubs.map { source ->
            val exactKey = normalize(source.clubName)
            val explicitTarget = explicitAliases[exactKey]
            val exactCandidates = targets.filter { target ->
                target.names.any { it == exactKey || (explicitTarget != null && it == explicitTarget) }
            }

            when {
                exactCandidates.size == 1 -> exactCandidates.single().toMatch(source, "exact/explicit alias")
                exactCandidates.size > 1 -> ambiguous(source, exactCandidates, "multiple exact/alias targets")
                else -> {
                    val sourceCore = core(source.clubName)
                    if (sourceCoreFrequency.getValue(sourceCore) > 1) {
                        unresolved(source, "source club name is non-unique after conservative normalization")
                    } else {
                        val coreCandidates = targets.filter { target -> target.names.any { core(it) == sourceCore } }
                        when (coreCandidates.size) {
                            1 -> coreCandidates.single().toMatch(source, "unique designator-normalized match")
                            0 -> unresolved(source, "no safe target club match")
                            else -> ambiguous(source, coreCandidates, "multiple designator-normalized targets")
                        }
                    }
                }
            }
        }
    }

    private data class Target(val team: Team, val names: Set<String>) {
        fun toMatch(source: Fc26SourceClub, reason: String) = Fc26ClubMatch(
            sourceClubTeamId = source.sourceClubTeamId,
            sourceClubName = source.clubName,
            leagueId = source.leagueId,
            leagueName = source.leagueName,
            playerCount = source.players.size,
            status = Fc26ClubMatchStatus.MATCHED,
            targetTeamId = team.id,
            targetTeamName = team.name,
            reason = reason
        )
    }

    private fun targetNames(team: Team): Set<String> = buildSet {
        add(normalize(team.name))
        StableTeamIdentityRegistry.identityForId(team.id)?.let { stable ->
            add(normalize(stable.canonicalName))
            stable.aliases.forEach { add(normalize(it)) }
        }
    }

    private fun unresolved(source: Fc26SourceClub, reason: String) = Fc26ClubMatch(
        sourceClubTeamId = source.sourceClubTeamId,
        sourceClubName = source.clubName,
        leagueId = source.leagueId,
        leagueName = source.leagueName,
        playerCount = source.players.size,
        status = Fc26ClubMatchStatus.UNMATCHED,
        reason = reason
    )

    private fun ambiguous(source: Fc26SourceClub, candidates: List<Target>, reason: String) = Fc26ClubMatch(
        sourceClubTeamId = source.sourceClubTeamId,
        sourceClubName = source.clubName,
        leagueId = source.leagueId,
        leagueName = source.leagueName,
        playerCount = source.players.size,
        status = Fc26ClubMatchStatus.AMBIGUOUS,
        reason = "$reason: ${candidates.joinToString { "${it.team.id}/${it.team.name}" }}"
    )

    internal fun normalize(value: String): String {
        val transliterated = value.trim()
            .replace("Ø", "O").replace("ø", "o")
            .replace("Ł", "L").replace("ł", "l")
            .replace("Đ", "D").replace("đ", "d")
            .replace("Æ", "Ae").replace("æ", "ae")
            .replace("Œ", "Oe").replace("œ", "oe")
            .replace("ß", "ss").replace("ı", "i")
        return Normalizer.normalize(transliterated, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    internal fun core(value: String): String = normalize(value)
        .split(' ')
        .filter { it.isNotBlank() && it !in clubDesignators }
        .joinToString(" ")
}
