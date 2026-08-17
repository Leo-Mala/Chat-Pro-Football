package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Snapshot compacto da classificação de uma liga em uma temporada.
 *
 * Diferente de [Fixture], esta entidade não guarda cada partida das ligas CPU globais.
 * Ela preserva somente o resultado agregado necessário para histórico, qualificação
 * continental e promoção/rebaixamento sem inflar o save.
 *
 * A primeira divisão pode permanecer como histórico de longo prazo. Divisões inferiores
 * são mantidas numa janela curta, suficiente para a movimentação global, e podem ser podadas
 * em temporadas seguintes para limitar o crescimento do banco.
 */
@Entity(
    tableName = "global_league_standings",
    primaryKeys = ["season", "country", "division", "teamId"],
    indices = [
        Index(value = ["season", "country", "division", "position"]),
        Index(value = ["teamId"])
    ]
)
data class GlobalLeagueStanding(
    val season: Int,
    val country: String,
    val division: Int,
    val teamId: Long,
    val position: Int,
    val points: Int,
    val played: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int
)

@Dao
interface GlobalLeagueStandingDao {
    @Query(
        "SELECT * FROM global_league_standings " +
            "WHERE season = :season ORDER BY country ASC, division ASC, position ASC"
    )
    suspend fun getForSeason(season: Int): List<GlobalLeagueStanding>

    @Query(
        "SELECT * FROM global_league_standings " +
            "WHERE season = :season AND country = :country AND division = :division " +
            "ORDER BY position ASC"
    )
    suspend fun getForLeague(
        season: Int,
        country: String,
        division: Int
    ): List<GlobalLeagueStanding>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<GlobalLeagueStanding>)

    @Query("DELETE FROM global_league_standings WHERE season = :season")
    suspend fun deleteForSeason(season: Int)

    @Query(
        "DELETE FROM global_league_standings " +
            "WHERE division > 1 AND season < :keepFromSeason"
    )
    suspend fun deleteLowerDivisionsBeforeSeason(keepFromSeason: Int)

    @Query("DELETE FROM global_league_standings")
    suspend fun deleteAll()
}
