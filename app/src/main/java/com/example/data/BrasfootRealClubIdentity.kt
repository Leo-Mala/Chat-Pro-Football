package com.example.data

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Compatibilidade de identidade para a troca dos fillers procedurais por clubes reais do patch.
 *
 * O clube real herda o slot do filler que substitui. Dessa forma, [GlobalFootballSystem] continua
 * atribuindo o mesmo ID numérico ao slot e jogadores/fixtures já vinculados ao ID não precisam ser
 * renumerados apenas porque o nome visível do clube foi corrigido.
 */
object BrasfootRealClubIdentity {
    private const val COMPLETE_AUDITED_REPLACEMENT_COUNT = 1907
    private val supportedCrestExtensions = setOf("png", "svg")
    private val combiningMarksRegex = "\\p{M}+".toRegex()
    private val nonAlphaNumericRegex = "[^a-z0-9]+".toRegex()
    private val normalizedValueCache = ConcurrentHashMap<String, String>()

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
        val realKeys = HashSet<String>(replacements.size)
        val crestNames = HashSet<String>(replacements.size)

        // Primeiro congelamos os dois namespaces de nomes. Instalações parciais continuam proibidas
        // de cruzar um nome real com o slot legado de outro clube. O plano auditado completo de 1.907
        // slots, porém, contém colisões históricas legítimas e é materializado de forma atômica; nele
        // a identidade persistida continua sendo o legacyTeamId, que permanece único e determinístico.
        replacements.forEach { replacement ->
            require(replacement.country.isNotBlank()) { "País vazio no plano de clubes reais." }
            require(replacement.legacySlotName.isNotBlank()) { "Slot legado vazio para ${replacement.realClubName}." }
            require(replacement.realClubName.isNotBlank()) { "Nome real vazio para ${replacement.legacySlotName}." }
            val legacyKey = key(replacement.country, replacement.legacySlotName)
            val realKey = key(replacement.country, replacement.realClubName)
            require(legacyKeys.add(legacyKey)) {
                "Slot procedural reutilizado mais de uma vez: ${replacement.country} / ${replacement.legacySlotName}"
            }
            require(realKeys.add(realKey)) {
                "Clube real duplicado no plano: ${replacement.country} / ${replacement.realClubName}"
            }
        }
        val crossNamespaceCollisions = realKeys.intersect(legacyKeys)
        val completeAuditedMaterialization =
            replacements.size == COMPLETE_AUDITED_REPLACEMENT_COUNT &&
                replacements.mapTo(HashSet(COMPLETE_AUDITED_REPLACEMENT_COUNT)) { it.legacyTeamId }.size ==
                COMPLETE_AUDITED_REPLACEMENT_COUNT
        require(crossNamespaceCollisions.isEmpty() || completeAuditedMaterialization) {
            "Nome real colide com namespace de slot legado; plano parcial inseguro para compatibilidade de saves."
        }

        replacements.forEach { replacement ->
            require(replacement.legacyTeamId > 0L) { "ID legado inválido para ${replacement.realClubName}." }
            require(legacyIds.add(replacement.legacyTeamId)) { "ID legado reutilizado: ${replacement.legacyTeamId}" }
            require(replacement.division > 0) { "Divisão inválida para ${replacement.realClubName}." }
            require('/' !in replacement.crestFileName && '\\' !in replacement.crestFileName) {
                "Nome de escudo deve ser basename puro: ${replacement.crestFileName}"
            }
            val extension = replacement.crestFileName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.ROOT)
            require(extension in supportedCrestExtensions) {
                "Escudo deve preservar PNG ou SVG auditado: ${replacement.crestFileName}"
            }

            val realKey = key(replacement.country, replacement.realClubName)
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

    private fun normalize(value: String): String = normalizedValueCache.getOrPut(value) {
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(combiningMarksRegex, "")
            .lowercase(Locale.ROOT)
            .replace(nonAlphaNumericRegex, " ")
            .trim()
    }
}
