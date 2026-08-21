package com.example.data

/**
 * Minimal ownership-aware market gates for transfer UI and offer generation.
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

/** A club can receive offers only for players it currently fields and owns outright. */
fun Player.isIncomingOfferCandidateFor(teamId: Long): Boolean =
    teamId > 0L && this.teamId == teamId && !isOnLoan
