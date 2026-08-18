package com.example.data

/**
 * Estrutura tipada das fases de acesso das competições UEFA 2026/27.
 *
 * Este modelo não agenda partidas ainda. Ele separa competição, caminho e fase para que a
 * implementação das qualificatórias não volte a depender de strings genéricas T1/T2/T3.
 */
enum class UefaCompetitionCode {
    UCL,
    UEL,
    UECL
}

enum class UefaQualificationPath {
    CHAMPIONS,
    LEAGUE,
    MAIN
}

enum class UefaQualificationStage {
    Q1,
    Q2,
    Q3,
    PLAYOFF,
    LEAGUE_PHASE
}

data class UefaEntryPoint(
    val competition: UefaCompetitionCode,
    val stage: UefaQualificationStage,
    val path: UefaQualificationPath? = null
) {
    init {
        if (stage == UefaQualificationStage.LEAGUE_PHASE) {
            require(path == null) { "League phase não possui caminho qualificatório." }
        }
    }
}

data class UefaEliminationTransfer(
    val from: UefaEntryPoint,
    val to: UefaEntryPoint
)

object UefaQualificationStructure {

    fun stagesFor(
        competition: UefaCompetitionCode,
        path: UefaQualificationPath
    ): List<UefaQualificationStage> = when (competition to path) {
        UefaCompetitionCode.UCL to UefaQualificationPath.CHAMPIONS ->
            listOf(UefaQualificationStage.Q1, UefaQualificationStage.Q2, UefaQualificationStage.Q3, UefaQualificationStage.PLAYOFF)
        UefaCompetitionCode.UCL to UefaQualificationPath.LEAGUE ->
            listOf(UefaQualificationStage.Q2, UefaQualificationStage.Q3, UefaQualificationStage.PLAYOFF)
        UefaCompetitionCode.UEL to UefaQualificationPath.MAIN ->
            listOf(UefaQualificationStage.Q1, UefaQualificationStage.Q2, UefaQualificationStage.Q3, UefaQualificationStage.PLAYOFF)
        UefaCompetitionCode.UEL to UefaQualificationPath.CHAMPIONS ->
            listOf(UefaQualificationStage.Q3, UefaQualificationStage.PLAYOFF)
        UefaCompetitionCode.UECL to UefaQualificationPath.MAIN ->
            listOf(UefaQualificationStage.Q1, UefaQualificationStage.Q2, UefaQualificationStage.Q3, UefaQualificationStage.PLAYOFF)
        UefaCompetitionCode.UECL to UefaQualificationPath.CHAMPIONS ->
            listOf(UefaQualificationStage.Q2, UefaQualificationStage.Q3, UefaQualificationStage.PLAYOFF)
        else -> emptyList()
    }

    /**
     * Transferências de clubes eliminados previstas pelos regulamentos 2026/27.
     *
     * O UCL Champions Path Q3 desemboca nos play-offs da UEL, cujo quadro contém clubes dos
     * caminhos champions e main. Por isso o destino é deliberadamente sem [path] até que a
     * access list/solver de sorteio da B2.1 materialize o subquadro correto.
     */
    val eliminationTransfers: List<UefaEliminationTransfer> = listOf(
        transfer(UefaCompetitionCode.UCL, UefaQualificationStage.Q1, UefaQualificationPath.CHAMPIONS,
            UefaCompetitionCode.UECL, UefaQualificationStage.Q2, UefaQualificationPath.CHAMPIONS),
        transfer(UefaCompetitionCode.UCL, UefaQualificationStage.Q2, UefaQualificationPath.CHAMPIONS,
            UefaCompetitionCode.UEL, UefaQualificationStage.Q3, UefaQualificationPath.CHAMPIONS),
        transfer(UefaCompetitionCode.UCL, UefaQualificationStage.Q3, UefaQualificationPath.CHAMPIONS,
            UefaCompetitionCode.UEL, UefaQualificationStage.PLAYOFF, null),
        transfer(UefaCompetitionCode.UCL, UefaQualificationStage.PLAYOFF, UefaQualificationPath.CHAMPIONS,
            UefaCompetitionCode.UEL, UefaQualificationStage.LEAGUE_PHASE, null),

        transfer(UefaCompetitionCode.UCL, UefaQualificationStage.Q2, UefaQualificationPath.LEAGUE,
            UefaCompetitionCode.UEL, UefaQualificationStage.Q3, UefaQualificationPath.MAIN),
        transfer(UefaCompetitionCode.UCL, UefaQualificationStage.Q3, UefaQualificationPath.LEAGUE,
            UefaCompetitionCode.UEL, UefaQualificationStage.LEAGUE_PHASE, null),
        transfer(UefaCompetitionCode.UCL, UefaQualificationStage.PLAYOFF, UefaQualificationPath.LEAGUE,
            UefaCompetitionCode.UEL, UefaQualificationStage.LEAGUE_PHASE, null),

        transfer(UefaCompetitionCode.UEL, UefaQualificationStage.Q1, UefaQualificationPath.MAIN,
            UefaCompetitionCode.UECL, UefaQualificationStage.Q2, UefaQualificationPath.MAIN),
        transfer(UefaCompetitionCode.UEL, UefaQualificationStage.Q2, UefaQualificationPath.MAIN,
            UefaCompetitionCode.UECL, UefaQualificationStage.Q3, UefaQualificationPath.MAIN),
        transfer(UefaCompetitionCode.UEL, UefaQualificationStage.Q3, UefaQualificationPath.MAIN,
            UefaCompetitionCode.UECL, UefaQualificationStage.PLAYOFF, UefaQualificationPath.MAIN),
        transfer(UefaCompetitionCode.UEL, UefaQualificationStage.Q3, UefaQualificationPath.CHAMPIONS,
            UefaCompetitionCode.UECL, UefaQualificationStage.PLAYOFF, UefaQualificationPath.CHAMPIONS),
        transfer(UefaCompetitionCode.UEL, UefaQualificationStage.PLAYOFF, UefaQualificationPath.MAIN,
            UefaCompetitionCode.UECL, UefaQualificationStage.LEAGUE_PHASE, null),
        transfer(UefaCompetitionCode.UEL, UefaQualificationStage.PLAYOFF, UefaQualificationPath.CHAMPIONS,
            UefaCompetitionCode.UECL, UefaQualificationStage.LEAGUE_PHASE, null)
    )

    fun destinationAfterElimination(from: UefaEntryPoint): UefaEntryPoint? =
        eliminationTransfers.singleOrNull { it.from == from }?.to

    private fun transfer(
        fromCompetition: UefaCompetitionCode,
        fromStage: UefaQualificationStage,
        fromPath: UefaQualificationPath,
        toCompetition: UefaCompetitionCode,
        toStage: UefaQualificationStage,
        toPath: UefaQualificationPath?
    ): UefaEliminationTransfer = UefaEliminationTransfer(
        from = UefaEntryPoint(fromCompetition, fromStage, fromPath),
        to = UefaEntryPoint(toCompetition, toStage, toPath)
    )
}
