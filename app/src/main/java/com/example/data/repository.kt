package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class GameRepository(private val db: AppDatabase) {
    suspend fun <R> withTransaction(block: suspend () -> R): R = db.withTransaction(block)
    suspend fun <R> runInTransaction(block: suspend () -> R): R = db.withTransaction(block)
    val gameSaveFlow: Flow<GameSave?> = db.gameSaveDao().getGameSaveFlow()
    val allTeamsFlow: Flow<List<Team>> = db.teamDao().getAllTeamsFlow()
    fun getTeamsByLeagueFlow(leagueCountry: String): Flow<List<Team>> = db.teamDao().getTeamsByLeagueFlow(leagueCountry)
    val allPlayersFlow: Flow<List<Player>> = db.playerDao().getAllPlayersFlow()
    val allFixturesFlow: Flow<List<Fixture>> = db.fixtureDao().getFixturesFlow()
    val allRecordsFlow: Flow<List<HistoricalRecord>> = db.historicalRecordDao().getAllRecordsFlow()
    val coachOffersFlow: Flow<List<CoachOffer>> = db.coachOfferDao().getCoachOffersFlow()
    val allTransactionsFlow: Flow<List<TransactionRecord>> = db.transactionRecordDao().getAllTransactionsFlow()
    val allOrdersFlow: Flow<List<TransferOrder>> = db.transferOrderDao().getAllOrdersFlow()

    fun getPlayersForTeamFlow(teamId: Long): Flow<List<Player>> = db.playerDao().getPlayersByTeamFlow(teamId)
    fun getLegendsForTeamFlow(teamId: Long): Flow<List<ClubLegend>> = db.clubLegendDao().getLegendsForTeamFlow(teamId)
    fun getFixturesForWeekFlow(season: Int, week: Int): Flow<List<Fixture>> = db.fixtureDao().getFixturesForWeekFlow(season, week)

    suspend fun getGameSave(): GameSave? = db.gameSaveDao().getGameSave()
    suspend fun saveGameSave(save: GameSave) = db.gameSaveDao().insertOrUpdate(save)
    suspend fun deleteSave() = db.gameSaveDao().deleteSave()

    suspend fun promoteAcademyPlayerAtomically(
        expectedPlayerTeamId: Long,
        expectedAcademyProspects: String,
        player: Player,
        updatedAcademyProspects: String
    ): Boolean = db.withTransaction {
        val currentSave = db.gameSaveDao().getGameSave() ?: return@withTransaction false
        if (currentSave.playerTeamId != expectedPlayerTeamId ||
            currentSave.academyProspects != expectedAcademyProspects ||
            player.teamId != expectedPlayerTeamId
        ) {
            return@withTransaction false
        }

        db.playerDao().insertPlayersReplace(listOf(player))
        db.gameSaveDao().insertOrUpdate(
            currentSave.copy(academyProspects = updatedAcademyProspects)
        )
        true
    }

    suspend fun saveTransaction(record: TransactionRecord) = db.transactionRecordDao().insertTransaction(record)
    suspend fun getAllTransactions(): List<TransactionRecord> = db.transactionRecordDao().getAllTransactions()
    suspend fun deleteTransactions() = db.transactionRecordDao().deleteTransactions()

    suspend fun getOrders(): List<TransferOrder> = db.transferOrderDao().getAllOrders()
    suspend fun saveOrder(order: TransferOrder) = db.transferOrderDao().insertOrder(order)
    suspend fun updateOrder(order: TransferOrder) = db.transferOrderDao().updateOrder(order)
    suspend fun deleteOrder(order: TransferOrder) = db.transferOrderDao().deleteOrder(order)
    suspend fun deleteOrders() = db.transferOrderDao().deleteOrders()

    suspend fun getAllTeams(): List<Team> = db.teamDao().getAllTeams()
    suspend fun getTeam(id: Long): Team? = db.teamDao().getTeam(id)
    suspend fun saveTeams(teams: List<Team>) = db.withTransaction {
        if (teams.size > 100) {
            teams.chunked(100).forEach { db.teamDao().insertTeams(it) }
        } else {
            db.teamDao().insertTeams(teams)
        }
    }
    suspend fun updateTeam(team: Team) = db.teamDao().updateTeam(team)
    suspend fun deleteTeam(id: Long) = db.teamDao().deleteTeam(id)
    suspend fun deleteTeams() = db.teamDao().deleteTeams()

    suspend fun getAllPlayers(): List<Player> = db.playerDao().getAllPlayers()
    suspend fun getPlayersByTeam(teamId: Long): List<Player> = db.playerDao().getPlayersByTeam(teamId)
    suspend fun getPlayerCountByTeam(teamId: Long): Int = db.playerDao().getPlayerCountByTeam(teamId)
    suspend fun getPlayer(id: Long): Player? = db.playerDao().getPlayer(id)
    suspend fun insertPlayersIfNotExists(players: List<Player>) = db.withTransaction {
        if (players.size > 100) {
            players.chunked(100).forEach { db.playerDao().insertPlayers(it) }
        } else {
            db.playerDao().insertPlayers(players)
        }
    }
    suspend fun savePlayers(players: List<Player>) = db.withTransaction {
        if (players.size > 100) {
            players.chunked(100).forEach { db.playerDao().insertPlayersReplace(it) }
        } else {
            db.playerDao().insertPlayersReplace(players)
        }
    }
    suspend fun updatePlayer(player: Player) = db.playerDao().updatePlayer(player)
    suspend fun updatePlayers(players: List<Player>) = db.withTransaction {
        if (players.size > 100) {
            players.chunked(100).forEach { db.playerDao().updatePlayers(it) }
        } else {
            db.playerDao().updatePlayers(players)
        }
    }
    fun getJogadoresPorNota(teamId: Long): Flow<List<Player>> = db.playerDao().getJogadoresPorNota(teamId)
    suspend fun atualizarCondicao(id: Long, condicao: Int) = db.playerDao().atualizarCondicao(id, condicao)
    suspend fun adicionarMinutos(id: Long, minutos: Int) = db.playerDao().adicionarMinutos(id, minutos)
    suspend fun saveHistoricoEvolucaoList(list: List<HistoricoEvolucao>) = db.withTransaction {
        db.historicoEvolucaoDao().insertAll(list)
    }
    suspend fun getHistoricoPorJogador(jogadorId: Long): List<HistoricoEvolucao> = db.historicoEvolucaoDao().getHistoricoPorJogador(jogadorId)
    suspend fun deleteAllHistorico() = db.historicoEvolucaoDao().deleteAll()
    suspend fun deletePlayer(id: Long) = db.playerDao().deletePlayer(id)
    suspend fun deletePlayers() = db.playerDao().deletePlayers()

    /**
     * Na interação de uma semana, jogos ainda pendentes precisam vir antes dos já concluídos para
     * que um segundo slot do usuário continue disponível após a partida do primeiro slot. Entre
     * fixtures com o mesmo estado, a ordem canônica MIDWEEK -> WEEKEND continua preservada.
     */
    suspend fun getFixturesForWeek(season: Int, week: Int): List<Fixture> =
        db.fixtureDao().getFixturesForWeek(season, week)
            .sortedWith(
                compareBy<Fixture> { if (it.isPlayed) 1 else 0 }
                    .thenBy { it.matchSlot.order }
                    .thenBy { it.id }
            )

    suspend fun getFixturesForSeason(season: Int): List<Fixture> =
        db.fixtureDao().getFixturesForSeason(season)
            .sortedWith(FixtureScheduleValidator.chronologicalComparator())

    fun getFixturesForSeasonFlow(season: Int): Flow<List<Fixture>> = db.fixtureDao().getFixturesForSeasonFlow(season)
    suspend fun getAllFixtures(): List<Fixture> = db.fixtureDao().getAllFixtures()

    /**
     * Toda criação de fixture passa pela mesma barreira de calendário. Progressões de copas não
     * podem inserir silenciosamente uma segunda partida do mesmo clube no mesmo slot.
     */
    suspend fun saveFixtures(fixtures: List<Fixture>) = db.withTransaction {
        if (fixtures.isEmpty()) return@withTransaction
        val existing = fixtures
            .map { it.season }
            .distinct()
            .flatMap { db.fixtureDao().getFixturesForSeason(it) }
        FixtureScheduleValidator.requireCanAdd(existing, fixtures)

        if (fixtures.size > 100) {
            fixtures.chunked(100).forEach { db.fixtureDao().insertFixtures(it) }
        } else {
            db.fixtureDao().insertFixtures(fixtures)
        }
    }

    /**
     * Alterar somente placar/eventos de um fixture já persistido não reabre a validação temporal.
     * Isso permite que saves V19 continuem jogáveis mesmo se carregarem uma colisão histórica.
     * Qualquer remarcação de semana/slot/clubes continua obrigada a passar pelo validador.
     */
    suspend fun updateFixture(fixture: Fixture) = db.withTransaction {
        val seasonFixtures = db.fixtureDao().getFixturesForSeason(fixture.season)
        val persisted = seasonFixtures.firstOrNull { it.id == fixture.id }
        if (persisted == null || !sameScheduleIdentity(persisted, fixture)) {
            FixtureScheduleValidator.requireCanAdd(
                seasonFixtures.filterNot { it.id == fixture.id },
                listOf(fixture)
            )
        }
        db.fixtureDao().updateFixture(fixture)
    }

    suspend fun updateFixtures(fixtures: List<Fixture>) = db.withTransaction {
        if (fixtures.isEmpty()) return@withTransaction

        val persistedById = fixtures
            .map { it.season }
            .distinct()
            .flatMap { db.fixtureDao().getFixturesForSeason(it) }
            .associateBy { it.id }

        val rescheduled = fixtures.filter { fixture ->
            val persisted = persistedById[fixture.id]
            persisted == null || !sameScheduleIdentity(persisted, fixture)
        }

        if (rescheduled.isNotEmpty()) {
            val rescheduledIds = rescheduled.map { it.id }.filter { it != 0L }.toSet()
            val existing = persistedById.values.filterNot { it.id in rescheduledIds }
            FixtureScheduleValidator.requireCanAdd(existing, rescheduled)
        }

        if (fixtures.size > 100) {
            fixtures.chunked(100).forEach { db.fixtureDao().updateFixtures(it) }
        } else {
            db.fixtureDao().updateFixtures(fixtures)
        }
    }

    private fun sameScheduleIdentity(first: Fixture, second: Fixture): Boolean =
        first.season == second.season &&
            first.week == second.week &&
            first.matchSlot == second.matchSlot &&
            first.homeTeamId == second.homeTeamId &&
            first.awayTeamId == second.awayTeamId &&
            first.competitionType == second.competitionType

    suspend fun deleteFixtures() = db.fixtureDao().deleteFixtures()
    suspend fun deleteFixturesByIds(ids: List<Long>) = db.fixtureDao().deleteFixturesByIds(ids)

    suspend fun getGlobalStandingsForSeason(season: Int): List<GlobalLeagueStanding> =
        db.globalLeagueStandingDao().getForSeason(season)

    suspend fun getGlobalStandingsForLeague(
        season: Int,
        country: String,
        division: Int = 1
    ): List<GlobalLeagueStanding> =
        db.globalLeagueStandingDao().getForLeague(season, country, division)

    suspend fun saveGlobalStandingsForSeason(
        season: Int,
        rows: List<GlobalLeagueStanding>
    ) {
        db.globalLeagueStandingDao().deleteForSeason(season)
        if (rows.size > 100) {
            rows.chunked(100).forEach { db.globalLeagueStandingDao().insertAll(it) }
        } else if (rows.isNotEmpty()) {
            db.globalLeagueStandingDao().insertAll(rows)
        }
        db.globalLeagueStandingDao().deleteLowerDivisionsBeforeSeason(season)
    }

    suspend fun deleteGlobalStandings() = db.globalLeagueStandingDao().deleteAll()

    suspend fun purgeOldData(currentSeason: Int) = db.withTransaction {
        db.fixtureDao().deleteOldSeasonFixtures(currentSeason)
        db.transactionRecordDao().deleteOldTransactions(currentSeason)
        db.transferOrderDao().deleteOldOrders(currentSeason)
    }

    suspend fun saveLegend(legend: ClubLegend) = db.clubLegendDao().insertLegend(legend)
    suspend fun saveLegends(legends: List<ClubLegend>) = db.clubLegendDao().insertLegends(legends)
    suspend fun updateLegend(legend: ClubLegend) = db.clubLegendDao().updateLegend(legend)
    suspend fun getAllLegends(): List<ClubLegend> = db.clubLegendDao().getAllLegends()
    suspend fun deleteLegends() = db.clubLegendDao().deleteLegends()

    suspend fun saveRecord(record: HistoricalRecord) = db.historicalRecordDao().insertRecord(record)
    suspend fun saveRecords(records: List<HistoricalRecord>) = db.historicalRecordDao().insertRecords(records)
    suspend fun getAllHistoricalRecords() = db.historicalRecordDao().getAllRecords()
    suspend fun deleteRecords() = db.historicalRecordDao().deleteRecords()

    suspend fun saveOffers(offers: List<CoachOffer>) = db.coachOfferDao().insertOffers(offers)
    suspend fun getAllOffers(): List<CoachOffer> = db.coachOfferDao().getAllOffers()
    suspend fun deleteOffers() = db.coachOfferDao().deleteOffers()

    suspend fun getAllHistorico(): List<HistoricoEvolucao> = db.historicoEvolucaoDao().getAll()

    suspend fun getActiveInstallmentsForWeek(currentSeason: Int, currentWeek: Int): List<TransferInstallment> = db.transferInstallmentDao().getActiveInstallmentsForWeek(currentSeason, currentWeek)
    suspend fun getActiveInstallments(): List<TransferInstallment> = db.transferInstallmentDao().getActiveInstallments()
    suspend fun getAllInstallments(): List<TransferInstallment> = db.transferInstallmentDao().getAllInstallments()
    suspend fun saveInstallment(installment: TransferInstallment): Long = db.transferInstallmentDao().insertInstallment(installment)
    suspend fun saveInstallments(installments: List<TransferInstallment>) = db.transferInstallmentDao().insertInstallments(installments)
    suspend fun updateInstallment(installment: TransferInstallment) = db.transferInstallmentDao().updateInstallment(installment)
    suspend fun deleteInstallments() = db.transferInstallmentDao().deleteInstallments()

    suspend fun getActiveLoans(): List<PlayerLoan> = db.playerLoanDao().getActiveLoans()
    suspend fun getActiveLoanForPlayer(playerId: Long): PlayerLoan? = db.playerLoanDao().getActiveLoanForPlayer(playerId)
    suspend fun getAllLoans(): List<PlayerLoan> = db.playerLoanDao().getAllLoans()
    suspend fun saveLoan(loan: PlayerLoan): Long = db.playerLoanDao().insertLoan(loan)
    suspend fun saveLoans(loans: List<PlayerLoan>) = db.playerLoanDao().insertLoans(loans)
    suspend fun updateLoan(loan: PlayerLoan) = db.playerLoanDao().updateLoan(loan)
    suspend fun deleteLoans() = db.playerLoanDao().deleteLoans()
}
