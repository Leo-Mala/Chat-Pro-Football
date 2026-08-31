package com.example.data

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves an installed crest from the stable Team.id before falling back to a persisted URL.
 * This keeps old saves compatible: a stale remote logoUrl never wins over a bundled crest.
 */
object BundledClubCrests {
    private const val ASSET_DIR = "club_crests"
    private const val ASSET_URI_PREFIX = "file:///android_asset/"

    private val resolvedPaths = ConcurrentHashMap<Int, String>()
    private val missingIds = ConcurrentHashMap.newKeySet<Int>()

    fun resolve(context: Context, teamId: Int?, fallbackUrl: String?): String? {
        val id = teamId?.takeIf { it > 0 } ?: return fallbackUrl
        resolvedPaths[id]?.let { return ASSET_URI_PREFIX + it }
        if (missingIds.contains(id)) return fallbackUrl

        val path = candidates(id).firstOrNull { candidate ->
            runCatching { context.assets.open(candidate).use { } }.isSuccess
        }
        if (path == null) {
            missingIds.add(id)
            return fallbackUrl
        }
        resolvedPaths[id] = path
        return ASSET_URI_PREFIX + path
    }

    internal fun assetPathFor(context: Context, teamId: Int): String? {
        val uri = resolve(context, teamId, null) ?: return null
        return uri.removePrefix(ASSET_URI_PREFIX)
    }

    internal fun resetForTests() {
        resolvedPaths.clear()
        missingIds.clear()
    }

    private fun candidates(id: Int): List<String> = listOf(
        "$ASSET_DIR/factual_${id}.webp",
        "$ASSET_DIR/factual_${id}.png",
        "$ASSET_DIR/factual_${id}.svg",
        "$ASSET_DIR/club_${id}.webp",
        "$ASSET_DIR/club_${id}.png",
        "$ASSET_DIR/club_${id}.svg",
    )
}
