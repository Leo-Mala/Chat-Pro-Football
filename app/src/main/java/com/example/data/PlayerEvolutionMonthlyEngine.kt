package com.example.data

import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Allocation-conscious monthly evolution engine for the ~60k-player career universe.
 *
 * This intentionally preserves the gameplay rules and Random call pattern of
 * [PlayerEvolutionSystem.processMonthlyEvolution], while replacing the per-player 35-entry
 * MutableMap and JSONObject serialization with an IntArray and direct JSON serialization.
 * The canonical post-match evolution path remains in [PlayerEvolutionSystem].
 */
object PlayerEvolutionMonthlyEngine {
    private const val ATTRIBUTE_COUNT = 35

    private val physicalAttributeKeys = listOf(
        "agilidade", "impulsao", "forca", "velocidade", "aceleracao", "resistencia"
    )

    private val selectableAttributeKeys = listOf(
        "reflexos", "pegada", "umContraUm", "saidaDeGol", "lancamento", "desarme",
        "marcacao", "cabeceio", "passeCurto", "cruzamento", "drible", "passe",
        "primeiroToque", "finalizacao", "chuteDeLonge", "controleBola",
        "posicionamento", "concentracao", "sangueFrio", "antecipacao", "bravura",
        "trabalhoEquipe", "decisao", "semBola", "visaoJogo", "criatividade",
        "agressividade", "lideranca", "regularidade",
        "agilidade", "impulsao", "forca", "velocidade", "aceleracao", "resistencia"
    )

    private val primaryAttributesByPosition = mapOf(
        Posicao.GOLEIRO to listOf("reflexos", "pegada", "umContraUm", "saidaDeGol", "posicionamento", "agilidade"),
        Posicao.ZAGUEIRO to listOf("desarme", "marcacao", "cabeceio", "posicionamento", "forca", "impulsao"),
        Posicao.LATERAL to listOf("cruzamento", "desarme", "velocidade", "aceleracao", "resistencia", "passe"),
        Posicao.VOLANTE to listOf("desarme", "marcacao", "passe", "resistencia", "posicionamento", "forca"),
        Posicao.MEIA to listOf("passe", "visaoJogo", "criatividade", "drible", "primeiroToque", "aceleracao"),
        Posicao.ATACANTE to listOf("finalizacao", "velocidade", "aceleracao", "drible", "sangueFrio", "cabeceio")
    )

    /**
     * Compatibility path: returns a result for every processed player.
     */
    fun process(
        players: List<Player>,
        teamsMap: Map<Long, Team>,
        periodDate: String = "2026-01"
    ): List<PlayerEvolutionResult> = processInternal(
        players = players,
        teamsMap = teamsMap,
        periodDate = periodDate,
        retainUnchangedResults = true
    )

    /**
     * Production monthly-planning path. It executes the exact same rules and Random calls as
     * [process], but retains heavy Player/Atributos/result objects only for players whose persisted
     * evolution state actually changes. Players without a delta are handled by the set-based
     * monthly counter reset at commit time, so keeping ~60k no-op result objects has no semantic
     * value and creates a large avoidable heap spike every fourth week.
     */
    fun processChanged(
        players: List<Player>,
        teamsMap: Map<Long, Team>,
        periodDate: String = "2026-01"
    ): List<PlayerEvolutionResult> = processInternal(
        players = players,
        teamsMap = teamsMap,
        periodDate = periodDate,
        retainUnchangedResults = false
    )

    private fun processInternal(
        players: List<Player>,
        teamsMap: Map<Long, Team>,
        periodDate: String,
        retainUnchangedResults: Boolean
    ): List<PlayerEvolutionResult> {
        val results = ArrayList<PlayerEvolutionResult>(
            if (retainUnchangedResults) players.size else minOf(players.size, 4096)
        )

        for (player in players) {
            val team = teamsMap[player.teamId]
            val ctLevel = team?.trainingCenterLevel ?: 1
            val ctMult = PlayerEvolutionSystem.getCTMultiplier(ctLevel)

            val oldAtributos = player.getAtributosObject()
            val values = oldAtributos.toMutableValues()
            val posEnum = Posicao.fromCode(player.position)
            val ageFactor = PlayerEvolutionSystem.calculateAgeFactor(player.age)
            val minutesFactor = PlayerEvolutionSystem.calculateMinutesFactor(player.minutosJogados)
            val ratingFactor = PlayerEvolutionSystem.calculateRatingFactor(player.mediaNotas)
            val targetPotential = player.potential.coerceIn(50, 99)
            val historyLogs = ArrayList<HistoricoEvolucao>(4)

            if (ageFactor > 0) {
                val primaryAttrList = primaryAttributesByPosition.getValue(posEnum)
                val selectedList = LinkedHashSet<String>(4)

                // Preserve the legacy exact-key behavior: focoTreino is lower-cased before this
                // membership check, while camelCase attribute keys remain camelCase.
                val focusCandidate = player.focoTreino?.lowercase()?.trim()
                if (!focusCandidate.isNullOrBlank() && focusCandidate in selectableAttributeKeys) {
                    selectedList.add(focusCandidate)
                }

                while (selectedList.size < 3) {
                    val candidate = if (Random.nextDouble() < 0.6) {
                        primaryAttrList[Random.nextInt(primaryAttrList.size)]
                    } else {
                        selectableAttributeKeys[Random.nextInt(selectableAttributeKeys.size)]
                    }
                    selectedList.add(candidate)
                }

                val trainingFactor = ctMult * ageFactor
                for (attrName in selectedList) {
                    val index = attributeIndex(attrName)
                    val currentVal = values[index]
                    if (currentVal < targetPotential) {
                        val focusMult = PlayerEvolutionSystem.calculateFocusFactor(attrName, player.focoTreino)
                        var gain = ((targetPotential - currentVal) / 15.0) *
                            trainingFactor * minutesFactor * ratingFactor * focusMult *
                            Random.nextDouble(0.8, 1.2)
                        gain = gain.coerceIn(0.1, 3.0)
                        val newVal = (currentVal + gain.roundToInt()).coerceAtMost(targetPotential)
                        if (newVal != currentVal) {
                            values[index] = newVal
                            historyLogs.add(
                                HistoricoEvolucao(
                                    jogadorId = player.id,
                                    data = periodDate,
                                    atributo = attrName,
                                    valorAntigo = currentVal,
                                    valorNovo = newVal
                                )
                            )
                        }
                    }
                }
            }

            if (player.age > 30) {
                val declineAmount = if (player.age > 33) Random.nextInt(1, 4) else Random.nextInt(0, 2)
                if (declineAmount > 0) {
                    val targetPhysicalAttr = physicalAttributeKeys[Random.nextInt(physicalAttributeKeys.size)]
                    val index = attributeIndex(targetPhysicalAttr)
                    val currentVal = values[index]
                    val newVal = (currentVal - declineAmount).coerceAtLeast(1)
                    if (newVal != currentVal) {
                        values[index] = newVal
                        historyLogs.add(
                            HistoricoEvolucao(
                                jogadorId = player.id,
                                data = periodDate,
                                atributo = targetPhysicalAttr,
                                valorAntigo = currentVal,
                                valorNovo = newVal
                            )
                        )
                    }
                }
            }

            val newAtributos = values.toAtributos()
            val newCalculatedForce = CalculadoraNota.calcularNota(posEnum, newAtributos)
            val netChange = (newCalculatedForce - player.force).toDouble()
            val hasPersistedDelta = historyLogs.isNotEmpty() || netChange != 0.0

            if (retainUnchangedResults || hasPersistedDelta) {
                val newJson = serializeAttributes(newAtributos)
                val updatedPlayer = player.copy(
                    atributosJson = newJson,
                    force = newCalculatedForce,
                    minutosJogados = 0,
                    evolucaoMensal = netChange
                )

                results.add(
                    PlayerEvolutionResult(
                        player = updatedPlayer,
                        oldAttributes = oldAtributos,
                        newAttributes = newAtributos,
                        netChange = netChange,
                        historyLogs = historyLogs
                    )
                )
            }
        }

        return results
    }

    private fun Atributos.toMutableValues(): IntArray = intArrayOf(
        reflexos,
        pegada,
        umContraUm,
        saidaDeGol,
        lancamento,
        desarme,
        marcacao,
        cabeceio,
        passeCurto,
        cruzamento,
        drible,
        passe,
        primeiroToque,
        finalizacao,
        chuteDeLonge,
        controleBola,
        posicionamento,
        concentracao,
        sangueFrio,
        antecipacao,
        bravura,
        trabalhoEquipe,
        decisao,
        semBola,
        visaoJogo,
        criatividade,
        agressividade,
        lideranca,
        regularidade,
        agilidade,
        impulsao,
        forca,
        velocidade,
        aceleracao,
        resistencia
    ).also { check(it.size == ATTRIBUTE_COUNT) }

    private fun IntArray.toAtributos(): Atributos {
        check(size == ATTRIBUTE_COUNT)
        return Atributos(
            reflexos = this[0],
            pegada = this[1],
            umContraUm = this[2],
            saidaDeGol = this[3],
            lancamento = this[4],
            desarme = this[5],
            marcacao = this[6],
            cabeceio = this[7],
            passeCurto = this[8],
            cruzamento = this[9],
            drible = this[10],
            passe = this[11],
            primeiroToque = this[12],
            finalizacao = this[13],
            chuteDeLonge = this[14],
            controleBola = this[15],
            posicionamento = this[16],
            concentracao = this[17],
            sangueFrio = this[18],
            antecipacao = this[19],
            bravura = this[20],
            trabalhoEquipe = this[21],
            decisao = this[22],
            semBola = this[23],
            visaoJogo = this[24],
            criatividade = this[25],
            agressividade = this[26],
            lideranca = this[27],
            regularidade = this[28],
            agilidade = this[29],
            impulsao = this[30],
            forca = this[31],
            velocidade = this[32],
            aceleracao = this[33],
            resistencia = this[34]
        )
    }

    private fun attributeIndex(name: String): Int = when (name) {
        "reflexos" -> 0
        "pegada" -> 1
        "umContraUm" -> 2
        "saidaDeGol" -> 3
        "lancamento" -> 4
        "desarme" -> 5
        "marcacao" -> 6
        "cabeceio" -> 7
        "passeCurto" -> 8
        "cruzamento" -> 9
        "drible" -> 10
        "passe" -> 11
        "primeiroToque" -> 12
        "finalizacao" -> 13
        "chuteDeLonge" -> 14
        "controleBola" -> 15
        "posicionamento" -> 16
        "concentracao" -> 17
        "sangueFrio" -> 18
        "antecipacao" -> 19
        "bravura" -> 20
        "trabalhoEquipe" -> 21
        "decisao" -> 22
        "semBola" -> 23
        "visaoJogo" -> 24
        "criatividade" -> 25
        "agressividade" -> 26
        "lideranca" -> 27
        "regularidade" -> 28
        "agilidade" -> 29
        "impulsao" -> 30
        "forca" -> 31
        "velocidade" -> 32
        "aceleracao" -> 33
        "resistencia" -> 34
        else -> error("Atributo de evolução desconhecido: $name")
    }

    /** Direct serializer: all keys are constants and all values are integers. */
    internal fun serializeAttributes(a: Atributos): String = buildString(512) {
        append('{')
        append("\"reflexos\":").append(a.reflexos)
        append(",\"pegada\":").append(a.pegada)
        append(",\"umContraUm\":").append(a.umContraUm)
        append(",\"saidaDeGol\":").append(a.saidaDeGol)
        append(",\"lancamento\":").append(a.lancamento)
        append(",\"desarme\":").append(a.desarme)
        append(",\"marcacao\":").append(a.marcacao)
        append(",\"cabeceio\":").append(a.cabeceio)
        append(",\"passeCurto\":").append(a.passeCurto)
        append(",\"cruzamento\":").append(a.cruzamento)
        append(",\"drible\":").append(a.drible)
        append(",\"passe\":").append(a.passe)
        append(",\"primeiroToque\":").append(a.primeiroToque)
        append(",\"finalizacao\":").append(a.finalizacao)
        append(",\"chuteDeLonge\":").append(a.chuteDeLonge)
        append(",\"controleBola\":").append(a.controleBola)
        append(",\"posicionamento\":").append(a.posicionamento)
        append(",\"concentracao\":").append(a.concentracao)
        append(",\"sangueFrio\":").append(a.sangueFrio)
        append(",\"antecipacao\":").append(a.antecipacao)
        append(",\"bravura\":").append(a.bravura)
        append(",\"trabalhoEquipe\":").append(a.trabalhoEquipe)
        append(",\"decisao\":").append(a.decisao)
        append(",\"semBola\":").append(a.semBola)
        append(",\"visaoJogo\":").append(a.visaoJogo)
        append(",\"criatividade\":").append(a.criatividade)
        append(",\"agressividade\":").append(a.agressividade)
        append(",\"lideranca\":").append(a.lideranca)
        append(",\"regularidade\":").append(a.regularidade)
        append(",\"agilidade\":").append(a.agilidade)
        append(",\"impulsao\":").append(a.impulsao)
        append(",\"forca\":").append(a.forca)
        append(",\"velocidade\":").append(a.velocidade)
        append(",\"aceleracao\":").append(a.aceleracao)
        append(",\"resistencia\":").append(a.resistencia)
        append('}')
    }
}
