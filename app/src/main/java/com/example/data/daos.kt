package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GameSaveDao {
    @Query("SELECT * FROM game_save WHERE id = 1")
    fun getGameSaveFlow(): Flow<GameSave?>

    @Query("SELECT * FROM game_save WHERE id = 1")
    suspend fun getGameSave(): GameSave?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(save: GameSave)

    @Query("DELETE FROM game_save")
    suspend fun deleteSave()
}

@Dao
interface TeamDao {
    @Query("SELECT * FROM teams ORDER BY division ASC, rating DESC, name ASC")
    fun getAllTeamsFlow(): Flow<List<Team>>

    @Query("SELECT * FROM teams WHERE country = :leagueCountry ORDER BY division ASC, rating DESC, name ASC")
    fun getTeamsByLeagueFlow(leagueCountry: String): Flow<List<Team>>

    @Query("SELECT * FROM teams ORDER BY division ASC, rating DESC, name ASC")
    suspend fun getAllTeams(): List<Team>

    @Query("SELECT * FROM teams WHERE id = :id")
    suspend fun getTeam(id: Long): Team?

    @Query("SELECT * FROM teams WHERE id = :id")
    fun getTeamFlow(id: Long): Flow<Team?>

    @Query("SELECT id FROM teams WHERE id IN (:ids)")
    suspend fun getExistingTeamIds(ids: List<Long>): List<Long>

    /**
     * Upsert evita a semântica DELETE+INSERT do SQLite REPLACE. Isso é essencial depois que Team
     * se torna tabela-pai de FKs de Player/Fixture no schema V21.
     */
    @Upsert
    suspend fun insertTeams(teams: List<Team>)

    @Update
    suspend fun updateTeam(team: Team)

    @Query("DELETE FROM teams WHERE id = :id")
    suspend fun deleteTeam(id: Long)

    @Query("DELETE FROM teams")
    suspend fun deleteTeams()
}

@Dao
interface PlayerDao {
    @Query("SELECT * FROM players ORDER BY force DESC, name ASC")
    fun getAllPlayersFlow(): Flow<List<Player>>

    @Query("SELECT * FROM players ORDER BY force DESC, name ASC")
    suspend fun getAllPlayers(): List<Player>

    @Query("SELECT * FROM players WHERE teamId = :teamId ORDER BY position DESC, force DESC")
    fun getPlayersByTeamFlow(teamId: Long?): Flow<List<Player>>

    @Query("SELECT * FROM players WHERE teamId = :teamId ORDER BY position DESC, force DESC")
    suspend fun getPlayersByTeam(teamId: Long?): List<Player>

    @Query("SELECT COUNT(*) FROM players WHERE teamId = :teamId")
    suspend fun getPlayerCountByTeam(teamId: Long?): Int

    @Query("SELECT * FROM players WHERE teamId IS NULL ORDER BY position DESC, force DESC, id ASC")
    suspend fun getFreeAgents(): List<Player>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getPlayer(id: Long): Player?

    /**
     * Reset sazonal em action-set SQL. Só toca nos campos esportivos que pertencem ao reset e
     * evita materializar dezenas de milhares de Player apenas para gravar valores constantes.
     */
    @Query("""
        UPDATE players
        SET energy = 100,
            moral = 75,
            injuryWeeksRemaining = 0,
            suspensionWeeksRemaining = 0,
            yellowCardsAccumulated = 0,
            careerGoals = 0
    """)
    suspend fun resetSeasonState(): Int

    /**
     * Phase 9.12B: aplica o ciclo semanal de contratos como conjuntos de ações SQL.
     * As expirações precisam acontecer antes do decremento de contratos > 1 para que um vínculo
     * com 2 semanas vire 1 — e não expire — em um único tick.
     */
    @Query("""
        UPDATE players
        SET contractDurationWeeks = 0,
            isStarter = 0,
            salary = 0
        WHERE contractDurationWeeks = 1 AND isOnLoan = 1
    """)
    suspend fun expireLoanContractsAtOneWeek(): Int

    @Query("""
        UPDATE players
        SET contractDurationWeeks = 0,
            teamId = NULL,
            originalTeamId = NULL,
            isStarter = 0,
            salary = 0
        WHERE contractDurationWeeks = 1 AND isOnLoan = 0
    """)
    suspend fun expireNonLoanContractsAtOneWeek(): Int

    @Query("""
        UPDATE players
        SET contractDurationWeeks = contractDurationWeeks - 1
        WHERE contractDurationWeeks > 1
    """)
    suspend fun decrementLongerContractsOneWeek(): Int

    @Transaction
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlayers(players: List<Player>)

    @Upsert
    suspend fun insertPlayersReplace(players: List<Player>)

    @Update
    suspend fun updatePlayer(player: Player)

    @Transaction
    @Update
    suspend fun updatePlayers(players: List<Player>)

    @Query("SELECT * FROM players WHERE teamId = :teamId ORDER BY mediaNotas DESC")
    fun getJogadoresPorNota(teamId: Long?): Flow<List<Player>>

    @Query("UPDATE players SET condicao = :condicao WHERE id = :id")
    suspend fun atualizarCondicao(id: Long, condicao: Int)

    @Query("UPDATE players SET minutosJogados = minutosJogados + :minutos WHERE id = :id")
    suspend fun adicionarMinutos(id: Long, minutos: Int)

    @Update
    suspend fun atualizar(jogador: Player)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deletePlayer(id: Long)

    @Query("DELETE FROM players")
    suspend fun deletePlayers()
}

@Dao
interface FixtureDao {
    @Query("SELECT * FROM fixtures ORDER BY season DESC, week ASC, matchSlot ASC, id ASC")
    fun getFixturesFlow(): Flow<List<Fixture>>

    @Query("SELECT * FROM fixtures ORDER BY season DESC, week ASC, matchSlot ASC, id ASC")
    suspend fun getAllFixtures(): List<Fixture>

    @Query("SELECT * FROM fixtures WHERE season = :season ORDER BY week ASC, matchSlot ASC, id ASC")
    fun getFixturesForSeasonFlow(season: Int): Flow<List<Fixture>>

    @Query("SELECT * FROM fixtures WHERE season = :season ORDER BY week ASC, matchSlot ASC, id ASC")
    suspend fun getFixturesForSeason(season: Int): List<Fixture>

    @Query("SELECT * FROM fixtures WHERE season = :season AND week = :week ORDER BY isPlayed ASC, matchSlot ASC, id ASC")
    fun getFixturesForWeekFlow(season: Int, week: Int): Flow<List<Fixture>>

    @Query(
        """
        SELECT * FROM fixtures
        WHERE season = :season
          AND week >= :week
          AND isPlayed = 0
          AND (homeTeamId = :teamId OR awayTeamId = :teamId)
        ORDER BY week ASC, matchSlot ASC, id ASC
        LIMIT 1
        """
    )
    fun getNextFixtureForTeamFlow(season: Int, week: Int, teamId: Long): Flow<Fixture?>

    @Query("SELECT * FROM fixtures WHERE season = :season AND week = :week ORDER BY isPlayed ASC, matchSlot ASC, id ASC")
    suspend fun getFixturesForWeek(season: Int, week: Int): List<Fixture>

    @Upsert
    suspend fun insertFixtures(fixtures: List<Fixture>)

    @Update
    suspend fun updateFixture(fixture: Fixture)

    @Transaction
    @Update
    suspend fun updateFixtures(fixtures: List<Fixture>)

    @Query("DELETE FROM fixtures")
    suspend fun deleteFixtures()

    @Query("DELETE FROM fixtures WHERE season < :currentSeason")
    suspend fun deleteOldSeasonFixtures(currentSeason: Int)

    @Query("DELETE FROM fixtures WHERE id IN (:ids)")
    suspend fun deleteFixturesByIds(ids: List<Long>)
}

@Dao
interface ClubLegendDao {
    @Query("SELECT * FROM club_legends WHERE teamId = :teamId ORDER BY goals DESC, apps DESC")
    fun getLegendsForTeamFlow(teamId: Long): Flow<List<ClubLegend>>

    @Query("SELECT * FROM club_legends")
    suspend fun getAllLegends(): List<ClubLegend>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLegend(legend: ClubLegend)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLegends(legends: List<ClubLegend>)

    @Update
    suspend fun updateLegend(legend: ClubLegend)

    @Query("DELETE FROM club_legends")
    suspend fun deleteLegends()
}

@Dao
interface HistoricalRecordDao {
    @Query("SELECT * FROM historical_records ORDER BY season DESC, id ASC")
    fun getAllRecordsFlow(): Flow<List<HistoricalRecord>>

    @Query("SELECT * FROM historical_records")
    suspend fun getAllRecords(): List<HistoricalRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: HistoricalRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<HistoricalRecord>)

    @Query("DELETE FROM historical_records")
    suspend fun deleteRecords()
}

@Dao
interface CoachOfferDao {
    @Query("SELECT * FROM coach_offers ORDER BY rating DESC")
    fun getCoachOffersFlow(): Flow<List<CoachOffer>>

    @Query("SELECT * FROM coach_offers")
    suspend fun getAllOffers(): List<CoachOffer>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOffers(offers: List<CoachOffer>)

    @Query("DELETE FROM coach_offers")
    suspend fun deleteOffers()
}

@Dao
interface TransactionRecordDao {
    @Query("SELECT * FROM transaction_history ORDER BY id DESC")
    fun getAllTransactionsFlow(): Flow<List<TransactionRecord>>

    @Query("SELECT * FROM transaction_history ORDER BY id DESC")
    suspend fun getAllTransactions(): List<TransactionRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionRecord)

    @Query("DELETE FROM transaction_history")
    suspend fun deleteTransactions()

    @Query("DELETE FROM transaction_history WHERE season < :currentSeason - 1")
    suspend fun deleteOldTransactions(currentSeason: Int)
}

@Dao
interface TransferOrderDao {
    @Query("SELECT * FROM transfer_orders ORDER BY timestamp DESC")
    fun getAllOrdersFlow(): Flow<List<TransferOrder>>

    @Query("SELECT * FROM transfer_orders ORDER BY timestamp DESC")
    suspend fun getAllOrders(): List<TransferOrder>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: TransferOrder)

    @Update
    suspend fun updateOrder(order: TransferOrder)

    @Delete
    suspend fun deleteOrder(order: TransferOrder)

    @Query("DELETE FROM transfer_orders")
    suspend fun deleteOrders()

    @Query("DELETE FROM transfer_orders WHERE season < :currentSeason - 1")
    suspend fun deleteOldOrders(currentSeason: Int)
}

@Dao
interface TransferInstallmentDao {
    @Query("SELECT * FROM transfer_installments WHERE status = 'ACTIVE' AND (season < :currentSeason OR (season = :currentSeason AND nextDueWeek <= :currentWeek))")
    suspend fun getActiveInstallmentsForWeek(currentSeason: Int, currentWeek: Int): List<TransferInstallment>

    @Query("SELECT * FROM transfer_installments WHERE status = 'ACTIVE'")
    suspend fun getActiveInstallments(): List<TransferInstallment>

    @Query("SELECT * FROM transfer_installments")
    suspend fun getAllInstallments(): List<TransferInstallment>

    @Upsert
    suspend fun insertInstallment(installment: TransferInstallment): Long

    @Upsert
    suspend fun insertInstallments(installments: List<TransferInstallment>)

    @Update
    suspend fun updateInstallment(installment: TransferInstallment)

    @Query("DELETE FROM transfer_installments")
    suspend fun deleteInstallments()
}

@Dao
interface PlayerLoanDao {
    @Query("SELECT * FROM player_loans WHERE status = 'ACTIVE'")
    suspend fun getActiveLoans(): List<PlayerLoan>

    @Query("SELECT * FROM player_loans WHERE playerId = :playerId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveLoanForPlayer(playerId: Long): PlayerLoan?

    @Query("SELECT * FROM player_loans")
    suspend fun getAllLoans(): List<PlayerLoan>

    @Upsert
    suspend fun insertLoan(loan: PlayerLoan): Long

    @Upsert
    suspend fun insertLoans(loans: List<PlayerLoan>)

    @Update
    suspend fun updateLoan(loan: PlayerLoan)

    @Query("DELETE FROM player_loans")
    suspend fun deleteLoans()
}
