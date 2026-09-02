package com.example.data

import androidx.room.Dao
import androidx.room.Query

/**
 * DAO estreito para leituras de elencos em lote usadas pelo fechamento da rodada.
 *
 * Mantê-lo separado de PlayerDao permite acelerar o caminho quente de fixtures da CPU sem alterar
 * a semântica das consultas legadas por clube. O chamador fragmenta os ids abaixo do limite de
 * bind parameters do SQLite.
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
}
