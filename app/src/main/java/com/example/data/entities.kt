package com.example.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "game_save")
data class GameSave(
    @PrimaryKey val id: Int = 1,
    val coachName: String = "Técnico",
    val coachReputation: Int = 30, // 0 to 100
    val currentWeek: Int = 1,
    val currentSeason: Int = 2026,
    val playerTeamId: Long = 0L,
    val bankBalance: Long = 5000000L, // Initial money R$ 5,000,000
    val stadiumCapacity: Int = 10000,
    val ticketPrice: Double = 25.0,
    val sponsorWeekly: Long = 100000L,
    val loanAmount: Long = 0L,
    val isGameOver: Boolean = false,
    val sponsorName: String = "Nenhum",
    val sponsorWeeksRemaining: Int = 0,
    val academyLevel: Int = 1,
    val academyWeeklyInvestment: Long = 10000L,
    val academyProspects: String = "",
    val playerFormation: String = "4-4-2",
    val playerStyle: String = "Equilibrado",
    val captainPlayerId: Long? = null,
    val penaltyPlayerId: Long? = null,
    val freekickPlayerId: Long? = null,
    val cornerPlayerId: Long? = null,
    val socioTorcedoresCount: Int = 2000,
    val hasHiredCoach: Boolean = false,
    val hasHiredPhysio: Boolean = false,
    val globalScoutRevealWeeksRemaining: Int = 0,
    val activeNewsTitle: String? = null,
    val activeNewsDesc: String? = null,
    val installmentWeeklyDeduction: Long = 0L,
    val installmentWeeksRemaining: Int = 0,
    val careerMatches: Int = 0,
    val careerWins: Int = 0,
    val careerDraws: Int = 0,
    val careerLosses: Int = 0,
    val careerGoalsScored: Int = 0,
    val careerGoalsConceded: Int = 0
)

@Entity(
    tableName = "teams",
    indices = [
        Index(value = ["division"]),
        Index(value = ["country"])
    ]
)
data class Team(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val city: String,
    val state: String,
    val country: String = "Brasil",
    val division: Int, // 1: Serie A, 2: Serie B, 3: Serie C, 4: Serie D
    val isPlayerControlled: Boolean = false,
    val rating: Int = 50,
    val stadiumName: String = "Estádio Municipal",
    val logoUrl: String? = null,
    val rivalTeamId: Long = 0L,
    val colorHex: String? = null,
    val trainingCenterLevel: Int = 1
)

@Entity(
    tableName = "players",
    foreignKeys = [
        ForeignKey(
            entity = Team::class,
            parentColumns = ["id"],
            childColumns = ["teamId"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["teamId", "position", "force"]),
        Index(value = ["teamId", "isStarter"]),
        Index(value = ["originalTeamId"])
    ]
)
data class Player(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** null é o estado canônico de Free Agent a partir do schema V21. */
    val teamId: Long?,
    val name: String,
    val age: Int,
    val nationality: String = "Brasil",
    val position: String, // GOL, ZAG, LAT, VOL, MEI, ATA
    val force: Int, // 1 to 100
    val energy: Int = 100, // 0 to 100
    val moral: Int = 75, // 0 to 100
    val salary: Long = 10000L,
    val contractDurationWeeks: Int = 52,
    val isFromAcademy: Boolean = false,
    val careerApps: Int = 0,
    val careerGoals: Int = 0,
    val imageUrl: String? = null,
    val injuryWeeksRemaining: Int = 0,
    val suspensionWeeksRemaining: Int = 0,
    val yellowCardsAccumulated: Int = 0,
    val isStarter: Boolean = false,
    val isOnLoan: Boolean = false,
    val loanWeeksRemaining: Int = 0,
    /** Estado legado/derivável de empréstimo; null significa ausência de clube proprietário legado. */
    val originalTeamId: Long? = null,
    
    // Performance and evolution stats
    val careerAssists: Int = 0,
    val careerTackles: Int = 0,
    val careerSaves: Int = 0,
    val ratingSum: Double = 0.0,
    val ratingCount: Int = 0,
    val maxHistoricalForce: Int = 0,

    // Market features
    val market_value: Long = 0L,
    val min_price: Long = 0L,
    val max_price: Long = 0L,
    val demand_level: String = "medium", // "high", "medium", "low"

    // Cyber Attributes
    val finishing: Int = 50,
    val passing: Int = 50,
    val pace: Int = 50,
    val strength: Int = 50,
    val vision: Int = 50,
    val defense: Int = 50,
    val scoutedLevel: Int = 0, // 0: hidden, -1: scouting pending, 1, 3, 5: scouted levels
    val atributosJson: String? = null,

    // Room Attributes via TypeConverter
    val atributos: Atributos = Atributos(),

    // Evolution and Season Stats
    val potential: Int = 80,
    val gols: Int = 0,
    val assistencias: Int = 0,
    val partidasDisputadas: Int = 0,
    val minutosJogados: Int = 0,
    val mediaNotas: Double = 0.0,
    val focoTreino: String? = null,
    val condicao: Int = 100,
    val evolucaoMensal: Double = 0.0
) {
    fun getAtributosObject(): Atributos {
        if (atributos != Atributos()) {
            return atributos
        }
        if (!atributosJson.isNullOrBlank()) {
            val parsed = AtributosConverter.jsonToAtributos(atributosJson)
            if (parsed != null) return parsed
        }
        val posEnum = Posicao.fromCode(position)
        val isGk = posEnum == Posicao.GOLEIRO
        val defaultGkAttr = if (isGk) force else 30
        return Atributos(
            finalizacao = finishing,
            sangueFrio = finishing,
            cabeceio = finishing,
            chuteDeLonge = finishing,
            passe = passing,
            passeCurto = passing,
            lancamento = passing,
            cruzamento = passing,
            visaoJogo = vision,
            criatividade = vision,
            controleBola = passing,
            primeiroToque = passing,
            drible = pace,
            velocidade = pace,
            aceleracao = pace,
            agilidade = pace,
            forca = strength,
            resistencia = strength,
            impulsao = strength,
            desarme = defense,
            posicionamento = defense,
            marcacao = defense,
            concentracao = defense,
            agressividade = defense,
            reflexos = defaultGkAttr,
            pegada = defaultGkAttr,
            umContraUm = defaultGkAttr,
            saidaDeGol = defaultGkAttr
        )
    }

    fun getCalculatedNota(): Int {
        val posEnum = Posicao.fromCode(position)
        return CalculadoraNota.calcularNota(posEnum, getAtributosObject())
    }
    fun getObservedForce(isGlobalReveal: Boolean, isUserTeam: Boolean = false): String {
        if (isUserTeam || isGlobalReveal) return force.toString()
        return when {
            scoutedLevel == 5 -> force.toString()
            scoutedLevel == 3 -> {
                val delta = (id % 5).toInt() - 2
                "~${(force + delta).coerceIn(1, 99)}"
            }
            scoutedLevel == 1 -> {
                val delta = (id % 11).toInt() - 5
                "~${(force + delta).coerceIn(1, 99)}"
            }
            scoutedLevel < 0 -> "Obs..."
            else -> "???"
        }
    }

    fun calculateMarketValue(): Long {
        val baseValue = when {
            force < 40 -> force * 12000L
            force < 60 -> 40 * 12000L + (force - 40) * 60000L
            force < 75 -> 40 * 12000L + 20 * 60000L + (force - 60) * 450000L
            force < 85 -> 40 * 12000L + 20 * 60000L + 15 * 450000L + (force - 75) * 1800000L
            else -> 40 * 12000L + 20 * 60000L + 15 * 450000L + 10 * 1800000L + (force - 85) * 6000000L
        }
        val ageFactor = when {
            age < 23 -> 0.8 + (age - 15) * 0.025
            age in 23..29 -> 1.0
            age in 30..33 -> 1.0 - (age - 29) * 0.07
            else -> (0.72 - (age - 33) * 0.07).coerceAtLeast(0.12)
        }
        return (baseValue * ageFactor).toLong().coerceAtLeast(40000L)
    }

    fun getMinPrice(): Long {
        if (min_price > 0L) return min_price
        val mv = calculateMarketValue()
        return (mv * 0.8).toLong().coerceAtLeast(30000L)
    }

    fun getMaxPrice(): Long {
        if (max_price > 0L) return max_price
        val mv = calculateMarketValue()
        return (mv * 1.3).toLong().coerceAtLeast(50000L)
    }

    fun calculateSalary(clubReputation: Double = 50.0): Long {
        val finalSalary = ((clubReputation / 100.0) * force * 1500.0).toLong()
        return finalSalary.coerceAtLeast(3000L)
    }

    fun getAverageRating(): Double {
        return if (ratingCount > 0) ratingSum / ratingCount else 6.0
    }
}

@Entity(
    tableName = "fixtures",
    foreignKeys = [
        ForeignKey(
            entity = Team::class,
            parentColumns = ["id"],
            childColumns = ["homeTeamId"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.NO_ACTION
        ),
        ForeignKey(
            entity = Team::class,
            parentColumns = ["id"],
            childColumns = ["awayTeamId"],
            onUpdate = ForeignKey.NO_ACTION,
            onDelete = ForeignKey.NO_ACTION
        )
    ],
    indices = [
        Index(value = ["season"]),
        Index(value = ["week"]),
        Index(value = ["homeTeamId"]),
        Index(value = ["awayTeamId"]),
        Index(value = ["competitionType"]),
        Index(value = ["season", "week"]),
        Index(value = ["season", "week", "matchSlot"])
    ]
)
data class Fixture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val season: Int,
    val week: Int,
    val homeTeamId: Long,
    val awayTeamId: Long,
    val homeScore: Int? = null,
    val awayScore: Int? = null,
    val homePenalties: Int? = null,
    val awayPenalties: Int? = null,
    val competitionType: String,
    val isPlayed: Boolean = false,
    val matchEventsJson: String? = null,
    @ColumnInfo(defaultValue = "'WEEKEND'")
    val matchSlot: MatchSlot = MatchSlot.WEEKEND
)

@Entity(tableName = "club_legends")
data class ClubLegend(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val teamId: Long,
    val playerName: String,
    val position: String,
    val apps: Int = 0,
    val goals: Int = 0
)

@Entity(tableName = "historical_records")
data class HistoricalRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val season: Int,
    val competitionName: String,
    val championTeamName: String,
    val runnerUpTeamName: String,
    val topScorerName: String,
    val topScorerGoals: Int,
    val topScorerTeam: String
)

@Entity(tableName = "coach_offers")
data class CoachOffer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val teamId: Long,
    val teamName: String,
    val rating: Int,
    val weeklySalary: Long,
    val description: String
)

@Entity(tableName = "transaction_history")
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val week: Int,
    val season: Int,
    val type: String,
    val description: String,
    val amount: Long,
    val isIncome: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class DatabaseValidationResult(
    val teamsWithoutCompleteRoster: List<String> = emptyList(),
    val playersWithoutForce: List<String> = emptyList(),
    val playersWithoutTeam: List<String> = emptyList(),
    val teamsWithoutStadium: List<String> = emptyList(),
    val teamsWithoutCityOrCountry: List<String> = emptyList(),
    val teamsWithInvalidShields: List<String> = emptyList(),
    val checkedAt: Long = System.currentTimeMillis()
) {
    fun isValid() = teamsWithoutCompleteRoster.isEmpty() &&
            playersWithoutForce.isEmpty() &&
            playersWithoutTeam.isEmpty() &&
            teamsWithoutStadium.isEmpty() &&
            teamsWithoutCityOrCountry.isEmpty() &&
            teamsWithInvalidShields.isEmpty()
}

@Entity(tableName = "transfer_orders")
data class TransferOrder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val playerId: Long,
    val buyerTeamId: Long = 0L,
    val sellerTeamId: Long = 0L,
    val playerName: String,
    val playerPosition: String,
    val playerForce: Int,
    val offeredPrice: Long,
    val demandLevel: String,
    val status: String,
    val week: Int,
    val season: Int,
    val timestamp: Long = System.currentTimeMillis()
)

enum class CompetitionType(val code: String, val displayName: String, val level: Int) {
    SERIE_A("SERIE_A", "Série A", 1),
    SERIE_B("SERIE_B", "Série B", 2),
    SERIE_C("SERIE_C", "Série C", 3),
    SERIE_D("SERIE_D", "Série D", 4),
    CUP("CUP", "Copa do Brasil", 0),
    STATE("STATE", "Campeonato Estadual", 0),
    CONTINENTAL_T1("CONTINENTAL_T1", "Copa Libertadores", 0),
    CONTINENTAL_T2("CONTINENTAL_T2", "Copa Sul-Americana", 0),
    CONTINENTAL_T3("CONTINENTAL_T3", "Continental Tier 3", 0),
    WORLD_CUP("WORLD_CUP", "Super Mundial de Clubes", 0);

    companion object {
        /** Resolução segura para novos consumidores. Código desconhecido permanece desconhecido. */
        fun fromCodeOrNull(code: String): CompetitionType? {
            return entries.find {
                it.code.equals(code, ignoreCase = true) || it.name.equals(code, ignoreCase = true)
            } ?: when (code.trim().uppercase()) {
                "DIV_1" -> SERIE_A
                "DIV_2" -> SERIE_B
                "DIV_3" -> SERIE_C
                "DIV_4" -> SERIE_D
                "COPA" -> CUP
                "ESTADUAL" -> STATE
                "LIBERTADORES" -> CONTINENTAL_T1
                "SULAMERICANA" -> CONTINENTAL_T2
                "WORLD" -> WORLD_CUP
                else -> null
            }
        }

        /**
         * Adaptador compatível com a assinatura histórica, porém agora falha explicitamente em vez
         * de converter qualquer código desconhecido em SERIE_A.
         */
        fun fromCode(code: String): CompetitionType =
            requireNotNull(fromCodeOrNull(code)) {
                "Código de competição desconhecido: '$code'."
            }
    }
}

@Entity(
    tableName = "transfer_installments",
    indices = [
        Index(value = ["buyerTeamId"]),
        Index(value = ["sellerTeamId"]),
        Index(value = ["status"])
    ]
)
data class TransferInstallment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val transferId: Long = 0L,
    val playerId: Long,
    val buyerTeamId: Long,
    val sellerTeamId: Long,
    val totalAmount: Long,
    val downPayment: Long,
    val installmentAmount: Long,
    val totalInstallments: Int,
    val remainingInstallments: Int,
    val nextDueWeek: Int,
    val season: Int,
    val status: String = "ACTIVE"
)

@Entity(
    tableName = "player_loans",
    indices = [
        Index(value = ["playerId"]),
        Index(value = ["ownerTeamId"]),
        Index(value = ["borrowerTeamId"]),
        Index(value = ["status"])
    ]
)
data class PlayerLoan(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playerId: Long,
    val ownerTeamId: Long,
    val borrowerTeamId: Long,
    val startSeason: Int,
    val startWeek: Int,
    val durationWeeks: Int,
    val remainingWeeks: Int,
    val weeklyFee: Long = 0L,
    val buyoutOptionPrice: Long? = null,
    val status: String = "ACTIVE"
)
