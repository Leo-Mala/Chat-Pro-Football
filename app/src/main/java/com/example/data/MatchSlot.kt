package com.example.data

import androidx.room.TypeConverter

/**
 * Dois momentos canônicos de jogo dentro de cada semana da carreira.
 *
 * A semana continua sendo a unidade de processamento financeiro, contratual e de evolução.
 * O slot existe para permitir, com segurança, uma partida no meio da semana e outra no fim de
 * semana sem transformar cada jogo em uma nova "semana" econômica.
 */
enum class MatchSlot(val order: Int) {
    MIDWEEK(0),
    WEEKEND(1)
}

class MatchSlotConverter {
    @TypeConverter
    fun fromMatchSlot(value: MatchSlot): String = value.name

    @TypeConverter
    fun toMatchSlot(value: String): MatchSlot =
        runCatching { MatchSlot.valueOf(value) }.getOrDefault(MatchSlot.WEEKEND)
}

/**
 * Valida a topologia temporal dos fixtures antes de persistência.
 *
 * Regras centrais:
 * - semanas sempre ficam dentro do calendário canônico;
 * - um clube pode jogar MIDWEEK + WEEKEND na mesma semana;
 * - um clube nunca pode ocupar duas partidas no mesmo slot;
 * - a mesma partida não pode ser inserida duas vezes.
 *
 * [requireCanAdd] não reprova conflitos que já existiam em um save legado; ele impede que uma
 * progressão nova acrescente outro conflito. A migração V19->V20 também classifica fixtures
 * legados por tipo para reduzir colisões pré-existentes.
 */
object FixtureScheduleValidator {

    private data class SlotKey(
        val season: Int,
        val week: Int,
        val slot: MatchSlot
    )

    private data class FixtureKey(
        val season: Int,
        val week: Int,
        val slot: MatchSlot,
        val competitionType: String,
        val firstTeamId: Long,
        val secondTeamId: Long
    )

    fun requireValid(fixtures: List<Fixture>) {
        val occupied = mutableMapOf<SlotKey, MutableSet<Long>>()
        val fixtureKeys = mutableSetOf<FixtureKey>()

        fixtures.forEach { fixture ->
            require(fixture.week in 1..GameCalendar.WEEKS_PER_SEASON) {
                "Fixture ${fixture.id} fora do calendário: temporada ${fixture.season}, semana ${fixture.week}. " +
                    "Esperado 1..${GameCalendar.WEEKS_PER_SEASON}."
            }
            require(fixture.homeTeamId != fixture.awayTeamId) {
                "Fixture inválido: clube ${fixture.homeTeamId} enfrenta a si mesmo na temporada " +
                    "${fixture.season}, semana ${fixture.week}, ${fixture.matchSlot}."
            }

            val first = minOf(fixture.homeTeamId, fixture.awayTeamId)
            val second = maxOf(fixture.homeTeamId, fixture.awayTeamId)
            val fixtureKey = FixtureKey(
                season = fixture.season,
                week = fixture.week,
                slot = fixture.matchSlot,
                competitionType = fixture.competitionType,
                firstTeamId = first,
                secondTeamId = second
            )
            require(fixtureKeys.add(fixtureKey)) {
                "Fixture duplicado em ${fixture.season}/W${fixture.week}/${fixture.matchSlot}: " +
                    "${fixture.homeTeamId} x ${fixture.awayTeamId} (${fixture.competitionType})."
            }

            val slotKey = SlotKey(fixture.season, fixture.week, fixture.matchSlot)
            val teams = occupied.getOrPut(slotKey) { mutableSetOf() }
            require(teams.add(fixture.homeTeamId)) {
                conflictMessage(fixture.homeTeamId, fixture)
            }
            require(teams.add(fixture.awayTeamId)) {
                conflictMessage(fixture.awayTeamId, fixture)
            }
        }
    }

    fun requireCanAdd(existing: List<Fixture>, additions: List<Fixture>) {
        if (additions.isEmpty()) return
        requireValid(additions)

        val occupied = mutableMapOf<SlotKey, MutableSet<Long>>()
        val existingFixtureKeys = mutableSetOf<FixtureKey>()

        existing.forEach { fixture ->
            if (fixture.week !in 1..GameCalendar.WEEKS_PER_SEASON) return@forEach
            val slotKey = SlotKey(fixture.season, fixture.week, fixture.matchSlot)
            occupied.getOrPut(slotKey) { mutableSetOf() }.apply {
                add(fixture.homeTeamId)
                add(fixture.awayTeamId)
            }
            existingFixtureKeys += fixtureKey(fixture)
        }

        additions.forEach { fixture ->
            require(fixtureKey(fixture) !in existingFixtureKeys) {
                "Tentativa de persistir fixture já existente em ${fixture.season}/W${fixture.week}/${fixture.matchSlot}: " +
                    "${fixture.homeTeamId} x ${fixture.awayTeamId} (${fixture.competitionType})."
            }

            val slotKey = SlotKey(fixture.season, fixture.week, fixture.matchSlot)
            val teams = occupied.getOrPut(slotKey) { mutableSetOf() }
            require(fixture.homeTeamId !in teams) {
                conflictMessage(fixture.homeTeamId, fixture)
            }
            require(fixture.awayTeamId !in teams) {
                conflictMessage(fixture.awayTeamId, fixture)
            }
            teams += fixture.homeTeamId
            teams += fixture.awayTeamId
        }
    }

    fun chronologicalComparator(): Comparator<Fixture> =
        compareBy<Fixture> { it.season }
            .thenBy { it.week }
            .thenBy { it.matchSlot.order }
            .thenBy { it.id }

    private fun fixtureKey(fixture: Fixture): FixtureKey = FixtureKey(
        season = fixture.season,
        week = fixture.week,
        slot = fixture.matchSlot,
        competitionType = fixture.competitionType,
        firstTeamId = minOf(fixture.homeTeamId, fixture.awayTeamId),
        secondTeamId = maxOf(fixture.homeTeamId, fixture.awayTeamId)
    )

    private fun conflictMessage(teamId: Long, fixture: Fixture): String =
        "Conflito de calendário: clube $teamId já possui partida em " +
            "${fixture.season}/W${fixture.week}/${fixture.matchSlot}; " +
            "não é permitido disputar duas partidas no mesmo slot."
}
