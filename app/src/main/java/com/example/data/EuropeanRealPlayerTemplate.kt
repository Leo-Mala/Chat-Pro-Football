package com.example.data

import kotlin.random.Random

/**
 * Registro factual de um jogador da temporada-base europeia.
 *
 * Campos factuais e campos de gameplay são deliberadamente separados. Nenhum rating proprietário
 * é armazenado aqui; `toGameplayPlayer` deriva atributos internos a partir da força do clube,
 * posição, idade e uma seed baseada no ID factual estável.
 */
data class EuropeanRealPlayerTemplate(
    val fullName: String,
    val birthDateIso: String,
    val nationality: String,
    val position: String,
    val shirtNumber: Int? = null,
    val identityDisambiguator: String = ""
) {
    init {
        require(position in VALID_POSITIONS) { "Posição não suportada: '$position'." }
        require(nationality.isNotBlank()) { "Nacionalidade não pode ser vazia." }
        require(shirtNumber == null || shirtNumber in 1..99) { "Camisa fora do intervalo 1..99." }
        // Valida nome/data e congela a regra de identidade sem depender do clube.
        RealPlayerIdentityKey(fullName, birthDateIso, identityDisambiguator)
    }

    val stableId: Long
        get() = StableRealPlayerIdentity.idFor(fullName, birthDateIso, identityDisambiguator)

    fun ageAt2026SeasonStart(): Int {
        val birthDate = parseStrictIsoDate(birthDateIso, "Data factual")
        val birthdayAlreadyOccurred = birthDate.month < 8 || (birthDate.month == 8 && birthDate.day <= 1)
        return (2026 - birthDate.year - if (birthdayAlreadyOccurred) 0 else 1).coerceAtLeast(15)
    }

    fun toGameplayPlayer(teamId: Long, teamRating: Int): Player {
        val age = ageAt2026SeasonStart()
        val random = Random(stableId xor (teamRating.toLong() shl 7))
        val ageAdjustment = when {
            age <= 20 -> -2
            age <= 29 -> 1
            age <= 33 -> 0
            else -> -2
        }
        val force = (teamRating + ageAdjustment + random.nextInt(-5, 6)).coerceIn(45, 96)

        fun around(offset: Int = 0, spread: Int = 6): Int =
            (force + offset + random.nextInt(-spread, spread + 1)).coerceIn(10, 99)

        val finishing = when (position) {
            "ATA" -> around(1, 5)
            "MEI" -> around(-5, 7)
            "GOL" -> random.nextInt(5, 16)
            else -> around(-25, 9)
        }
        val passing = when (position) {
            "MEI", "VOL", "LAT" -> around(-1, 5)
            "ATA" -> around(-7, 6)
            "GOL" -> random.nextInt(20, 46)
            else -> around(-12, 7)
        }
        val pace = when (position) {
            "ATA", "LAT" -> around(0, 6)
            "MEI" -> around(-4, 6)
            "ZAG" -> around(-7, 7)
            "GOL" -> random.nextInt(20, 51)
            else -> around(-5, 6)
        }
        val strength = when (position) {
            "ZAG", "VOL" -> around(1, 5)
            "ATA", "GOL" -> around(-3, 7)
            else -> around(-5, 7)
        }
        val vision = when (position) {
            "MEI" -> around(1, 5)
            "VOL", "LAT" -> around(-6, 6)
            else -> around(-15, 8)
        }
        val defense = when (position) {
            "ZAG", "VOL" -> around(1, 5)
            "LAT" -> around(-4, 6)
            "GOL" -> around(0, 5)
            else -> random.nextInt(10, 41)
        }

        val base = Player(
            id = stableId,
            teamId = teamId,
            name = fullName,
            age = age,
            nationality = nationality,
            position = position,
            force = force,
            energy = 100,
            moral = 82,
            salary = 0L,
            contractDurationWeeks = 104,
            isFromAcademy = false,
            isStarter = false,
            demand_level = when {
                force >= 86 -> "high"
                force >= 72 -> "medium"
                else -> "low"
            },
            finishing = finishing,
            passing = passing,
            pace = pace,
            strength = strength,
            vision = vision,
            defense = defense,
            potential = (force + random.nextInt(0, 8)).coerceIn(force, 99)
        )
        val marketValue = base.calculateMarketValue()
        return base.copy(
            market_value = marketValue,
            min_price = (marketValue * 0.8).toLong().coerceAtLeast(30_000L),
            max_price = (marketValue * 1.3).toLong().coerceAtLeast(50_000L),
            salary = base.calculateSalary(teamRating.toDouble())
        )
    }

    companion object {
        val VALID_POSITIONS: Set<String> = setOf("GOL", "ZAG", "LAT", "VOL", "MEI", "ATA")
    }
}
