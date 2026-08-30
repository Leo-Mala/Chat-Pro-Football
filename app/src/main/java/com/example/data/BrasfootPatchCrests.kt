package com.example.data

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Catálogo dos escudos originais materializados para os clubes reais (PNG ou SVG). */
object BrasfootPatchCrests {
    private const val ASSET_URI_PREFIX = "file:///android_asset/club_crests/"
    private val supportedExtensions = setOf("png", "svg")
    private val combiningMarksRegex = "\\p{M}+".toRegex()
    private val nonAlphaNumericRegex = "[^a-z0-9]+".toRegex()
    private val normalizedValueCache = ConcurrentHashMap<String, String>()

    data class Entry(
        val country: String,
        val clubName: String,
        val crestFileName: String
    )

    @Volatile
    private var crestByClubKey: Map<String, String> = emptyMap()

    fun install(entries: Collection<Entry>) {
        val next = LinkedHashMap<String, String>(entries.size)
        entries.forEach { entry ->
            require(entry.country.isNotBlank()) { "País vazio no catálogo de escudos." }
            require(entry.clubName.isNotBlank()) { "Clube vazio no catálogo de escudos." }
            require('/' !in entry.crestFileName && '\\' !in entry.crestFileName) {
                "Nome de escudo deve ser basename puro: ${entry.crestFileName}"
            }
            val extension = entry.crestFileName.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase(Locale.ROOT)
            require(extension in supportedExtensions) {
                "O escudo deve preservar o PNG ou SVG original: ${entry.crestFileName}"
            }
            val key = key(entry.country, entry.clubName)
            val previous = next.putIfAbsent(key, entry.crestFileName)
            require(previous == null || previous.equals(entry.crestFileName, ignoreCase = true)) {
                "Clube com dois escudos diferentes: ${entry.country} / ${entry.clubName}"
            }
        }
        require(next.values.map { it.lowercase(Locale.ROOT) }.distinct().size == next.size) {
            "O mesmo arquivo de escudo foi atribuído a clubes diferentes."
        }
        crestByClubKey = next.toMap()
    }

    fun assetUriFor(country: String, clubName: String): String? =
        crestByClubKey[key(country, clubName)]?.let { ASSET_URI_PREFIX + it }

    fun isBundledAssetUri(url: String?): Boolean =
        !url.isNullOrBlank() && url.startsWith(ASSET_URI_PREFIX, ignoreCase = false)

    fun installedCount(): Int = crestByClubKey.size

    internal fun resetForTests() {
        crestByClubKey = emptyMap()
        normalizedValueCache.clear()
    }

    private fun key(country: String, name: String): String = normalize(country) + '\u0000' + normalize(name)

    private fun normalize(value: String): String = normalizedValueCache.getOrPut(value) {
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(combiningMarksRegex, "")
            .lowercase(Locale.ROOT)
            .replace(nonAlphaNumericRegex, " ")
            .trim()
    }
}
