package com.example.ui.viewmodel

import com.example.data.Fixture

internal fun shouldRecoverInterruptedWeeklyClose(
    fixtures: List<Fixture>,
    userTeamId: Long
): Boolean {
    val userFixtures = fixtures.filter { fixture ->
        fixture.homeTeamId == userTeamId || fixture.awayTeamId == userTeamId
    }
    return userFixtures.isNotEmpty() && userFixtures.all { it.isPlayed }
}

private fun GameViewModel.isRecoverySessionActive(session: SaveSession): Boolean {
    val active = activeSaveSession.value ?: return false
    return active.slotId == session.slotId &&
        active.generation == session.generation &&
        active.repository === session.repository &&
        currentSaveId.value == session.slotId
}

/**
 * Completa somente a metade durável de uma semana que já teve a partida do usuário persistida.
 * Sem partida do usuário na semana (folga) ou com partida ainda pendente, a abertura é passiva.
 *
 * O repositório é capturado da SaveSession e nunca é resolvido novamente pelo slot ativo durante a
 * recuperação. Uma troca de carreira pode interromper a recuperação antes do fechamento, mas não
 * pode redirecionar CPU fixtures/finanças/evolução para outro banco.
 */
internal suspend fun GameViewModel.recoverInterruptedWeeklyCloseIfNeeded(
    session: SaveSession
): Boolean {
    if (!isRecoverySessionActive(session)) return false
    val targetRepo = session.repository
    val before = targetRepo.getGameSave() ?: return false
    var fixtures = targetRepo.getFixturesForWeek(before.currentSeason, before.currentWeek)
    if (!shouldRecoverInterruptedWeeklyClose(fixtures, before.playerTeamId)) return false

    simulateCpuMatchesForCurrentWeek(targetRepo)
    if (!isRecoverySessionActive(session)) return false

    fixtures = targetRepo.getFixturesForWeek(before.currentSeason, before.currentWeek)
    if (fixtures.any { !it.isPlayed }) return false

    processWeekEndEconomicAndEvolution(targetRepo)
    val after = targetRepo.getGameSave() ?: return false
    return after.currentSeason != before.currentSeason || after.currentWeek != before.currentWeek
}
