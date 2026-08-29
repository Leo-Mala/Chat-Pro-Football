package com.example.data

import java.text.Normalizer
import java.util.Locale

/**
 * Compatibilidade de identidade para a troca dos fillers procedurais por clubes reais do patch.
 *
 * O clube real herda o slot do filler que substitui. Dessa forma, [GlobalFootballSystem] continua
 * atribuindo o mesmo ID numérico ao slot e jogadores/fixtures já vinculados ao ID não precisam ser
 * renumerados apenas porque o nome visível do clube foi corrigido.
 */
object BrasfootRealClubIdentity {
    data class Replacement(
        val legacyTeamId: Long,
        val country: String,
        val division: Int,
        val legacySlotName: String,
        val realClubName: String,
        val crestFileName: String
    )

    private val lock = Any()
    private var replacementByLegacyTeamId: Map<Long, Replacement> = emptyMap()
    private var legacyTeamIdByRealKey: Map<String, Long> = emptyMap()
    private var legacySlotByRealKey: Map<String, String> = emptyMap()
    private var crestByRealKey: Map<String, String> = emptyMap()

    fun install(replacements: Collection<Replacement>) {
        val byLegacyId = LinkedHashMap<Long, Replacement>(replacements.size)
        val ids = LinkedHashMap<String, Long>(replacements.size)
        val aliases = LinkedHashMap<String, String>(replacements.size)
        val crests = LinkedHashMap<String, String>(replacements.size)
        val legacyIds = HashSet<Long>(replacements.size)
        val legacyKeys = HashSet<String>(replacements.size)
        val crestNames = HashSet<String>(replacements.size)

        replacements.forEach { replacement ->
            require(replacement.legacyTeamId > 0L) { "ID legado inválido para ${replacement.realClubName}." }
            require(legacyIds.add(replacement.legacyTeamId)) { "ID legado reutilizado: ${replacement.legacyTeamId}" }
            require(replacement.country.isNotBlank()) { "País vazio no plano de clubes reais." }
            require(replacement.division > 0) { "Divisão inválida para ${replacement.realClubName}." }
            require(replacement.legacySlotName.isNotBlank()) { "Slot legado vazio para ${replacement.realClubName}." }
            require(replacement.realClubName.isNotBlank()) { "Nome real vazio para ${replacement.legacySlotName}." }
            require(replacement.crestFileName.endsWith(".png", ignoreCase = true)) {
                "Escudo deve preservar o PNG original do patch: ${replacement.crestFileName}"
            }

            val realKey = key(replacement.country, replacement.realClubName)
            require(realKey !in aliases) { "Clube real duplicado no plano: ${replacement.country} / ${replacement.realClubName}" }

            val legacyKey = key(replacement.country, replacement.legacySlotName)
            require(legacyKeys.add(legacyKey)) {
                "Slot procedural reutilizado mais de uma vez: ${replacement.country} / ${replacement.legacySlotName}"
            }

            val crestKey = replacement.crestFileName.lowercase(Locale.ROOT)
            require(crestNames.add(crestKey)) { "Escudo reutilizado por mais de um clube: ${replacement.crestFileName}" }

            byLegacyId[replacement.legacyTeamId] = replacement
            ids[realKey] = replacement.legacyTeamId
            aliases[realKey] = replacement.legacySlotName
            crests[realKey] = replacement.crestFileName
        }

        synchronized(lock) {
            replacementByLegacyTeamId = byLegacyId.toMap()
            legacyTeamIdByRealKey = ids.toMap()
            legacySlotByRealKey = aliases.toMap()
            crestByRealKey = crests.toMap()
        }
    }

    /**
     * Resolve a identidade real diretamente pelo ID persistido no save antigo.
     *
     * Esta é a direção mais segura para migração/reconciliação de saves: o ID numérico já é a
     * referência usada por jogadores, fixtures e demais tabelas, então o processo não precisa
     * confiar que o nome procedural legado ainda esteja disponível ou idêntico.
     */
    fun replacementForLegacyTeamId(legacyTeamId: Long): Replacement? =
        synchronized(lock) { replacementByLegacyTeamId[legacyTeamId] }

    fun legacyTeamIdFor(country: String, realClubName: String): Long? =
        synchronized(lock) { legacyTeamIdByRealKey[key(country, realClubName)] }

    fun legacySlotNameFor(country: String, realClubName: String): String? =
        synchronized(lock) { legacySlotByRealKey[key(country, realClubName)] }

    fun crestAssetUriFor(country: String, realClubName: String): String? =
        synchronized(lock) { crestByRealKey[key(country, realClubName)] }
            ?.let { "file:///android_asset/club_crests/$it" }

    fun installedReplacementCount(): Int = synchronized(lock) { legacySlotByRealKey.size }

    internal fun resetForTests() {
        synchronized(lock) {
            replacementByLegacyTeamId = emptyMap()
            legacyTeamIdByRealKey = emptyMap()
            legacySlotByRealKey = emptyMap()
            crestByRealKey = emptyMap()
        }
    }

    private fun key(country: String, name: String): String =
        normalize(country) + '\u0000' + normalize(name)

    private fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()
}
