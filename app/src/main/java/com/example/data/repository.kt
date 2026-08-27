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
        // careerGoals é cumulativo e deve sobreviver ao restart quando o histórico de carreira está
        // inicializado. Saves/fixtures legados podem, porém, carregar um careerGoals órfão com
        // careerApps=0; esse estado é inconsistente (gols de carreira sem nenhuma partida) e o
        // contrato histórico do restart o normaliza para zero. A condição preserva históricos
        // legítimos sem reintroduzir o reset global que apagava estatísticas cumulativas válidas.
        db.openHelper.writableDatabase.execSQL(
            "UPDATE players SET careerGoals = 0 WHERE careerApps = 0 AND careerGoals != 0"
        )
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
    suspend fun saveFixtures(fixtures: List<Fixture>) {
        if (fixtures.isEmpty()) return
        db.withTransaction {
            ensureFixtureTeamReferences(fixtures)
            val existing = db.fixtureDao().getFixturesForSeasons(fixtures.map { it.season }.distinct())
            FixtureScheduleValidator.requireCanAdd(existing, fixtures)
            if (fixtures.size > 100) {
                fixtures.chunked(100).forEach { db.fixtureDao().insertFixtures(it) }
            } else {
                db.fixtureDao().insertFixtures(fixtures)
            }
        }
    }
    suspend fun updateFixture(fixture: Fixture) = db.fixtureDao().updateFixture(fixture)
    suspend fun deleteFixtures() = db.fixtureDao().deleteFixtures()

    suspend fun saveRecord(record: HistoricalRecord) = db.historicalRecordDao().insertRecord(record)
    suspend fun getAllRecords(): List<HistoricalRecord> = db.historicalRecordDao().getAllRecords()
    suspend fun getRecordsByTeam(teamId: Long): List<HistoricalRecord> = db.historicalRecordDao().getRecordsByTeam(teamId)

    suspend fun saveOffer(offer: CoachOffer) = db.coachOfferDao().insertOffer(offer)
    suspend fun getAllOffers(): List<CoachOffer> = db.coachOfferDao().getCoachOffers()
    suspend fun deleteOffers() = db.coachOfferDao().deleteOffers()

    suspend fun saveLoan(loan: PlayerLoan) = db.playerLoanDao().insertLoan(loan)
    suspend fun getLoansForBorrower(teamId: Long): List<PlayerLoan> = db.playerLoanDao().getLoansForBorrower(teamId)
    suspend fun getLoansForOwner(teamId: Long): List<PlayerLoan> = db.playerLoanDao().getLoansForOwner(teamId)
    suspend fun deleteLoanByPlayer(playerId: Long) = db.playerLoanDao().deleteByPlayer(playerId)

    suspend fun saveClubLegend(legend: ClubLegend) = db.clubLegendDao().insertLegend(legend)
    suspend fun getClubLegends(teamId: Long): List<ClubLegend> = db.clubLegendDao().getLegendsForTeam(teamId)

    private suspend fun ensureFixtureTeamReferences(fixtures: List<Fixture>) {
        val referencedIds = fixtures
            .asSequence()
            .flatMap { sequenceOf(it.homeTeamId, it.awayTeamId) }
            .filter { it > 0L }
            .toSet()
        if (referencedIds.isEmpty()) return

        val existingIds = referencedIds
            .chunked(SQLITE_SAFE_IN_QUERY_SIZE)
            .flatMap { db.teamDao().getExistingTeamIds(it) }
            .toSet()
        val missingIds = referencedIds - existingIds
        if (missingIds.isEmpty()) return

        val virtualTeams = missingIds.mapNotNull(::virtualCompetitionTeam)
        val unresolved = missingIds - virtualTeams.map { it.id }.toSet()
        require(unresolved.isEmpty()) {
            "Fixtures com Team.id inexistente: ${unresolved.sorted().joinToString()}"
        }
        db.teamDao().insertTeams(virtualTeams)
    }

    companion object {
        private const val SQLITE_SAFE_IN_QUERY_SIZE = 900

        /**
         * Participantes virtuais já fazem parte do domínio histórico de competições; materializá-los
         * aqui mantém a FK sem transformar ids desconhecidos em clubes inventados.
         */
        private fun virtualCompetitionTeam(id: Long): Team? = when (id) {
            SuperMundialSystem.VIRTUAL_CLUB_ID -> Team(
                id = id,
                name = "Clube Virtual Mundial",
                city = "Virtual",
                state = "--",
                country = "Mundial",
                division = 1,
                rating = 70
            )
            else -> null
        }
    }
}
