package com.example.data

/** Origem tipada de uma vaga para uma competição. */
sealed class QualificationSource {
    data class LeaguePosition(val position: Int) : QualificationSource() {
        init { require(position > 0) }
    }

    data class NationalCupWinner(val country: String) : QualificationSource()

    data class ContinentalChampion(val competition: CompetitionIdentity) : QualificationSource()

    data class HostAssociation(val country: String) : QualificationSource()

    data class AssociationSlot(val association: String, val slot: Int) : QualificationSource() {
        init { require(slot > 0) }
    }

    data class ExplicitRule(val code: String) : QualificationSource() {
        init { require(code.isNotBlank()) }
    }
}

data class QualificationSlot(
    val source: QualificationSource,
    val destinationCompetition: CompetitionIdentity,
    val ordinal: Int = 1
) {
    init { require(ordinal > 0) }
}
