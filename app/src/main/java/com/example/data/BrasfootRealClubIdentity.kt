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
        val country: String,
        val division: Int,
        val legacySlotName: String,
        val realClubName: String,
        val crestFileName: String
    )

    private val lock = Any()
    private var legacySlotByRealKey: Map<String, String> = emptyMap()
    private var crestByRealKey: Map<String, String> = emptyMap()

    fun install(replacements: Collection<Replacement>) {
        val aliases = LinkedHashMap<String, String>(replacements.size)
        val crests = LinkedHashMap<String, String>(replacements.size)
        val legacyKeys = HashSet<String>(replacements.size)
        val crestNames = HashSet<String>(replacements.size)

        replacements.forEach { replacement ->
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

            aliases[realKey] = replacement.legacySlotName
            crests[realKey] = replacement.crestFileName
        }

        synchronized(lock) {
            legacySlotByRealKey = aliases.toMap()
            crestByRealKey = crests.toMap()
        }
    }

    fun legacySlotNameFor(country: String, realClubName: String): String? =
        synchronized(lock) { legacySlotByRealKey[key(country, realClubName)] }

    fun crestAssetUriFor(country: String, realClubName: String): String? =
        synchronized(lock) { crestByRealKey[key(country, realClubName)] }
            ?.let { "file:///android_asset/club_crests/$it" }

    fun installedReplacementCount(): Int = synchronized(lock) { legacySlotByRealKey.size }

    internal fun resetForTests() {
        synchronized(lock) {
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
