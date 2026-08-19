package com.example.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class Fc26ClubMatchStatus { MATCHED, UNMATCHED, AMBIGUOUS }

enum class Fc26TargetMaterializationStatus {
    STABLE_TARGET_PRESENT,
    STABLE_TARGET_MISSING,
    NO_STABLE_IDENTITY,
    UNKNOWN_COUNTRY_CONTEXT
}

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

data class Fc26TargetClubCandidate(
    val targetTeamId: Long,
    val targetTeamName: String,
    val targetCountry: String,
    val targetDivision: Int,
    val score: Int,
    val reasons: List<String>
)

data class Fc26ClubCandidateAudit(
    val sourceClubTeamId: Long,
    val sourceClubName: String,
    val leagueId: Long?,
    val leagueName: String?,
    val sourceCountry: String?,
    val playerCount: Int,
    val currentStatus: Fc26ClubMatchStatus,
    val materializationStatus: Fc26TargetMaterializationStatus,
    val expectedStableTeamId: Long? = null,
    val expectedStableTeamName: String? = null,
    val candidates: List<Fc26TargetClubCandidate>
)

/**
 * Conservative club matching.
 *
 * Automatic matching remains restricted to:
 *  1. audited source `club_team_id` -> existing Pro Football `Team.id`;
 *  2. already-reserved stable club identity when that exact stable target is materialized;
 *  3. the pre-existing exact/explicit-name and unique designator-normalized rules.
 *
 * Similarity scoring exists only in [auditCandidates]; it never promotes a candidate to MATCHED.
 */
object Fc26ClubMatcher {
    private val clubDesignators = setOf("fc", "afc", "cf", "sc", "ac")

    /** Small audited spelling bridge; key/value are normalized before use. */
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
        val targetById = targets.associateBy { it.team.id }
        val sourceCoreFrequency = dataset.sourceClubs.groupingBy { core(it.clubName) }.eachCount()

        return dataset.sourceClubs.map { source ->
            val explicit = Fc26ClubMappingRegistry.explicitMappingFor(source)
            if (explicit != null) {
                val target = targetById[explicit.targetTeamId]
                if (target != null && targetMatchesExplicitIdentity(target, explicit)) {
                    return@map target.toMatch(source, "explicit source club id: ${explicit.reason}")
                }
            }

            val stableIdentity = stableIdentityForSource(source)
            if (stableIdentity != null) {
                targetById[stableIdentity.id]?.let { target ->
                    return@map target.toMatch(source, "stable country/source identity")
                }
            }

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

    /**
     * Produces review-only ranked suggestions for unresolved FC26 clubs.
     * Scores are diagnostic and are deliberately disconnected from [match].
     */
    fun auditCandidates(
        dataset: Fc26Dataset,
        teams: List<Team>,
        limitPerClub: Int = 5
    ): List<Fc26ClubCandidateAudit> {
        require(limitPerClub in 1..20)
        val matchesBySourceId = match(dataset, teams).associateBy { it.sourceClubTeamId }
        val targets = teams.map { Target(it, targetNames(it)) }
        val targetIds = teams.mapTo(mutableSetOf()) { it.id }

        return dataset.sourceClubs
            .asSequence()
            .filter { matchesBySourceId.getValue(it.sourceClubTeamId).status != Fc26ClubMatchStatus.MATCHED }
            .map { source ->
                val currentMatch = matchesBySourceId.getValue(source.sourceClubTeamId)
                val country = Fc26ClubMappingRegistry.countryFor(source)
                val stableIdentity = stableIdentityForSource(source)
                val materialization = when {
                    country == null -> Fc26TargetMaterializationStatus.UNKNOWN_COUNTRY_CONTEXT
                    stableIdentity == null -> Fc26TargetMaterializationStatus.NO_STABLE_IDENTITY
                    stableIdentity.id in targetIds -> Fc26TargetMaterializationStatus.STABLE_TARGET_PRESENT
                    else -> Fc26TargetMaterializationStatus.STABLE_TARGET_MISSING
                }

                val scopedTargets = if (country == null) {
                    targets
                } else {
                    targets.filter { normalize(it.team.country) == normalize(country) }
                }

                val candidates = scopedTargets
                    .map { target -> scoreCandidate(source, target, stableIdentity?.id) }
                    .sortedWith(
                        compareByDescending<Fc26TargetClubCandidate> { it.score }
                            .thenBy { it.targetTeamId }
                    )
                    .take(limitPerClub)

                Fc26ClubCandidateAudit(
                    sourceClubTeamId = source.sourceClubTeamId,
                    sourceClubName = source.clubName,
                    leagueId = source.leagueId,
                    leagueName = source.leagueName,
                    sourceCountry = country,
                    playerCount = source.players.size,
                    currentStatus = currentMatch.status,
                    materializationStatus = materialization,
                    expectedStableTeamId = stableIdentity?.id,
                    expectedStableTeamName = stableIdentity?.canonicalName,
                    candidates = candidates
                )
            }
            .sortedWith(
                compareByDescending<Fc26ClubCandidateAudit> { it.playerCount }
                    .thenBy { it.sourceClubName }
                    .thenBy { it.sourceClubTeamId }
            )
            .toList()
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

    private fun targetMatchesExplicitIdentity(
        target: Target,
        explicit: Fc26ClubMappingRegistry.ExplicitMapping
    ): Boolean {
        if (target.team.id != explicit.targetTeamId) return false
        val stable = StableTeamIdentityRegistry.identityForId(target.team.id)
        if (stable != null) {
            return normalize(stable.canonicalName) == normalize(explicit.targetCanonicalName)
        }
        return target.names.any { it == normalize(explicit.targetCanonicalName) }
    }

    private fun stableIdentityForSource(source: Fc26SourceClub): StableTeamIdentity? {
        val country = Fc26ClubMappingRegistry.countryFor(source) ?: return null
        val sourceName = normalize(source.clubName)
        val aliasTarget = explicitAliases[sourceName]

        return StableTeamIdentityRegistry.all.singleOrNull { identity ->
            normalize(identity.country) == normalize(country) &&
                (identity.aliases + identity.canonicalName).any { name ->
                    val normalized = normalize(name)
                    normalized == sourceName || (aliasTarget != null && normalized == aliasTarget)
                }
        }
    }

    private fun targetNames(team: Team): Set<String> = buildSet {
        add(normalize(team.name))
        StableTeamIdentityRegistry.identityForId(team.id)?.let { stable ->
            add(normalize(stable.canonicalName))
            stable.aliases.forEach { add(normalize(it)) }
        }
    }

    private fun scoreCandidate(
        source: Fc26SourceClub,
        target: Target,
        expectedStableId: Long?
    ): Fc26TargetClubCandidate {
        val sourceName = normalize(source.clubName)
        val sourceCore = core(source.clubName)
        var bestScore = 0
        val reasons = linkedSetOf<String>()

        target.names.forEach { candidateName ->
            when {
                candidateName == sourceName -> {
                    bestScore = max(bestScore, 100)
                    reasons += "exact normalized name"
                }
                core(candidateName) == sourceCore -> {
                    bestScore = max(bestScore, 96)
                    reasons += "same conservative core"
                }
                else -> {
                    val similarity = normalizedEditSimilarity(sourceCore, core(candidateName))
                    bestScore = max(bestScore, (similarity * 85.0).roundToInt())
                }
            }
        }

        if (expectedStableId != null && target.team.id == expectedStableId) {
            bestScore = 100
            reasons += "expected stable Team.id"
        }

        if (reasons.isEmpty()) reasons += "review-only textual candidate"

        return Fc26TargetClubCandidate(
            targetTeamId = target.team.id,
            targetTeamName = target.team.name,
            targetCountry = target.team.country,
            targetDivision = target.team.division,
            score = bestScore.coerceIn(0, 100),
            reasons = reasons.toList().sorted()
        )
    }

    private fun normalizedEditSimilarity(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val distance = levenshtein(left, right)
        return 1.0 - distance.toDouble() / max(left.length, right.length).toDouble()
    }

    private fun levenshtein(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)

        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val cost = if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
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
