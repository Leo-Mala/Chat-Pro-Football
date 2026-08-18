package com.example.data

/**
 * Empréstimo factual da temporada-base.
 *
 * A identidade do jogador continua independente do clube. O jogador atua pelo [borrowerClubName]
 * (`Player.teamId`) enquanto `PlayerLoan.ownerTeamId` preserva o proprietário contratual.
 *
 * Este modelo reutiliza a entidade Room `PlayerLoan` existente no schema V21; não exige nova tabela
 * nem migration. Os snapshots são usados somente na criação de novos saves.
 */
data class EuropeanRealLoanSnapshot(
    val player: EuropeanRealPlayerTemplate,
    val ownerCountry: String,
    val ownerClubName: String,
    val borrowerCountry: String,
    val borrowerClubName: String,
    val season: Int,
    val startWeek: Int,
    val durationWeeks: Int,
    val verifiedAsOfIso: String,
    val sourceRefs: List<String>
) {
    init {
        require(season > 0)
        require(startWeek in 1..GameCalendar.WEEKS_PER_SEASON)
        require(durationWeeks > 0)
        require(ISO_DATE.matches(verifiedAsOfIso)) {
            "verifiedAsOfIso deve usar YYYY-MM-DD: $verifiedAsOfIso"
        }
        require(sourceRefs.isNotEmpty() && sourceRefs.none { it.isBlank() }) {
            "Empréstimo factual precisa registrar ao menos uma fonte."
        }
        require(ownerTeamId != borrowerTeamId) {
            "Proprietário e tomador não podem ser o mesmo clube."
        }
    }

    val ownerTeamId: Long
        get() = requireStableTeam(ownerCountry, ownerClubName, "proprietário")

    val borrowerTeamId: Long
        get() = requireStableTeam(borrowerCountry, borrowerClubName, "tomador")

    fun toBorrowerPlayer(borrowerTeamRating: Int): Player =
        player.toGameplayPlayer(teamId = borrowerTeamId, teamRating = borrowerTeamRating).copy(
            isOnLoan = true,
            loanWeeksRemaining = durationWeeks,
            originalTeamId = ownerTeamId
        )

    fun toPlayerLoan(): PlayerLoan = PlayerLoan(
        playerId = player.stableId,
        ownerTeamId = ownerTeamId,
        borrowerTeamId = borrowerTeamId,
        startSeason = season,
        startWeek = startWeek,
        durationWeeks = durationWeeks,
        remainingWeeks = durationWeeks,
        weeklyFee = 0L,
        buyoutOptionPrice = null,
        status = "ACTIVE"
    )

    private fun requireStableTeam(country: String, club: String, role: String): Long =
        requireNotNull(StableTeamIdentityRegistry.idFor(country, club)) {
            "Clube $role sem teamId factual estável: $country/$club"
        }

    companion object {
        private val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}

/** Catálogo imutável que impede dois empréstimos ativos para a mesma identidade factual. */
class EuropeanRealLoanCatalog(
    loans: List<EuropeanRealLoanSnapshot>
) {
    private val byPlayerId: Map<Long, EuropeanRealLoanSnapshot>

    init {
        val ids = loans.map { it.player.stableId }
        require(ids.distinct().size == ids.size) {
            "Um jogador factual possui mais de um empréstimo ativo no snapshot inicial."
        }
        byPlayerId = loans.associateBy { it.player.stableId }
    }

    fun find(playerId: Long): EuropeanRealLoanSnapshot? = byPlayerId[playerId]

    fun all(): List<EuropeanRealLoanSnapshot> = byPlayerId.values.toList()

    fun materializePlayers(teamRatings: Map<Long, Int>): List<Player> = byPlayerId.values.map { loan ->
        val rating = requireNotNull(teamRatings[loan.borrowerTeamId]) {
            "Rating interno ausente para clube tomador ${loan.borrowerClubName}."
        }
        loan.toBorrowerPlayer(rating)
    }

    fun materializeLoans(): List<PlayerLoan> = byPlayerId.values.map { it.toPlayerLoan() }
}

/**
 * Primeiro empréstimo factual auditado: Andre Onana pertence ao Manchester United e voltou ao
 * Trabzonspor por empréstimo para 2026/27. A fonte oficial do United descreve o acordo como novo
 * empréstimo por uma temporada.
 */
object AndreOnanaLoan2026_27 {
    val snapshot = EuropeanRealLoanSnapshot(
        player = EuropeanRealPlayerTemplate(
            fullName = "Andre Onana",
            birthDateIso = "1996-06-02",
            nationality = "Cameroon",
            position = "GOL"
        ),
        ownerCountry = "Inglaterra",
        ownerClubName = "Manchester United",
        borrowerCountry = "Turquia",
        borrowerClubName = "Trabzonspor",
        season = 2026,
        startWeek = 1,
        durationWeeks = GameCalendar.WEEKS_PER_SEASON,
        verifiedAsOfIso = "2026-08-18",
        sourceRefs = listOf(
            "https://www.manutd.com/en/teams/mens-team/andre-onana"
        )
    )
}

object EuropeanRealLoans {
    val catalog = EuropeanRealLoanCatalog(
        listOf(AndreOnanaLoan2026_27.snapshot)
    )
}
