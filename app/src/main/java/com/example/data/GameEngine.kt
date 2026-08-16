package com.example.data

import kotlin.math.roundToInt
import kotlin.random.Random

object GameEngine {
    // Classic formations and their tactical modifiers
    val formations = listOf(
        "4-4-2",
        "4-4-1-1",
        "4-5-1",
        "4-3-3",
        "4-3-2-1",
        "4-1-3-2",
        "5-4-1",
        "4-1-2-1-2 Diamond",
        "3-5-2",
        "5-3-2",
        "4-2-3-1",
        "3-4-3",
        "3-2-4-1",
        "3-2-5 (W-M)",
        "2-3-2-3",
        "4-2-4"
    )

    fun getFormationModifiers(formation: String): Pair<Double, Double> {
        return when (formation) {
            "4-4-2" -> Pair(1.00, 1.00)
            "4-4-1-1" -> Pair(1.02, 1.05)
            "4-5-1" -> Pair(0.90, 1.20)
            "4-3-3" -> Pair(1.15, 0.85)
            "4-3-2-1" -> Pair(1.10, 0.95)
            "4-1-3-2" -> Pair(1.08, 0.98)
            "5-4-1" -> Pair(0.75, 1.35)
            "4-1-2-1-2 Diamond" -> Pair(1.12, 0.92)
            "3-5-2" -> Pair(1.08, 0.95)
            "5-3-2" -> Pair(0.80, 1.25)
            "4-2-3-1" -> Pair(1.08, 1.02)
            "3-4-3" -> Pair(1.20, 0.82)
            "3-2-4-1" -> Pair(1.15, 0.90)
            "3-2-5 (W-M)" -> Pair(1.22, 0.80)
            "2-3-2-3" -> Pair(1.18, 0.85)
            "4-2-4" -> Pair(1.30, 0.70)
            else -> Pair(1.00, 1.00)
        }
    }

    val playStyles = listOf("Ofensiva", "Equilibrada", "Defensiva", "Ataque Total", "Equilibrado", "Retranca", "Contra-ataque")

    fun getStyleModifiers(style: String): Pair<Double, Double> {
        return when (style.trim().lowercase()) {
            "ofensiva", "ataque total" -> Pair(1.25, 0.75)
            "defensiva", "retranca" -> Pair(0.70, 1.30)
            "contra-ataque" -> Pair(1.10, 1.10)
            else -> Pair(1.00, 1.00)
        }
    }

    fun generateRoundRobin(
        teamIds: List<Long>,
        season: Int,
        startWeek: Int,
        competitionType: String,
        doubleRound: Boolean = false,
        customWeeks: List<Int>? = null
    ): List<Fixture> {
        val fixtures = mutableListOf<Fixture>()
        val list = teamIds.toMutableList()
        if (list.size < 2) return emptyList()

        val hasBye = list.size % 2 != 0
        if (hasBye) {
            list.add(0L)
        }
        val n = list.size
        val numRounds = n - 1

        for (round in 0 until numRounds) {
            val weekNum = customWeeks?.getOrNull(round) ?: (startWeek + round)
            for (i in 0 until n / 2) {
                val home = list[i]
                val away = list[n - 1 - i]
                if (home == 0L || away == 0L) continue
                if (round % 2 == 0) {
                    fixtures.add(Fixture(season = season, week = weekNum, homeTeamId = home, awayTeamId = away, competitionType = competitionType))
                } else {
                    fixtures.add(Fixture(season = season, week = weekNum, homeTeamId = away, awayTeamId = home, competitionType = competitionType))
                }
            }
            val last = list.removeAt(list.size - 1)
            list.add(1, last)
        }

        if (doubleRound) {
            val firstLegFixtures = fixtures.toList()
            val matchesPerRound = n / 2
            val secondLegFixtures = firstLegFixtures.mapIndexed { idx, f ->
                val roundIndex = if (matchesPerRound > 0) idx / matchesPerRound else 0
                val secondLegRoundIndex = roundIndex + numRounds
                val weekNum = customWeeks?.getOrNull(secondLegRoundIndex) ?: (f.week + numRounds)
                Fixture(
                    season = season,
                    week = weekNum,
                    homeTeamId = f.awayTeamId,
                    awayTeamId = f.homeTeamId,
                    competitionType = competitionType
                )
            }
            fixtures.addAll(secondLegFixtures)
        }
        return fixtures
    }

    fun calculateTeamRating(players: List<Player>): Int {
        if (players.isEmpty()) return 30

        val gk = players.filter { it.position == "GOL" }.maxByOrNull { it.force }
        val dfs = players.filter { it.position == "ZAG" || it.position == "LAT" }.sortedByDescending { it.force }.take(4)
        val mfs = players.filter { it.position == "VOL" || it.position == "MEI" }.sortedByDescending { it.force }.take(4)
        val fwds = players.filter { it.position == "ATA" }.sortedByDescending { it.force }.take(2)
        val bestXI = mutableListOf<Player>()
        gk?.let { bestXI.add(it) }
        bestXI.addAll(dfs)
        bestXI.addAll(mfs)
        bestXI.addAll(fwds)

        if (bestXI.size < 11) {
            val remaining = players.filter { it !in bestXI }.sortedByDescending { it.force }.take(11 - bestXI.size)
            bestXI.addAll(remaining)
        }
        if (bestXI.isEmpty()) return 30

        return bestXI.map { p ->
            val energyFactor = 0.5 + 0.5 * (p.energy.coerceIn(0, 100) / 100.0)
            val moralFactor = 0.8 + 0.2 * (p.moral.coerceIn(0, 100) / 100.0)
            p.force * energyFactor * moralFactor
        }.average().toInt().coerceIn(10, 100)
    }

    data class MatchEventDetail(
        val minute: Int,
        val type: String, // GOAL, CARD_YELLOW, CARD_RED, INJURY, INFO, SUBSTITUTION
        val teamId: Long,
        val description: String,
        val isHomeEvent: Boolean,
        val scorerId: Long? = null,
        val playerId: Long? = null,
        val assistId: Long? = null
    )

    fun simulateMatchInstant(
        homeTeam: Team,
        awayTeam: Team,
        homePlayers: List<Player> = emptyList(),
        awayPlayers: List<Player> = emptyList(),
        homeTactics: String = "4-4-2",
        homeStyle: String = "Equilibrado",
        awayTactics: String = "4-4-2",
        awayStyle: String = "Equilibrado",
        isRivalry: Boolean = false,
        randomSeed: Long = Random.nextLong()
    ): Pair<Int, Int> {
        val rand = Random(randomSeed)
        val homeRating = if (homePlayers.isNotEmpty()) calculateTeamRating(homePlayers) else homeTeam.rating
        val awayRating = if (awayPlayers.isNotEmpty()) calculateTeamRating(awayPlayers) else awayTeam.rating
        val (hFormAtt, hFormDef) = getFormationModifiers(homeTactics)
        val (hStyleAtt, hStyleDef) = getStyleModifiers(homeStyle)
        val (aFormAtt, aFormDef) = getFormationModifiers(awayTactics)
        val (aStyleAtt, aStyleDef) = getStyleModifiers(awayStyle)
        val homeTacticalPower = hFormAtt * hStyleAtt
        val awayTacticalPower = aFormAtt * aStyleAtt
        val homeAdvantage = if (isRivalry) 3 else 5
        val adjHomeRating = (homeRating * homeTacticalPower) + homeAdvantage
        val adjAwayRating = awayRating * awayTacticalPower
        val diff = adjHomeRating - adjAwayRating
        val homeLambda = (1.3 + (diff / 22.0)).coerceIn(0.2, 4.5)
        val awayLambda = (1.0 - (diff / 22.0)).coerceIn(0.1, 4.0)

        fun poiss(lambda: Double): Int {
            val L = Math.exp(-lambda)
            var k = 0
            var p = 1.0
            do {
                k++
                p *= rand.nextDouble()
            } while (p > L && k < 10)
            return (k - 1).coerceIn(0, 8)
        }
        return Pair(poiss(homeLambda), poiss(awayLambda))
    }

    data class DetailedMatchOutput(
        val events: List<MatchEventDetail>,
        val homeScore: Int,
        val awayScore: Int,
        val homePossession: Int,
        val awayPossession: Int,
        val homeShots: Int,
        val awayShots: Int,
        val playerRatings: Map<Long, Double>
    )

    fun simulateMatchDetailed(
        homeTeam: Team,
        awayTeam: Team,
        homePlayers: List<Player>,
        awayPlayers: List<Player>,
        homeTactics: String,
        homeStyle: String,
        awayTactics: String,
        awayStyle: String,
        isRivalry: Boolean,
        randomSeed: Long,
        homeReserves: List<Player> = emptyList(),
        awayReserves: List<Player> = emptyList()
    ): List<MatchEventDetail> {
        return simulateMatchDetailedWithStats(
            homeTeam, awayTeam, homePlayers, awayPlayers,
            homeTactics, homeStyle, awayTactics, awayStyle, isRivalry, randomSeed,
            homeReserves, awayReserves
        ).events
    }

    fun simulateMatchDetailedWithStats(
        homeTeam: Team,
        awayTeam: Team,
        homePlayers: List<Player>,
        awayPlayers: List<Player>,
        homeTactics: String,
        homeStyle: String,
        awayTactics: String,
        awayStyle: String,
        isRivalry: Boolean,
        randomSeed: Long,
        homeReserves: List<Player> = emptyList(),
        awayReserves: List<Player> = emptyList()
    ): DetailedMatchOutput {
        val rand = Random(randomSeed)
        val events = mutableListOf<MatchEventDetail>()
        val activeHome = homePlayers.toMutableList()
        val activeAway = awayPlayers.toMutableList()

        if (activeHome.isEmpty()) {
            val virtualRoster = DefaultData.generateRosterForTeam(homeTeam.id, homeTeam.rating, homeTeam.name, homeTeam.country)
            activeHome.addAll(virtualRoster.take(11))
        }
        if (activeAway.isEmpty()) {
            val virtualRoster = DefaultData.generateRosterForTeam(awayTeam.id, awayTeam.rating, awayTeam.name, awayTeam.country)
            activeAway.addAll(virtualRoster.take(11))
        }

        val resHome = homeReserves.filter { it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }.toMutableList()
        val resAway = awayReserves.filter { it.injuryWeeksRemaining == 0 && it.suspensionWeeksRemaining == 0 }.toMutableList()
        var homeSubsCount = 0
        var awaySubsCount = 0
        val maxSubs = 3

        val posActions = mutableMapOf<Long, Double>()
        val negActions = mutableMapOf<Long, Double>()
        (homePlayers + awayPlayers + homeReserves + awayReserves).forEach { p ->
            posActions[p.id] = 0.0
            negActions[p.id] = 0.0
        }

        val (hFormAtt, hFormDef) = getFormationModifiers(homeTactics)
        val (hStyleAtt, hStyleDef) = getStyleModifiers(homeStyle)
        val (aFormAtt, aFormDef) = getFormationModifiers(awayTactics)
        val (aStyleAtt, aStyleDef) = getStyleModifiers(awayStyle)
        val homeAttMod = hFormAtt * hStyleAtt
        val homeDefMod = hFormDef * hStyleDef
        val awayAttMod = aFormAtt * aStyleAtt
        val awayDefMod = aFormDef * aStyleDef

        // 1. CÁLCULO DE POSSE DE BOLA
        val homeMidfielders = activeHome.filter { it.position == "MEI" || it.position == "VOL" }.ifEmpty { activeHome }
        val awayMidfielders = activeAway.filter { it.position == "MEI" || it.position == "VOL" }.ifEmpty { activeAway }
        val homePasseAvg = homeMidfielders.map { it.getAtributosObject().passeCurto }.average().let { if (it.isNaN()) 50.0 else it }
        val homeCtrlAvg = homeMidfielders.map { it.getAtributosObject().controleBola }.average().let { if (it.isNaN()) 50.0 else it }
        val homeVisaoAvg = homeMidfielders.map { it.getAtributosObject().visaoJogo }.average().let { if (it.isNaN()) 50.0 else it }
        val homeMidScore = ((homePasseAvg * 0.4 + homeCtrlAvg * 0.3 + homeVisaoAvg * 0.3) * hStyleDef.coerceAtLeast(0.8)).coerceAtLeast(10.0)

        val awayPasseAvg = awayMidfielders.map { it.getAtributosObject().passeCurto }.average().let { if (it.isNaN()) 50.0 else it }
        val awayCtrlAvg = awayMidfielders.map { it.getAtributosObject().controleBola }.average().let { if (it.isNaN()) 50.0 else it }
        val awayVisaoAvg = awayMidfielders.map { it.getAtributosObject().visaoJogo }.average().let { if (it.isNaN()) 50.0 else it }
        val awayMidScore = ((awayPasseAvg * 0.4 + awayCtrlAvg * 0.3 + awayVisaoAvg * 0.3) * aStyleDef.coerceAtLeast(0.8)).coerceAtLeast(10.0)

        val homePossessionPercent = kotlin.math.round((homeMidScore / (homeMidScore + awayMidScore)) * 100.0).toInt().coerceIn(25, 75)
        val awayPossessionPercent = 100 - homePossessionPercent

        // 2. CÁLCULO DE FINALIZAÇÕES
        val homeAttackers = activeHome.filter { it.position == "ATA" || it.position == "MEI" }.ifEmpty { activeHome }
        val homeDefenders = activeHome.filter { it.position == "ZAG" || it.position == "VOL" }.ifEmpty { activeHome }
        val awayAttackers = activeAway.filter { it.position == "ATA" || it.position == "MEI" }.ifEmpty { activeAway }
        val awayDefenders = activeAway.filter { it.position == "ZAG" || it.position == "VOL" }.ifEmpty { activeAway }

        val homeAttRatio = run {
            val avgCriat = homeAttackers.map { it.getAtributosObject().criatividade }.average().let { if (it.isNaN()) 50.0 else it }
            val avgPasse = homeAttackers.map { it.getAtributosObject().passe }.average().let { if (it.isNaN()) 50.0 else it }
            val avgDrib = homeAttackers.map { it.getAtributosObject().drible }.average().let { if (it.isNaN()) 50.0 else it }
            val avgDefPos = awayDefenders.map { it.getAtributosObject().posicionamento }.average().let { if (it.isNaN()) 50.0 else it }
            val avgDefConc = awayDefenders.map { it.getAtributosObject().concentracao }.average().let { if (it.isNaN()) 50.0 else it }
            val avgDefForca = awayDefenders.map { it.getAtributosObject().forca }.average().let { if (it.isNaN()) 50.0 else it }
            ((avgCriat + avgPasse + avgDrib) * homeAttMod) / ((avgDefPos + avgDefConc + avgDefForca) * awayDefMod).coerceAtLeast(10.0)
        }

        val awayAttRatio = run {
            val avgCriat = awayAttackers.map { it.getAtributosObject().criatividade }.average().let { if (it.isNaN()) 50.0 else it }
            val avgPasse = awayAttackers.map { it.getAtributosObject().passe }.average().let { if (it.isNaN()) 50.0 else it }
            val avgDrib = awayAttackers.map { it.getAtributosObject().drible }.average().let { if (it.isNaN()) 50.0 else it }
            val avgDefPos = homeDefenders.map { it.getAtributosObject().posicionamento }.average().let { if (it.isNaN()) 50.0 else it }
            val avgDefConc = homeDefenders.map { it.getAtributosObject().concentracao }.average().let { if (it.isNaN()) 50.0 else it }
            val avgDefForca = homeDefenders.map { it.getAtributosObject().forca }.average().let { if (it.isNaN()) 50.0 else it }
            ((avgCriat + avgPasse + avgDrib) * awayAttMod) / ((avgDefPos + avgDefConc + avgDefForca) * homeDefMod).coerceAtLeast(10.0)
        }

        val homeShots = (5 + (homeAttRatio * 4.0) + (homePossessionPercent / 12.0) + rand.nextInt(0, 3)).toInt().coerceIn(3, 20)
        val awayShots = (5 + (awayAttRatio * 4.0) + (awayPossessionPercent / 12.0) + rand.nextInt(0, 3)).toInt().coerceIn(3, 20)

        // 3. AVALIAÇÃO DE CHANCE DE GOL (AJUSTADA E REBALANCED)
        val homeGK = activeHome.find { it.position == "GOL" } ?: activeHome.firstOrNull()
        val awayGK = activeAway.find { it.position == "GOL" } ?: activeAway.firstOrNull()

        fun evalGoalAttempt(
            attacker: Player,
            defenderGK: Player?,
            defTeamDefenders: List<Player>,
            attTeamMod: Double,
            defTeamMod: Double
        ): Boolean {
            val a = attacker.getAtributosObject()
            val gk = defenderGK?.getAtributosObject() ?: Atributos()
            val defZagAvg = defTeamDefenders
                .map { (it.getAtributosObject().desarme + it.getAtributosObject().marcacao) / 2.0 }
                .average()
                .let { if (it.isNaN()) 50.0 else it }

            val attScore = (a.finalizacao * 0.40 + a.sangueFrio * 0.30 + a.primeiroToque * 0.30) * attTeamMod
            val defScore = (gk.reflexos * 0.35 + gk.posicionamento * 0.25 + gk.agilidade * 0.20 + defZagAvg * 0.20) * defTeamMod

            val ratio = attScore / defScore.coerceAtLeast(10.0)
            val conversionProbability = (0.15 * ratio).coerceIn(0.05, 0.35)

            val isGoal = rand.nextDouble() < conversionProbability

            if (isGoal) {
                if (defenderGK != null) {
                    negActions[defenderGK.id] = (negActions[defenderGK.id] ?: 0.0) + 0.5
                }
                return true
            } else {
                if (defenderGK != null && rand.nextDouble() < 0.6) {
                    posActions[defenderGK.id] = (posActions[defenderGK.id] ?: 0.0) + 0.8
                }
                return false
            }
        }

        val homeGoalsList = mutableListOf<Pair<Player, Player?>>()
        val awayGoalsList = mutableListOf<Pair<Player, Player?>>()

        for (i in 1..homeShots) {
            val shooter = pickScorer(activeHome, rand) ?: activeHome.randomOrNull(rand) ?: activeHome.firstOrNull() ?: Player(id = homeTeam.id * 10000L + rand.nextLong(100, 999), teamId = homeTeam.id, name = "Atacante ${homeTeam.name}", age = 24, position = "ATA", force = homeTeam.rating)
            if (evalGoalAttempt(shooter, awayGK, awayDefenders, homeAttMod, awayDefMod)) {
                val assist = activeHome.filter { it.id != shooter.id }.randomOrNull(rand)
                homeGoalsList.add(Pair(shooter, assist))
            }
        }

        for (i in 1..awayShots) {
            val shooter = pickScorer(activeAway, rand) ?: activeAway.randomOrNull(rand) ?: activeAway.firstOrNull() ?: Player(id = awayTeam.id * 10000L + rand.nextLong(100, 999), teamId = awayTeam.id, name = "Atacante ${awayTeam.name}", age = 24, position = "ATA", force = awayTeam.rating)
            if (evalGoalAttempt(shooter, homeGK, homeDefenders, awayAttMod, homeDefMod)) {
                val assist = activeAway.filter { it.id != shooter.id }.randomOrNull(rand)
                awayGoalsList.add(Pair(shooter, assist))
            }
        }

        // Placar final sem trava do coerceAtMost(7)
        val homeGoals = homeGoalsList.size
        val awayGoals = awayGoalsList.size

        val usedMinutes = mutableSetOf<Int>()
        fun getUniqueMinute(minRange: IntRange): Int {
            var m = minRange.random(rand)
            var attempts = 0
            while (m in usedMinutes && attempts < 100) {
                m = minRange.random(rand)
                attempts++
            }
            usedMinutes.add(m)
            return m
        }

        // Eventos de Gol
        for (g in 0 until homeGoals) {
            val (scorer, assistPlayer) = homeGoalsList[g]
            val m = getUniqueMinute(5..89)
            posActions[scorer.id] = (posActions[scorer.id] ?: 0.0) + 2.0
            if (assistPlayer != null) posActions[assistPlayer.id] = (posActions[assistPlayer.id] ?: 0.0) + 1.0
            val desc = listOf(
                "GOL do ${homeTeam.name}! ${scorer.name} finaliza no ângulo após belo passe!",
                "GOL do ${homeTeam.name}! Cruzamento preciso de ${assistPlayer?.name ?: "Meia"} e cabeceio certeiro de ${scorer.name}!",
                "GOL do ${homeTeam.name}! ${scorer.name} se livra da zaga e bate firme sem chance!"
            ).random(rand)
            events.add(
                MatchEventDetail(m, "GOAL", homeTeam.id, desc, true, scorer.id, scorer.id, assistId = assistPlayer?.id)
            )
        }

        for (g in 0 until awayGoals) {
            val (scorer, assistPlayer) = awayGoalsList[g]
            val m = getUniqueMinute(5..89)
            posActions[scorer.id] = (posActions[scorer.id] ?: 0.0) + 2.0
            if (assistPlayer != null) posActions[assistPlayer.id] = (posActions[assistPlayer.id] ?: 0.0) + 1.0
            val desc = listOf(
                "GOL do ${awayTeam.name}! ${scorer.name} finaliza cruzado sem chances!",
                "GOL do ${awayTeam.name}! Contra-ataque veloz finalizado por ${scorer.name}!",
                "GOL do ${awayTeam.name}! ${scorer.name} aproveita rebote na pequena área!"
            ).random(rand)
            events.add(
                MatchEventDetail(m, "GOAL", awayTeam.id, desc, false, scorer.id, scorer.id, assistId = assistPlayer?.id)
            )
        }

        // Substituições
        for (sub in 1..3) {
            if (resHome.isNotEmpty() && rand.nextDouble() < 0.7 && homeSubsCount < maxSubs) {
                val m = getUniqueMinute(50..85)
                val outPlayer = activeHome.filter { it.position != "GOL" }.randomOrNull(rand)
                if (outPlayer != null) {
                    val inPlayer = resHome.filter { it.position == outPlayer.position }.randomOrNull(rand) ?: resHome.randomOrNull(rand)
                    if (inPlayer != null) {
                        activeHome.remove(outPlayer)
                        activeHome.add(inPlayer)
                        resHome.remove(inPlayer)
                        homeSubsCount++
                        events.add(MatchEventDetail(m, "SUBSTITUTION", homeTeam.id, "SUBSTITUIÇÃO no ${homeTeam.name}: Sai ${outPlayer.name} e entra ${inPlayer.name}!", true, playerId = inPlayer.id))
                    }
                }
            }
            if (resAway.isNotEmpty() && rand.nextDouble() < 0.7 && awaySubsCount < maxSubs) {
                val m = getUniqueMinute(50..85)
                val outPlayer = activeAway.filter { it.position != "GOL" }.randomOrNull(rand)
                if (outPlayer != null) {
                    val inPlayer = resAway.filter { it.position == outPlayer.position }.randomOrNull(rand) ?: resAway.randomOrNull(rand)
                    if (inPlayer != null) {
                        activeAway.remove(outPlayer)
                        activeAway.add(inPlayer)
                        resAway.remove(inPlayer)
                        awaySubsCount++
                        events.add(MatchEventDetail(m, "SUBSTITUTION", awayTeam.id, "SUBSTITUIÇÃO no ${awayTeam.name}: Sai ${outPlayer.name} e entra ${inPlayer.name}!", false, playerId = inPlayer.id))
                    }
                }
            }
        }

        // Desarmes e Cartões
        val allActive = (activeHome + activeAway)
        for (p in allActive) {
            val a = p.getAtributosObject()
            val isHomeP = p in activeHome
            val pTeam = if (isHomeP) homeTeam else awayTeam

            if (a.agressividade > 75 && rand.nextDouble() > 0.80) {
                val m = getUniqueMinute(10..88)
                negActions[p.id] = (negActions[p.id] ?: 0.0) + 1.0
                events.add(MatchEventDetail(m, "CARD_YELLOW", pTeam.id, "Cartão Amarelo para ${p.name} por entrada forte (${pTeam.name}).", isHomeP, playerId = p.id))
            }
            if (a.agressividade > 90 && rand.nextDouble() > 0.90) {
                val m = getUniqueMinute(20..85)
                negActions[p.id] = (negActions[p.id] ?: 0.0) + 2.5
                events.add(MatchEventDetail(m, "CARD_RED", pTeam.id, "EXPULSÃO! Cartão Vermelho direto para ${p.name} (${pTeam.name}) por falta violenta!", isHomeP, playerId = p.id))
            }
            if (p.position in listOf("ZAG", "VOL", "LAT")) {
                val probDesarme = (a.desarme * 0.4 + a.marcacao * 0.3 + a.agressividade * 0.3) / 60.0
                if (probDesarme > 0.8) {
                    posActions[p.id] = (posActions[p.id] ?: 0.0) + 0.5
                }
            }
        }

        // Notas finais dos jogadores
        val playerRatings = mutableMapOf<Long, Double>()
        (homePlayers + awayPlayers + homeReserves + awayReserves).forEach { p ->
            val pos = posActions[p.id] ?: 0.0
            val neg = negActions[p.id] ?: 0.0
            val calculatedRating = (5.0 + (pos * 0.5) - (neg * 0.3)).coerceIn(0.0, 10.0)
            playerRatings[p.id] = (kotlin.math.round(calculatedRating * 100.0) / 100.0)
        }

        return DetailedMatchOutput(
            events = events.sortedBy { it.minute },
            homeScore = homeGoals,
            awayScore = awayGoals,
            homePossession = homePossessionPercent,
            awayPossession = awayPossessionPercent,
            homeShots = homeShots,
            awayShots = awayShots,
            playerRatings = playerRatings
        )
    }

    private fun pickScorer(players: List<Player>, rand: Random): Player? {
        val activePlayers = players.filter { it.position != "GOL" }
        if (activePlayers.isEmpty()) return null

        val weights = activePlayers.map { p ->
            when (p.position) {
                "ATA" -> p.force * 5.0
                "MEI" -> p.force * 3.0
                "VOL" -> p.force * 1.2
                "LAT" -> p.force * 0.8
                "ZAG" -> p.force * 0.5
                else -> 1.0
            }
        }

        val sum = weights.sum()
        var roll = rand.nextDouble() * sum
        for (i in activePlayers.indices) {
            roll -= weights[i]
            if (roll <= 0) {
                return activePlayers[i]
            }
        }
        return activePlayers.lastOrNull()
    }
}
