package com.example.data

/**
 * Targeted lookup used by loan lifecycle hot paths. Keeping this outside GameRepository avoids a
 * broad repository rewrite while still using the typed Room DAO and the indexed Player primary key.
 */
suspend fun GameRepository.getPlayersByIds(ids: Collection<Long>): List<Player> {
    if (ids.isEmpty()) return emptyList()
    val distinctIds = ids.asSequence().filter { it > 0L }.distinct().toList()
    if (distinctIds.isEmpty()) return emptyList()
    return db.playerDao().getPlayersByIds(distinctIds)
}
