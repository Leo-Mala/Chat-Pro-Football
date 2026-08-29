package com.example.data

import java.text.Normalizer
import java.util.Locale

/** Catálogo dos PNGs originais de escudos fornecidos pelo patch Brasfoot. */
object BrasfootPatchCrests {
    private const val ASSET_URI_PREFIX = "file:///android_asset/club_crests/"

    data class Entry(
        val country: String,
        val clubName: String,
        val crestFileName: String
    )

    private val lock = Any()
    private var crestByClubKey: Map<String, String> = emptyMap()

    fun install(entries: Collection<Entry>) {
        val next = LinkedHashMap<String, String>()
        entries.forEach { entry ->
            require(entry.country.isNotBlank()) { "País vazio no catálogo de escudos." }
            require(entry.clubName.isNotBlank()) { "Clube vazio no catálogo de escudos." }
            require(entry.crestFileName.endsWith(".png", ignoreCase = true)) {
                "O escudo deve manter o PNG original do patch: ${entry.crestFileName}"
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
        synchronized(lock) { crestByClubKey = next.toMap() }
    }

    fun assetUriFor(country: String, clubName: String): String? =
        synchronized(lock) { crestByClubKey[key(country, clubName)] }
            ?.let { ASSET_URI_PREFIX + it }

    fun isBundledAssetUri(url: String?): Boolean =
        !url.isNullOrBlank() && url.startsWith(ASSET_URI_PREFIX, ignoreCase = false)

    fun installedCount(): Int = synchronized(lock) { crestByClubKey.size }

    internal fun resetForTests() {
        synchronized(lock) { crestByClubKey = emptyMap() }
    }

    private fun key(country: String, name: String): String = normalize(country) + '\u0000' + normalize(name)

    private fun normalize(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()
}
