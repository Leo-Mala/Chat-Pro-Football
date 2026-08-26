package com.example.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * Erro de domínio para uma tentativa de reset/Novo Jogo que encontrou carreira persistida.
 * Diferente de CancellationException: o rollback continua ocorrendo, mas o chamador pode
 * reportar a falha ao usuário sem confundi-la com cancelamento real de lifecycle/coroutine.
 */
class ExistingCareerOverwriteBlockedException(
    val gameSaveRowCount: Int
) : IllegalStateException(
    "Exclusão parcial de GameSave bloqueada: $gameSaveRowCount linha(s) preservada(s); remova explicitamente o banco do slot."
)

class GameRepository(internal val db: AppDatabase) {
    suspend fun <R> withTransaction(block: suspend () -> R): R =
        try {
            db.withTransaction(block)
        } finally {
            // Um plano factual de novo save é estritamente efêmero e nunca pode vazar para
            // reparos, carregamentos ou transações posteriores do mesmo slot.
            EuropeanNewSaveSeedCoordinator.clear(this)
        }

    suspend fun <R> runInTransaction(block: suspend () -> R): R = db.withTransaction(block)
    val gameSaveFlow: Flow<GameSave?> = db.gameSaveDao().getGameSaveFlow()
    val allTeamsFlow: Flow<List<Team>> = db.teamDao().getAllTeamsFlow()
    fun getTeamsByLeagueFlow(leagueCountry: String): Flow<List<Team>> = db.teamDao().getTeamsByLeagueFlow(leagueCountry)
    fun getTeamsByCountryDivisionFlow(country: String, division: Int): Flow<List<Team>> =
        db.teamDao().getTeamsByCountryDivisionFlow(country, division)
    fun getTeamFlow(teamId: Long): Flow<Team?> = db.teamDao().getTeamFlow(teamId)
    fun getTeamsByIdsFlow(ids: List<Long>): Flow<List<Team>> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return flowOf(emptyList())
        val chunkFlows = distinctIds
            .chunked(SQLITE_SAFE_IN_QUERY_SIZE)
            .map { chunk -> db.teamDao().getTeamsByIdsFlow(chunk) }
        if (chunkFlows.size == 1) return chunkFlows.single()
        return combine(chunkFlows) { chunks ->
            chunks
                .asSequence()
                .flatMap { it.asSequence() }
                .distinctBy { it.id }
                .sortedBy { it.name }
                .toList()
        }
    }
    val allPlayersFlow: Flow<List<Player>> = db.playerDao().getAllPlayersFlow()
    val allFixturesFlow: Flow<List<Fixture>> = db.fixtureDao().getFixturesFlow()
    val allRecordsFlow: Flow<List<HistoricalRecord>> = db.historicalRecordDao().getAllRecordsFlow()
    val coachOffersFlow: Flow<List<CoachOffer>> = db.coachOfferDao().getCoachOffersFlow()
    val allTransactionsFlow: Flow<List<TransactionRecord>> = db.transactionRecordDao().getAllTransactionsFlow()
    val allOrdersFlow: Flow<List<TransferOrder>> = db.transferOrderDao().getAllOrdersFlow()

    fun getPlayersForTeamFlow(teamId: Long?): Flow<List<Player>> = db.playerDao().getPlayersByTeamFlow(teamId)
    fun getLegendsForTeamFlow(teamId: Long): Flow<List<ClubLegend>> = db.clubLegendDao().getLegendsForTeamFlow(teamId)
    fun getFixturesForWeekFlow(season: Int, week: Int): Flow<List<Fixture>> = db.fixtureDao().getFixturesForWeekFlow(season, week)
    fun getPlayedFixturesForCompetitionFlow(season: Int, competitionType: String): Flow<List<Fixture>> =
        db.fixtureDao().getPlayedFixturesForCompetitionFlow(season, competitionType)
    fun getNextFixtureForTeamFlow(season: Int, week: Int, teamId: Long): Flow<Fixture?> =
        db.fixtureDao().getNextFixtureForTeamFlow(season, week, teamId)

    suspend fun getGameSave(): GameSave? = db.gameSaveDao().getGameSave()
    suspend fun saveGameSave(save: GameSave) = db.gameSaveDao().insertOrUpdate(save)

    /**
     * Não permite transformar uma carreira existente em "slot vazio" por uma exclusão parcial.
     *
     * Novo Jogo só usa esta limpeza quando o slot já foi semanticamente classificado como vazio.
     * Qualquer linha em `game_save`, inclusive uma linha não-canônica resultante de corrupção ou
     * restore parcial, bloqueia a limpeza. A remoção de carreira continua sendo exclusivamente o
     * fluxo explícito de exclusão física do slot.
     */
    suspend fun deleteSave() = db.withTransaction {
        val gameSaveRowCount = db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM game_save")
            .use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        if (gameSaveRowCount > 0) {
            throw ExistingCareerOverwriteBlockedException(gameSaveRowCount)
        }
        db.gameSaveDao().deleteSave()
    }

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

    /**
     * Reinicia o estado esportivo da temporada em uma única transação.
     *
     * O snapshot de [GameSave] é relido já dentro da transação para que um comando de reinício
     * nunca sobrescreva silenciosamente saldo, contratos ou transferências persistidos antes de a
     * transação adquirir o banco. O estado sazonal de Player é zerado por action-set SQL, sem
     * materializar a tabela inteira. Se temporada ou clube mudaram, a operação falha fechada.
     */
    suspend fun restartSeasonStateAtomically(
        expectedSeason: Int,
        expectedPlayerTeamId: Long,
        replacementFixtures: List<Fixture>
    ): Boolean = db.withTransaction {
        val currentSave = db.gameSaveDao().getGameSave() ?: return@withTransaction false
        if (currentSave.currentSeason != expectedSeason ||
            currentSave.playerTeamId != expectedPlayerTeamId
        ) {
            return@withTransaction false
        }

        require(replacementFixtures.all { it.season == expectedSeason }) {
            "Reinício de temporada recebeu fixtures de outra temporada."
        }

        db.gameSaveDao().insertOrUpdate(
            currentSave.copy(currentWeek = 1, isGameOver = false)
        )
        db.fixtureDao().deleteFixtures()

        if (replacementFixtures.isNotEmpty()) {
            ensureFixtureTeamReferences(replacementFixtures)
            FixtureScheduleValidator.requireCanAdd(emptyList(), replacementFixtures)
            if (replacementFixtures.size > 100) {
                replacementFixtures.chunked(100).forEach { db.fixtureDao().insertFixtures(it) }
            } else {
                db.fixtureDao().insertFixtures(replacementFixtures)
            }
        }

        db.playerDao().resetSeasonState()
        db.coachOfferDao().deleteOffers()
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
        val teamsToPersist = EuropeanNewSaveSeedCoordinator.teamsFor(this@GameRepository, teams)
        if (teamsToPersist.isNotEmpty()) {
            db.teamDao().insertTeams(teamsToPersist)
        }
    }
    suspend fun updateTeam(team: Team) = db.teamDao().updateTeam(team)
    suspend fun deleteTeam(id: Long) = db.teamDao().deleteTeam(id)
    suspend fun deleteTeams() = db.teamDao().deleteTeams()

    suspend fun getAllPlayers(): List<Player> = db.playerDao().getAllPlayers()
    suspend fun getPlayersByTeam(teamId: Long?): List<Player> = db.playerDao().getPlayersByTeam(teamId)
    suspend fun getPlayerCountByTeam(teamId: Long?): Int = db.playerDao().getPlayerCountByTeam(teamId)
    suspend fun getFreeAgents(): List<Player> = db.playerDao().getFreeAgents()
    suspend fun getPlayer(id: Long): Player? = db.playerDao().getPlayer(id)

    /**
     * Aplica a única regressão semanal de contratos sem materializar toda a tabela Player.
     * A ordem é intencional: contratos em 1 semana expiram antes do decremento dos vínculos > 1,
     * evitando que um contrato com 2 semanas expire no mesmo tick.
     */
    suspend fun processWeeklyPlayerContractTick() = db.withTransaction {
        val players = db.playerDao()
        players.expireLoanContractsAtOneWeek()
        players.expireNonLoanContractsAtOneWeek()
        players.decrementLongerContractsOneWeek()
    }
    suspend fun insertPlayersIfNotExists(players: List<Player>) = db.withTransaction {
        if (players.size > 100) {
            players.chunked(100).forEach { db.playerDao().insertPlayers(it) }
        } else {
            db.playerDao().insertPlayers(players)
        }
    }
    suspend fun savePlayers(players: List<Player>) = db.withTransaction {
        val seed = EuropeanNewSaveSeedCoordinator.consumePlayers(this@GameRepository, players)
        val playersToPersist = seed.players
        if (playersToPersist.isNotEmpty()) {
            db.playerDao().insertPlayersReplace(playersToPersist)
        }
        if (seed.loans.isNotEmpty()) {
            db.playerLoanDao().insertLoans(seed.loans)
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
    fun getJogadoresPorNota(teamId: Long?): Flow<List<Player>> = db.playerDao().getJogadoresPorNota(teamId)
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
    suspend fun getFixture(id: Long): Fixture? = db.fixtureDao().getFixture(id)

    /**
     * Toda criação de fixture passa pela mesma barreira de calendário e, desde V21, pela barreira
     * relacional. Participantes virtuais conhecidos são materializados em Team antes do insert;
     * referências desconhecidas são recusadas em vez de persistir corrupção.
     */
    suspend fun saveFixtures(fixtures: List<Fixture>) = db.withTransaction {
        if (fixtures.isEmpty()) return@withTransaction
        ensureFixtureTeamReferences(fixtures)
        val existing = fixtures
            .map { it.season }
            .distinct()
            .flatMap { db.fixtureDao().getFixturesForSeason(it) }
        FixtureScheduleValidator.requireCanAdd(existing, fixtures)

        db.fixtureDao().insertFixtures(fixtures)
    }

    /**
     * Alterar somente placar/eventos de um fixture já persistido não reabre a validação temporal.
     * Isso permite que saves V19 continuem jogáveis mesmo se carregarem uma colisão histórica.
     * Qualquer remarcação de semana/slot/clubes continua obrigada a passar pelo validador.
     */
    suspend fun updateFixture(fixture: Fixture) = db.withTransaction {
        ensureFixtureTeamReferences(listOf(fixture))
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
        ensureFixtureTeamReferences(fixtures)

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

    private suspend fun ensureFixtureTeamReferences(fixtures: List<Fixture>) {
        if (fixtures.isEmpty()) return
        val requiredIds = fixtures
            .flatMap { listOf(it.homeTeamId, it.awayTeamId) }
            .toSet()
        require(requiredIds.none { it <= 0L }) {
            "Fixture não pode referenciar teamId <= 0."
        }

        // Android devices may expose SQLite builds capped at 999 bind parameters. Keep every IN
        // query comfortably below that limit so a full-career calendar with thousands of clubs
        // cannot fail before fixture insertion reaches its own chunked writes.
        val persistedIds = requiredIds
            .toList()
            .chunked(SQLITE_SAFE_IN_QUERY_SIZE)
            .flatMap { ids -> db.teamDao().getExistingTeamIds(ids) }
            .toHashSet()
        val missing = requiredIds.filterNot { it in persistedIds }.sorted()
        if (missing.isEmpty()) return

        val invalidMissing = missing.filterNot(GlobalFootballSystem::isGeneratedVirtualTeamId)
        require(invalidMissing.isEmpty()) {
            "Fixture referencia clubes não persistidos fora do namespace virtual: $invalidMissing"
        }

        db.teamDao().insertTeams(missing.map(GlobalFootballSystem::getVirtualTeam))
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

    /**
     * Substitui o snapshot global como unidade atômica. A combinação delete + chunks + limpeza
     * histórica não pode deixar uma temporada parcialmente persistida se qualquer insert falhar.
     */
    suspend fun saveGlobalStandingsForSeason(
        season: Int,
        rows: List<GlobalLeagueStanding>
    ) = db.withTransaction {
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

    private companion object {
        const val SQLITE_SAFE_IN_QUERY_SIZE = 900
    }
}
