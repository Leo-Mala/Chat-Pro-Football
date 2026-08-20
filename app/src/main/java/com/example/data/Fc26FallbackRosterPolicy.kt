package com.example.data

/**
 * Phase 9.14 policy for procedural squads that exist only because the FC26 snapshot has no safely
 * matched source club for a Pro Football Team.
 *
 * The legacy roster generator still produces its canonical deterministic 30-player squad. This
 * policy only selects a balanced subset of that already-generated squad, so retained fallback
 * players keep the exact same ids, attributes, contracts and salaries they had before Phase 9.14.
 * Real FC26 players never pass through this policy.
 */
internal object Fc26FallbackRosterPolicy {
    const val TARGET_SIZE = 20

    private val targetByPosition = linkedMapOf(
        "GOL" to 2,
        "ZAG" to 4,
        "LAT" to 3,
        "VOL" to 3,
        // 3-2-4-1 is a supported formation and requires four natural MEI players.
        "MEI" to 4,
        "ATA" to 4
    )

    init {
        require(targetByPosition.values.sum() == TARGET_SIZE)
    }

    fun select(fullRoster: List<Player>): List<Player> {
        if (fullRoster.size <= TARGET_SIZE) return fullRoster.toList()

        val selected = BooleanArray(fullRoster.size)
        var selectedCount = 0

        for ((position, targetCount) in targetByPosition) {
            var positionCount = 0
            for (index in fullRoster.indices) {
                if (positionCount >= targetCount) break
                if (!selected[index] && fullRoster[index].position == position) {
                    selected[index] = true
                    selectedCount++
                    positionCount++
                }
            }
        }

        // Custom/test roster factories may not expose the canonical six-position distribution.
        // Fill the remaining slots deterministically in original order without changing any Player.
        if (selectedCount < TARGET_SIZE) {
            for (index in fullRoster.indices) {
                if (selectedCount >= TARGET_SIZE) break
                if (!selected[index]) {
                    selected[index] = true
                    selectedCount++
                }
            }
        }

        return fullRoster.filterIndexed { index, _ -> selected[index] }
    }
}
