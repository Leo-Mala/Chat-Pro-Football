package com.example.data

import java.text.Normalizer
import java.util.Locale

/**
 * Namespace determinístico para jogadores factuais da base inicial.
 *
 * O ID NÃO contém `teamId`: transferir o jogador para outro clube não muda sua identidade.
 * O namespace começa muito acima dos IDs procedurais atuais (`teamId * 1000 + slot`) para que a
 * expansão da base real não colida com rosters legados ou reparos de integridade.
 *
 * Chave factual mínima: nome canônico + data de nascimento ISO. O desambiguador só deve ser usado
 * quando duas pessoas reais ainda colidirem depois desses dois campos.
 */
data class RealPlayerIdentityKey(
    val fullName: String,
    val birthDateIso: String,
    val disambiguator: String = ""
) {
    init {
        require(fullName.isNotBlank()) { "Nome real do jogador não pode ser vazio." }
        require(BIRTH_DATE_REGEX.matches(birthDateIso)) {
            "Data de nascimento deve usar YYYY-MM-DD: '$birthDateIso'."
        }
    }

    internal fun canonicalValue(): String = listOf(
        normalizeIdentityText(fullName),
        birthDateIso,
        normalizeIdentityText(disambiguator)
    ).joinToString("|")

    companion object {
        private val BIRTH_DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")
    }
}

object StableRealPlayerIdentity {
    const val REAL_PLAYER_ID_FLOOR = 100_000_000_000_000L
    private const val REAL_PLAYER_ID_SPAN = 8_000_000_000_000_000L

    fun idFor(key: RealPlayerIdentityKey): Long {
        var hash = 1469598103934665603L
        key.canonicalValue().forEach { ch ->
            hash = (hash xor ch.code.toLong()) * 1099511628211L
        }
        val positive = hash and Long.MAX_VALUE
        return REAL_PLAYER_ID_FLOOR + (positive % REAL_PLAYER_ID_SPAN)
    }

    fun idFor(fullName: String, birthDateIso: String, disambiguator: String = ""): Long =
        idFor(RealPlayerIdentityKey(fullName, birthDateIso, disambiguator))

    fun isRealPlayerId(id: Long): Boolean = id >= REAL_PLAYER_ID_FLOOR
}

/**
 * NFKD cobre marcas combináveis (á, š, ğ etc.), mas alguns caracteres europeus não se decompõem
 * para ASCII. Eles são transliterados explicitamente antes da normalização para que grafias como
 * `Ødegaard`/`Odegaard`, `Bayındır`/`Bayindir` e `Łukasz`/`Lukasz` preservem a mesma identidade.
 */
private fun normalizeIdentityText(value: String): String {
    val transliterated = value.trim()
        .replace("Ø", "O")
        .replace("ø", "o")
        .replace("Ł", "L")
        .replace("ł", "l")
        .replace("Đ", "D")
        .replace("đ", "d")
        .replace("Ð", "D")
        .replace("ð", "d")
        .replace("Þ", "Th")
        .replace("þ", "th")
        .replace("Æ", "Ae")
        .replace("æ", "ae")
        .replace("Œ", "Oe")
        .replace("œ", "oe")
        .replace("ß", "ss")
        .replace("ı", "i")

    val decomposed = Normalizer.normalize(transliterated, Normalizer.Form.NFKD)
    return decomposed
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}
