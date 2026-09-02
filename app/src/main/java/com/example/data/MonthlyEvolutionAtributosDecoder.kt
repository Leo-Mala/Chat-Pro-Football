package com.example.data

/**
 * Fast-path decoder for the exact compact JSON emitted by [AtributosConverter.fromAtributos].
 *
 * Monthly evolution reads the complete world-player table, so constructing a JSONObject for every
 * canonical row is disproportionately expensive on slower Android devices. This parser accepts only
 * the canonical key order and compact integer representation produced by our own converter. Any
 * legacy, sparse, reordered or otherwise non-canonical payload falls back to the existing converter,
 * preserving save compatibility and its current defaulting behavior.
 */
internal object MonthlyEvolutionAtributosDecoder {
    fun decode(storage: String?): Atributos? {
        if (storage == null) return null
        return parseCanonical(storage) ?: AtributosConverter.jsonToAtributos(storage)
    }

    private fun parseCanonical(json: String): Atributos? {
        if (json == "{}") return Atributos()
        if (json.isEmpty() || json[0] != '{') return null

        var index = 1

        fun readInt(key: String, terminal: Boolean = false): Int? {
            if (index >= json.length || json[index] != '"') return null
            index++
            if (index + key.length > json.length ||
                !json.regionMatches(index, key, 0, key.length, ignoreCase = false)
            ) {
                return null
            }
            index += key.length
            if (index >= json.length || json[index] != '"') return null
            index++
            if (index >= json.length || json[index] != ':') return null
            index++

            var negative = false
            if (index < json.length && json[index] == '-') {
                negative = true
                index++
            }

            val digitStart = index
            var value = 0L
            val limit = if (negative) 2_147_483_648L else 2_147_483_647L
            while (index < json.length) {
                val char = json[index]
                if (char !in '0'..'9') break
                value = value * 10L + (char - '0')
                if (value > limit) return null
                index++
            }
            if (index == digitStart) return null

            val separator = if (terminal) '}' else ','
            if (index >= json.length || json[index] != separator) return null
            index++

            val signed = if (negative) -value else value
            return signed.toInt()
        }

        val reflexos = readInt("reflexos") ?: return null
        val pegada = readInt("pegada") ?: return null
        val umContraUm = readInt("umContraUm") ?: return null
        val saidaDeGol = readInt("saidaDeGol") ?: return null
        val lancamento = readInt("lancamento") ?: return null
        val desarme = readInt("desarme") ?: return null
        val marcacao = readInt("marcacao") ?: return null
        val cabeceio = readInt("cabeceio") ?: return null
        val passeCurto = readInt("passeCurto") ?: return null
        val cruzamento = readInt("cruzamento") ?: return null
        val drible = readInt("drible") ?: return null
        val passe = readInt("passe") ?: return null
        val primeiroToque = readInt("primeiroToque") ?: return null
        val finalizacao = readInt("finalizacao") ?: return null
        val chuteDeLonge = readInt("chuteDeLonge") ?: return null
        val controleBola = readInt("controleBola") ?: return null
        val posicionamento = readInt("posicionamento") ?: return null
        val concentracao = readInt("concentracao") ?: return null
        val sangueFrio = readInt("sangueFrio") ?: return null
        val antecipacao = readInt("antecipacao") ?: return null
        val bravura = readInt("bravura") ?: return null
        val trabalhoEquipe = readInt("trabalhoEquipe") ?: return null
        val decisao = readInt("decisao") ?: return null
        val semBola = readInt("semBola") ?: return null
        val visaoJogo = readInt("visaoJogo") ?: return null
        val criatividade = readInt("criatividade") ?: return null
        val agressividade = readInt("agressividade") ?: return null
        val lideranca = readInt("lideranca") ?: return null
        val regularidade = readInt("regularidade") ?: return null
        val agilidade = readInt("agilidade") ?: return null
        val impulsao = readInt("impulsao") ?: return null
        val forca = readInt("forca") ?: return null
        val velocidade = readInt("velocidade") ?: return null
        val aceleracao = readInt("aceleracao") ?: return null
        val resistencia = readInt("resistencia", terminal = true) ?: return null

        if (index != json.length) return null

        return Atributos(
            reflexos = reflexos,
            pegada = pegada,
            umContraUm = umContraUm,
            saidaDeGol = saidaDeGol,
            lancamento = lancamento,
            desarme = desarme,
            marcacao = marcacao,
            cabeceio = cabeceio,
            passeCurto = passeCurto,
            cruzamento = cruzamento,
            drible = drible,
            passe = passe,
            primeiroToque = primeiroToque,
            finalizacao = finalizacao,
            chuteDeLonge = chuteDeLonge,
            controleBola = controleBola,
            posicionamento = posicionamento,
            concentracao = concentracao,
            sangueFrio = sangueFrio,
            antecipacao = antecipacao,
            bravura = bravura,
            trabalhoEquipe = trabalhoEquipe,
            decisao = decisao,
            semBola = semBola,
            visaoJogo = visaoJogo,
            criatividade = criatividade,
            agressividade = agressividade,
            lideranca = lideranca,
            regularidade = regularidade,
            agilidade = agilidade,
            impulsao = impulsao,
            forca = forca,
            velocidade = velocidade,
            aceleracao = aceleracao,
            resistencia = resistencia
        )
    }
}
