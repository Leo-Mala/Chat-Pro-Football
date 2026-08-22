package com.example.data

/**
 * Minimal ownership-aware market gate for the UI.
 *
 * During an active loan, [Player.teamId] is the sporting borrower while [Player.originalTeamId]
 * carries the owner. A borrower may negotiate a permanent purchase, while the owner must never see
 * its own loaned-out player as an acquisition target. Incomplete loan state fails closed.
 */
fun Player.isTransferMarketCandidateFor(teamId: Long?): Boolean {
    if (teamId == null || teamId <= 0L) return false
    return if (isOnLoan) {
        val ownerTeamId = originalTeamId ?: return false
        ownerTeamId > 0L && ownerTeamId != teamId
    } else {
        this.teamId != teamId
    }
}

/**
 * True quando uma compra seria a conversão permanente de um jogador que já ocupa uma vaga no
 * roster esportivo do comprador. É uma projeção de UI: o use case ainda revalida PlayerLoan e
 * ownership dentro da transação antes de concluir a operação.
 */
fun Player.isInRosterLoanConversionFor(teamId: Long?): Boolean {
    if (teamId == null || teamId <= 0L) return false
    val borrowerTeamId = this.teamId ?: return false
    val ownerTeamId = originalTeamId ?: return false
    return isOnLoan &&
        borrowerTeamId == teamId &&
        ownerTeamId > 0L &&
        ownerTeamId != teamId
}

/**
 * Read-only UI projection for a club's players currently loaned to another roster.
 * This is intentionally stricter than checking [originalTeamId] alone: incomplete/quarantined loan
 * state and non-positive endpoints are never exposed as owner-manageable. Domain actions still
 * revalidate the persisted [PlayerLoan] transactionally before changing a contract or ownership.
 */
fun Player.isOwnedLoanedOutBy(teamId: Long?): Boolean {
    if (teamId == null || teamId <= 0L) return false
    val borrowerTeamId = this.teamId ?: return false
    return isOnLoan &&
        originalTeamId == teamId &&
        borrowerTeamId > 0L &&
        borrowerTeamId != teamId
}
