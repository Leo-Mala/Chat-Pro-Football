package com.example.data

import androidx.room.Dao
import androidx.room.Query

/**
 * DAO estreito para leituras de jogadores em lote usadas pelo fechamento da rodada.
 *
 * Mantê-lo separado de PlayerDao permite acelerar os caminhos quentes sem alterar a semântica das
 * consultas legadas. Os chamadores fragmentam os ids abaixo do limite de bind parameters do SQLite.
 */
@Dao
interface PlayerBatchDao {
    @Query(
        """
        SELECT * FROM players
        WHERE teamId IN (:teamIds)
        ORDER BY teamId ASC, position DESC, force DESC
        """
    )
    suspend fun getPlayersByTeamIds(teamIds: List<Long>): List<Player>

    @Query(
        """
        SELECT * FROM players
        WHERE id IN (:playerIds)
        ORDER BY id ASC
        """
    )
    suspend fun getPlayersByIds(playerIds: List<Long>): List<Player>
}
