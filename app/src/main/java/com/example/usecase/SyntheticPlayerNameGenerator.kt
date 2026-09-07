package com.example.usecase

import com.example.data.DefaultData

/**
 * Gera identidades sintéticas estáveis usando exclusivamente os pools de nomes já existentes no app.
 *
 * O gerador é intencionalmente independente do RNG esportivo: criar/normalizar um nome não pode
 * alterar a sequência usada para força, potencial, resultados ou qualquer outra regra de gameplay.
 */
internal object SyntheticPlayerNameGenerator {
    fun forStableIdentity(country: String, stableKey: Long): String {
        val countryInfo = DefaultData.getCountryInfo(country)
        require(countryInfo.firstNames.isNotEmpty()) {
            "Pool de primeiros nomes vazio para $country"
        }
        require(countryInfo.lastNames.isNotEmpty()) {
            "Pool de sobrenomes vazio para $country"
        }

        val firstIndex = Math.floorMod(stableKey, countryInfo.firstNames.size.toLong()).toInt()
        val lastKey = stableKey xor NAME_SALT
        val lastIndex = Math.floorMod(lastKey, countryInfo.lastNames.size.toLong()).toInt()
        return "${countryInfo.firstNames[firstIndex]} ${countryInfo.lastNames[lastIndex]}"
    }

    private const val NAME_SALT = 0x5DEECE66DL
}
