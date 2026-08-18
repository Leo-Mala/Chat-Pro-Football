package com.example.data

/**
 * Identidade tipada das seis confederações continentais reconhecidas pelo motor mundial.
 *
 * O projeto já possuía uma data class chamada [Confederation] usada como metadado visual. Este
 * enum deliberadamente usa outro nome para não quebrar essa API legada enquanto a camada de regras
 * deixa de depender de strings livres.
 */
enum class FootballConfederation(val code: String) {
    UEFA("UEFA"),
    CONMEBOL("CONMEBOL"),
    CONCACAF("CONCACAF"),
    CAF("CAF"),
    AFC("AFC"),
    OFC("OFC");

    companion object {
        fun fromCode(code: String?): FootballConfederation? {
            val normalized = code?.trim().orEmpty()
            if (normalized.isEmpty()) return null
            return entries.firstOrNull {
                it.code.equals(normalized, ignoreCase = true) ||
                    it.name.equals(normalized, ignoreCase = true)
            }
        }
    }
}
