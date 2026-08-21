package com.example.data

import kotlin.math.roundToInt
import kotlin.random.Random

data class PlayerEvolutionResult(
    val player: Player,
    val oldAttributes: Atributos,
    val newAttributes: Atributos,
    val netChange: Double,
    val historyLogs: List<HistoricoEvolucao>
)

object PlayerEvolutionSystem {

    // Phase 10.1: these tables are immutable domain constants. Keeping them at object scope avoids
    // rebuilding the same lists/sets tens of thousands of times during every monthly world tick.
    private val physicalAttributeKeys = listOf(
        "agilidade", "impulsao", "forca", "velocidade", "aceleracao", "resistencia"
    )
    private val physicalAttributeNames = physicalAttributeKeys.toSet()
    private val technicalAttributeNames = setOf(
        "reflexos", "pegada", "umcontraum", "saidadegol", "lancamento", "desarme",
        "marcacao", "cabeceio", "passecurto", "cruzamento", "drible", "passe",
        "primeirotoque", "finalizacao", "chutedelonge", "controlebola"
    )
    private val mentalAttributeNames = setOf(
        "posicionamento", "concentracao", "sanguefrio", "antecipacao", "bravura",
        "trabalhoequipe", "decisao", "sembola", "visaojogo", "criatividade",
        "agressividade", "lideranca", "regularidade"
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

    fun calculateAgeFactor(age: Int): Double {
        return when {
            age in 17..23 -> 1.5
            age in 24..28 -> 1.0
            age in 29..32 -> 0.5
            else -> -0.3
        }
    }

    fun calculateMinutesFactor(minutosJogados: Int): Double {
        return when {
            minutosJogados >= 240 -> 1.2  // >60% de minutos em ~4 jogos
            minutosJogados >= 120 -> 1.0  // 30% - 60%
            else -> 0.5                   // <30%
        }
    }

    fun calculateRatingFactor(mediaNotas: Double): Double {
        return when {
            mediaNotas >= 7.5 -> 1.3
            mediaNotas >= 6.0 -> 1.0
            mediaNotas > 0.0 -> 0.8
            else -> 1.0 // sem partidas avaliadas ainda
        }
    }

    fun getCTMultiplier(ctLevel: Int): Double {
        val level = ctLevel.coerceIn(1, 5)
        return 0.7 + (level - 1) * 0.15
    }

    fun isPhysicalAttribute(attrName: String): Boolean {
        return attrName.lowercase() in physicalAttributeNames
    }

    fun isTechnicalAttribute(attrName: String): Boolean {
        return attrName.lowercase() in technicalAttributeNames
    }

    fun isMentalAttribute(attrName: String): Boolean {
        return attrName.lowercase() in mentalAttributeNames
    }

    fun calculateFocusFactor(attrName: String, focoTreino: String?): Double {
        if (focoTreino.isNullOrBlank() || focoTreino.equals("NENHUM", ignoreCase = true)) return 1.0
        val focusUpper = focoTreino.uppercase().trim()
        val attrUpper = attrName.uppercase().trim()

        if (focusUpper == attrUpper || focusUpper.replace(" ", "") == attrUpper) return 2.0
        if (focusUpper == "FISICO" && isPhysicalAttribute(attrName)) return 2.0
        if (focusUpper == "TECNICO" && isTechnicalAttribute(attrName)) return 2.0
        if (focusUpper == "MENTAL" && isMentalAttribute(attrName)) return 2.0

        return 0.5
    }

    fun processMonthlyEvolution(
        players: List<Player>,
        teamsMap: Map<Long, Team>,
        periodDate: String = "2026-01"
    ): List<PlayerEvolutionResult> {
        return players.map { player ->
            val team = teamsMap[player.teamId]
            val ctLevel = team?.trainingCenterLevel ?: 1
            val ctMult = getCTMultiplier(ctLevel)

            val oldAtributos = player.getAtributosObject()
            val posEnum = Posicao.fromCode(player.position)
            val ageFactor = calculateAgeFactor(player.age)
            val minutesFactor = calculateMinutesFactor(player.minutosJogados)
            val ratingFactor = calculateRatingFactor(player.mediaNotas)
            val targetPotential = player.potential.coerceIn(50, 99)

            val updatedAttrMap = mutableMapOf(
                // Técnicos
                "reflexos" to oldAtributos.reflexos,
                "pegada" to oldAtributos.pegada,
                "umContraUm" to oldAtributos.umContraUm,
                "saidaDeGol" to oldAtributos.saidaDeGol,
                "lancamento" to oldAtributos.lancamento,
                "desarme" to oldAtributos.desarme,
                "marcacao" to oldAtributos.marcacao,
                "cabeceio" to oldAtributos.cabeceio,
                "passeCurto" to oldAtributos.passeCurto,
                "cruzamento" to oldAtributos.cruzamento,
                "drible" to oldAtributos.drible,
                "passe" to oldAtributos.passe,
                "primeiroToque" to oldAtributos.primeiroToque,
                "finalizacao" to oldAtributos.finalizacao,
                "chuteDeLonge" to oldAtributos.chuteDeLonge,
                "controleBola" to oldAtributos.controleBola,

                // Mentais
                "posicionamento" to oldAtributos.posicionamento,
                "concentracao" to oldAtributos.concentracao,
                "sangueFrio" to oldAtributos.sangueFrio,
                "antecipacao" to oldAtributos.antecipacao,
                "bravura" to oldAtributos.bravura,
                "trabalhoEquipe" to oldAtributos.trabalhoEquipe,
                "decisao" to oldAtributos.decisao,
                "semBola" to oldAtributos.semBola,
                "visaoJogo" to oldAtributos.visaoJogo,
                "criatividade" to oldAtributos.criatividade,
                "agressividade" to oldAtributos.agressividade,
                "lideranca" to oldAtributos.lideranca,
                "regularidade" to oldAtributos.regularidade,

                // Físicos
                "agilidade" to oldAtributos.agilidade,
                "impulsao" to oldAtributos.impulsao,
                "forca" to oldAtributos.forca,
                "velocidade" to oldAtributos.velocidade,
                "aceleracao" to oldAtributos.aceleracao,
                "resistencia" to oldAtributos.resistencia
            )

            val historyLogs = ArrayList<HistoricoEvolucao>(4)

            if (ageFactor > 0) {
                // Selecionar 2 a 3 atributos para evoluir. As tabelas são constantes compartilhadas
                // para evitar listas/keys temporárias por jogador no universo de ~60k atletas.
                val primaryAttrList = primaryAttributesByPosition.getValue(posEnum)
                val selectedList = LinkedHashSet<String>(4)

                val focusCandidate = player.focoTreino?.lowercase()?.trim()
                if (!focusCandidate.isNullOrBlank() && updatedAttrMap.containsKey(focusCandidate)) {
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
                    val currentVal = updatedAttrMap[attrName] ?: 50
                    if (currentVal < targetPotential) {
                        val focusMult = calculateFocusFactor(attrName, player.focoTreino)
                        var ganho = ((targetPotential - currentVal) / 15.0) * trainingFactor * minutesFactor * ratingFactor * focusMult * Random.nextDouble(0.8, 1.2)
                        ganho = ganho.coerceIn(0.1, 3.0)
                        val newVal = (currentVal + ganho.roundToInt()).coerceAtMost(targetPotential)
                        if (newVal != currentVal) {
                            updatedAttrMap[attrName] = newVal
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

            // Declínio físico por idade
            if (player.age > 30) {
                val declineAmount = if (player.age > 33) Random.nextInt(1, 4) else Random.nextInt(0, 2)
                if (declineAmount > 0) {
                    val targetPhysicalAttr = physicalAttributeKeys[Random.nextInt(physicalAttributeKeys.size)]
                    val currentVal = updatedAttrMap[targetPhysicalAttr] ?: 50
                    val newVal = (currentVal - declineAmount).coerceAtLeast(1)
                    if (newVal != currentVal) {
                        updatedAttrMap[targetPhysicalAttr] = newVal
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

            val newAtributos = Atributos(
                reflexos = updatedAttrMap["reflexos"] ?: 50,
                pegada = updatedAttrMap["pegada"] ?: 50,
                umContraUm = updatedAttrMap["umContraUm"] ?: 50,
                saidaDeGol = updatedAttrMap["saidaDeGol"] ?: 50,
                lancamento = updatedAttrMap["lancamento"] ?: 50,
                desarme = updatedAttrMap["desarme"] ?: 50,
                marcacao = updatedAttrMap["marcacao"] ?: 50,
                cabeceio = updatedAttrMap["cabeceio"] ?: 50,
                passeCurto = updatedAttrMap["passeCurto"] ?: 50,
                cruzamento = updatedAttrMap["cruzamento"] ?: 50,
                drible = updatedAttrMap["drible"] ?: 50,
                passe = updatedAttrMap["passe"] ?: 50,
                primeiroToque = updatedAttrMap["primeiroToque"] ?: 50,
                finalizacao = updatedAttrMap["finalizacao"] ?: 50,
                chuteDeLonge = updatedAttrMap["chuteDeLonge"] ?: 50,
                controleBola = updatedAttrMap["controleBola"] ?: 50,

                posicionamento = updatedAttrMap["posicionamento"] ?: 50,
                concentracao = updatedAttrMap["concentracao"] ?: 50,
                sangueFrio = updatedAttrMap["sangueFrio"] ?: 50,
                antecipacao = updatedAttrMap["antecipacao"] ?: 50,
                bravura = updatedAttrMap["bravura"] ?: 50,
                trabalhoEquipe = updatedAttrMap["trabalhoEquipe"] ?: 50,
                decisao = updatedAttrMap["decisao"] ?: 50,
                semBola = updatedAttrMap["semBola"] ?: 50,
                visaoJogo = updatedAttrMap["visaoJogo"] ?: 50,
                criatividade = updatedAttrMap["criatividade"] ?: 50,
                agressividade = updatedAttrMap["agressividade"] ?: 50,
                lideranca = updatedAttrMap["lideranca"] ?: 50,
                regularidade = updatedAttrMap["regularidade"] ?: 50,

                agilidade = updatedAttrMap["agilidade"] ?: 50,
                impulsao = updatedAttrMap["impulsao"] ?: 50,
                forca = updatedAttrMap["forca"] ?: 50,
                velocidade = updatedAttrMap["velocidade"] ?: 50,
                aceleracao = updatedAttrMap["aceleracao"] ?: 50,
                resistencia = updatedAttrMap["resistencia"] ?: 50
            )

            val newJson = AtributosConverter.atributosToJson(newAtributos)
            val newCalculatedForce = CalculadoraNota.calcularNota(posEnum, newAtributos)

            val updatedPlayer = player.copy(
                atributosJson = newJson,
                force = newCalculatedForce,
                minutosJogados = 0, // reseta contador mensal
                evolucaoMensal = (newCalculatedForce - player.force).toDouble()
            )

            PlayerEvolutionResult(
                player = updatedPlayer,
                oldAttributes = oldAtributos,
                newAttributes = newAtributos,
                netChange = (newCalculatedForce - player.force).toDouble(),
                historyLogs = historyLogs
            )
        }
    }

    fun processPostMatchExperience(players: List<Player>, matchRatings: Map<Long, Double>): List<Player> {
        return players.map { player ->
            val rating = matchRatings[player.id] ?: 6.0
            if (rating >= 7.0) {
                val currentAttr = player.getAtributosObject()
                val posEnum = Posicao.fromCode(player.position)
                val isGoleiro = posEnum == Posicao.GOLEIRO

                // Pequeno impulso pós-partida em 1 atributo aleatório relevante
                val targetKey = when {
                    isGoleiro -> listOf("reflexos", "posicionamento", "pegada").random()
                    posEnum == Posicao.ZAGUEIRO -> listOf("desarme", "marcacao", "posicionamento").random()
                    posEnum == Posicao.LATERAL -> listOf("cruzamento", "velocidade", "desarme").random()
                    posEnum == Posicao.VOLANTE -> listOf("desarme", "passe", "posicionamento").random()
                    posEnum == Posicao.MEIA -> listOf("passe", "visaoJogo", "criatividade").random()
                    else -> listOf("finalizacao", "drible", "sangueFrio").random()
                }

                val currentVal = when (targetKey) {
                    "reflexos" -> currentAttr.reflexos
                    "desarme" -> currentAttr.desarme
                    "finalizacao" -> currentAttr.finalizacao
                    "passe" -> currentAttr.passe
                    "cruzamento" -> currentAttr.cruzamento
                    "posicionamento" -> currentAttr.posicionamento
                    else -> currentAttr.controleBola
                }

                if (currentVal < player.potential && Random.nextDouble() < 0.4) {
                    val updatedVal = (currentVal + 1).coerceAtMost(player.potential)
                    val newAttrMap = when (targetKey) {
                        "reflexos" -> currentAttr.copy(reflexos = updatedVal)
                        "desarme" -> currentAttr.copy(desarme = updatedVal)
                        "finalizacao" -> currentAttr.copy(finalizacao = updatedVal)
                        "passe" -> currentAttr.copy(passe = updatedVal)
                        "cruzamento" -> currentAttr.copy(cruzamento = updatedVal)
                        "posicionamento" -> currentAttr.copy(posicionamento = updatedVal)
                        else -> currentAttr.copy(controleBola = updatedVal)
                    }
                    val newForce = CalculadoraNota.calcularNota(posEnum, newAttrMap)
                    player.copy(
                        atributosJson = AtributosConverter.atributosToJson(newAttrMap),
                        force = newForce
                    )
                } else {
                    player
                }
            } else {
                player
            }
        }
    }
}
