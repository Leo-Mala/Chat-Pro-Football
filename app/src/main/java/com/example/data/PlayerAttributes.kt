package com.example.data

import androidx.room.TypeConverter
import org.json.JSONObject
import kotlin.math.roundToInt
import kotlin.random.Random

enum class Posicao(val code: String, val displayName: String) {
    GOLEIRO("GOL", "Goleiro"),
    ZAGUEIRO("ZAG", "Zagueiro"),
    LATERAL("LAT", "Lateral"),
    VOLANTE("VOL", "Volante"),
    MEIA("MEI", "Meia"),
    ATACANTE("ATA", "Atacante");

    companion object {
        fun fromCode(code: String): Posicao {
            return when (code.uppercase().trim()) {
                "GOL", "GK", "GOLEIRO" -> GOLEIRO
                "ZAG", "CB", "ZAGUEIRO" -> ZAGUEIRO
                "LAT", "LB", "RB", "LATERAL" -> LATERAL
                "VOL", "CDM", "VOLANTE" -> VOLANTE
                "MEI", "CAM", "CM", "MEIA" -> MEIA
                "ATA", "ST", "CF", "ATACANTE" -> ATACANTE
                else -> MEIA
            }
        }
    }
}

data class Atributos(
    // Técnicos (16)
    val reflexos: Int = 50,
    val pegada: Int = 50,
    val umContraUm: Int = 50,
    val saidaDeGol: Int = 50,
    val lancamento: Int = 50,
    val desarme: Int = 50,
    val marcacao: Int = 50,
    val cabeceio: Int = 50,
    val passeCurto: Int = 50,
    val cruzamento: Int = 50,
    val drible: Int = 50,
    val passe: Int = 50,
    val primeiroToque: Int = 50,
    val finalizacao: Int = 50,
    val chuteDeLonge: Int = 50,
    val controleBola: Int = 50,

    // Mentais (13)
    val posicionamento: Int = 50,
    val concentracao: Int = 50,
    val sangueFrio: Int = 50,
    val antecipacao: Int = 50,
    val bravura: Int = 50,
    val trabalhoEquipe: Int = 50,
    val decisao: Int = 50,
    val semBola: Int = 50,
    val visaoJogo: Int = 50,
    val criatividade: Int = 50,
    val agressividade: Int = 50,
    val lideranca: Int = 50,
    val regularidade: Int = 50,

    // Físicos (6)
    val agilidade: Int = 50,
    val impulsao: Int = 50,
    val forca: Int = 50,
    val velocidade: Int = 50,
    val aceleracao: Int = 50,
    val resistencia: Int = 50
)

class AtributosConverter {
    @TypeConverter
    fun fromAtributos(atributos: Atributos?): String? {
        if (atributos == null) return null
        return try {
            JSONObject().apply {
                // Técnicos
                put("reflexos", atributos.reflexos)
                put("pegada", atributos.pegada)
                put("umContraUm", atributos.umContraUm)
                put("saidaDeGol", atributos.saidaDeGol)
                put("lancamento", atributos.lancamento)
                put("desarme", atributos.desarme)
                put("marcacao", atributos.marcacao)
                put("cabeceio", atributos.cabeceio)
                put("passeCurto", atributos.passeCurto)
                put("cruzamento", atributos.cruzamento)
                put("drible", atributos.drible)
                put("passe", atributos.passe)
                put("primeiroToque", atributos.primeiroToque)
                put("finalizacao", atributos.finalizacao)
                put("chuteDeLonge", atributos.chuteDeLonge)
                put("controleBola", atributos.controleBola)

                // Mentais
                put("posicionamento", atributos.posicionamento)
                put("concentracao", atributos.concentracao)
                put("sangueFrio", atributos.sangueFrio)
                put("antecipacao", atributos.antecipacao)
                put("bravura", atributos.bravura)
                put("trabalhoEquipe", atributos.trabalhoEquipe)
                put("decisao", atributos.decisao)
                put("semBola", atributos.semBola)
                put("visaoJogo", atributos.visaoJogo)
                put("criatividade", atributos.criatividade)
                put("agressividade", atributos.agressividade)
                put("lideranca", atributos.lideranca)
                put("regularidade", atributos.regularidade)

                // Físicos
                put("agilidade", atributos.agilidade)
                put("impulsao", atributos.impulsao)
                put("forca", atributos.forca)
                put("velocidade", atributos.velocidade)
                put("aceleracao", atributos.aceleracao)
                put("resistencia", atributos.resistencia)
            }.toString()
        } catch (_: Throwable) {
            """{"reflexos":${atributos.reflexos},"pegada":${atributos.pegada},"umContraUm":${atributos.umContraUm},"saidaDeGol":${atributos.saidaDeGol},"lancamento":${atributos.lancamento},"desarme":${atributos.desarme},"marcacao":${atributos.marcacao},"cabeceio":${atributos.cabeceio},"passeCurto":${atributos.passeCurto},"cruzamento":${atributos.cruzamento},"drible":${atributos.drible},"passe":${atributos.passe},"primeiroToque":${atributos.primeiroToque},"finalizacao":${atributos.finalizacao},"chuteDeLonge":${atributos.chuteDeLonge},"controleBola":${atributos.controleBola},"posicionamento":${atributos.posicionamento},"concentracao":${atributos.concentracao},"sangueFrio":${atributos.sangueFrio},"antecipacao":${atributos.antecipacao},"bravura":${atributos.bravura},"trabalhoEquipe":${atributos.trabalhoEquipe},"decisao":${atributos.decisao},"semBola":${atributos.semBola},"visaoJogo":${atributos.visaoJogo},"criatividade":${atributos.criatividade},"agressividade":${atributos.agressividade},"lideranca":${atributos.lideranca},"regularidade":${atributos.regularidade},"agilidade":${atributos.agilidade},"impulsao":${atributos.impulsao},"forca":${atributos.forca},"velocidade":${atributos.velocidade},"aceleracao":${atributos.aceleracao},"resistencia":${atributos.resistencia}}"""
        }
    }

    @TypeConverter
    fun toAtributos(jsonStr: String?): Atributos? {
        if (jsonStr.isNullOrBlank()) return null
        return try {
            val json = JSONObject(jsonStr)
            Atributos(
                // Técnicos
                reflexos = json.optInt("reflexos", 50),
                pegada = json.optInt("pegada", 50),
                umContraUm = json.optInt("umContraUm", 50),
                saidaDeGol = json.optInt("saidaDeGol", 50),
                lancamento = json.optInt("lancamento", 50),
                desarme = json.optInt("desarme", 50),
                marcacao = json.optInt("marcacao", 50),
                cabeceio = json.optInt("cabeceio", 50),
                passeCurto = json.optInt("passeCurto", 50),
                cruzamento = json.optInt("cruzamento", 50),
                drible = json.optInt("drible", 50),
                passe = json.optInt("passe", 50),
                primeiroToque = json.optInt("primeiroToque", 50),
                finalizacao = json.optInt("finalizacao", 50),
                chuteDeLonge = json.optInt("chuteDeLonge", 50),
                controleBola = json.optInt("controleBola", 50),

                // Mentais
                posicionamento = json.optInt("posicionamento", 50),
                concentracao = json.optInt("concentracao", 50),
                sangueFrio = json.optInt("sangueFrio", 50),
                antecipacao = json.optInt("antecipacao", 50),
                bravura = json.optInt("bravura", 50),
                trabalhoEquipe = json.optInt("trabalhoEquipe", 50),
                decisao = json.optInt("decisao", 50),
                semBola = json.optInt("semBola", 50),
                visaoJogo = json.optInt("visaoJogo", 50),
                criatividade = json.optInt("criatividade", 50),
                agressividade = json.optInt("agressividade", 50),
                lideranca = json.optInt("lideranca", 50),
                regularidade = json.optInt("regularidade", 50),

                // Físicos
                agilidade = json.optInt("agilidade", 50),
                impulsao = json.optInt("impulsao", 50),
                forca = json.optInt("forca", 50),
                velocidade = json.optInt("velocidade", 50),
                aceleracao = json.optInt("aceleracao", 50),
                resistencia = json.optInt("resistencia", 50)
            )
        } catch (_: Throwable) {
            try {
                fun parseVal(key: String): Int {
                    val match = Regex(""""$key"\s*:\s*(\d+)""").find(jsonStr)
                    return match?.groupValues?.get(1)?.toIntOrNull() ?: 50
                }
                Atributos(
                    reflexos = parseVal("reflexos"),
                    pegada = parseVal("pegada"),
                    umContraUm = parseVal("umContraUm"),
                    saidaDeGol = parseVal("saidaDeGol"),
                    lancamento = parseVal("lancamento"),
                    desarme = parseVal("desarme"),
                    marcacao = parseVal("marcacao"),
                    cabeceio = parseVal("cabeceio"),
                    passeCurto = parseVal("passeCurto"),
                    cruzamento = parseVal("cruzamento"),
                    drible = parseVal("drible"),
                    passe = parseVal("passe"),
                    primeiroToque = parseVal("primeiroToque"),
                    finalizacao = parseVal("finalizacao"),
                    chuteDeLonge = parseVal("chuteDeLonge"),
                    controleBola = parseVal("controleBola"),
                    posicionamento = parseVal("posicionamento"),
                    concentracao = parseVal("concentracao"),
                    sangueFrio = parseVal("sangueFrio"),
                    antecipacao = parseVal("antecipacao"),
                    bravura = parseVal("bravura"),
                    trabalhoEquipe = parseVal("trabalhoEquipe"),
                    decisao = parseVal("decisao"),
                    semBola = parseVal("semBola"),
                    visaoJogo = parseVal("visaoJogo"),
                    criatividade = parseVal("criatividade"),
                    agressividade = parseVal("agressividade"),
                    lideranca = parseVal("lideranca"),
                    regularidade = parseVal("regularidade"),
                    agilidade = parseVal("agilidade"),
                    impulsao = parseVal("impulsao"),
                    forca = parseVal("forca"),
                    velocidade = parseVal("velocidade"),
                    aceleracao = parseVal("aceleracao"),
                    resistencia = parseVal("resistencia")
                )
            } catch (_: Throwable) {
                null
            }
        }
    }

    companion object {
        fun jsonToAtributos(jsonStr: String?): Atributos? = AtributosConverter().toAtributos(jsonStr)
        fun atributosToJson(atributos: Atributos?): String? = AtributosConverter().fromAtributos(atributos)
    }
}

object CalculadoraNota {

    fun calcularNota(posicao: Posicao, a: Atributos): Int {
        // 1. Físicos (40% peso)
        val notaFisica = listOf(a.velocidade, a.aceleracao, a.resistencia, a.agilidade, a.impulsao, a.forca).average()

        // 2. Mentais (35% peso)
        val mentaisRelevantes = when (posicao) {
            Posicao.GOLEIRO -> listOf(a.posicionamento, a.concentracao, a.sangueFrio, a.antecipacao, a.decisao)
            Posicao.ZAGUEIRO -> listOf(a.posicionamento, a.antecipacao, a.concentracao, a.bravura, a.decisao)
            Posicao.LATERAL -> listOf(a.trabalhoEquipe, a.decisao, a.semBola, a.posicionamento, a.concentracao)
            Posicao.VOLANTE -> listOf(a.posicionamento, a.visaoJogo, a.decisao, a.trabalhoEquipe, a.concentracao)
            Posicao.MEIA -> listOf(a.visaoJogo, a.criatividade, a.decisao, a.semBola, a.concentracao)
            Posicao.ATACANTE -> listOf(a.semBola, a.decisao, a.sangueFrio, a.criatividade, a.posicionamento)
        }
        val notaMental = mentaisRelevantes.average()

        // 3. Técnicos (25% peso)
        val tecnicosRelevantes = when (posicao) {
            Posicao.GOLEIRO -> listOf(a.reflexos, a.pegada, a.umContraUm, a.saidaDeGol, a.lancamento)
            Posicao.ZAGUEIRO -> listOf(a.desarme, a.marcacao, a.cabeceio, a.passeCurto)
            Posicao.LATERAL -> listOf(a.cruzamento, a.desarme, a.drible, a.passe, a.controleBola)
            Posicao.VOLANTE -> listOf(a.desarme, a.marcacao, a.passe, a.controleBola, a.chuteDeLonge)
            Posicao.MEIA -> listOf(a.passe, a.primeiroToque, a.drible, a.finalizacao, a.controleBola)
            Posicao.ATACANTE -> listOf(a.finalizacao, a.cabeceio, a.drible, a.cruzamento, a.primeiroToque, a.controleBola)
        }
        val notaTecnica = tecnicosRelevantes.average()

        val notaFinal = (notaFisica * 0.40) + (notaMental * 0.35) + (notaTecnica * 0.25)
        return notaFinal.roundToInt().coerceIn(1, 99)
    }

    fun gerarAtributosAleatorios(posicao: Posicao, targetBaseForce: Int = 65): Atributos {
        val base = targetBaseForce.coerceIn(40, 90)
        fun randAttr(isPrimary: Boolean = false): Int {
            val delta = if (isPrimary) Random.nextInt(-3, 10) else Random.nextInt(-12, 6)
            return (base + delta).coerceIn(35, 99)
        }

        val isGoleiro = posicao == Posicao.GOLEIRO
        val isZagueiro = posicao == Posicao.ZAGUEIRO
        val isLateral = posicao == Posicao.LATERAL
        val isVolante = posicao == Posicao.VOLANTE
        val isMeia = posicao == Posicao.MEIA
        val isAtacante = posicao == Posicao.ATACANTE

        return Atributos(
            // Técnicos
            reflexos = if (isGoleiro) randAttr(true) else randAttr(false),
            pegada = if (isGoleiro) randAttr(true) else randAttr(false),
            umContraUm = if (isGoleiro) randAttr(true) else randAttr(false),
            saidaDeGol = if (isGoleiro) randAttr(true) else randAttr(false),
            lancamento = randAttr(isGoleiro || isMeia),
            desarme = randAttr(isZagueiro || isLateral || isVolante),
            marcacao = randAttr(isZagueiro || isVolante),
            cabeceio = randAttr(isZagueiro || isAtacante),
            passeCurto = randAttr(isVolante || isMeia || isLateral),
            cruzamento = randAttr(isLateral || isMeia),
            drible = randAttr(isAtacante || isMeia || isLateral),
            passe = randAttr(isMeia || isVolante),
            primeiroToque = randAttr(isMeia || isAtacante),
            finalizacao = randAttr(isAtacante || isMeia),
            chuteDeLonge = randAttr(isMeia || isVolante || isAtacante),
            controleBola = randAttr(isMeia || isAtacante || isLateral),

            // Mentais
            posicionamento = randAttr(true),
            concentracao = randAttr(true),
            sangueFrio = randAttr(isAtacante || isGoleiro),
            antecipacao = randAttr(isZagueiro || isGoleiro),
            bravura = randAttr(isZagueiro || isVolante),
            trabalhoEquipe = randAttr(isLateral || isVolante),
            decisao = randAttr(true),
            semBola = randAttr(isAtacante || isLateral),
            visaoJogo = randAttr(isMeia || isVolante),
            criatividade = randAttr(isMeia || isAtacante),
            agressividade = randAttr(isZagueiro || isVolante),
            lideranca = randAttr(false),
            regularidade = randAttr(true),

            // Físicos
            agilidade = randAttr(isGoleiro || isLateral || isMeia || isAtacante),
            impulsao = randAttr(isGoleiro || isZagueiro || isAtacante),
            forca = randAttr(isZagueiro || isVolante || isAtacante),
            velocidade = randAttr(isLateral || isAtacante),
            aceleracao = randAttr(isLateral || isAtacante || isMeia),
            resistencia = randAttr(isLateral || isVolante)
        )
    }
}

val EXEMPLO_JOGADORES_JSON = """
{
  "jogadores": [
    {
      "id": 1,
      "nome": "Cássio Silva",
      "posicao": "GOLEIRO",
      "idade": 34,
      "valorMercado": 12000000,
      "atributos": {
        "reflexos": 88, "pegada": 85, "umContraUm": 82, "saidaDeGol": 80, "lancamento": 75,
        "desarme": 30, "marcacao": 25, "cabeceio": 40, "passeCurto": 65, "cruzamento": 20,
        "drible": 30, "passe": 60, "primeiroToque": 55, "finalizacao": 15, "chuteDeLonge": 20, "controleBola": 50,
        "posicionamento": 86, "concentracao": 84, "sangueFrio": 88, "antecipacao": 82, "bravura": 80,
        "trabalhoEquipe": 85, "decisao": 83, "semBola": 50, "visaoJogo": 70, "criatividade": 40,
        "agressividade": 60, "lideranca": 90, "regularidade": 85,
        "agilidade": 82, "impulsao": 86, "forca": 84, "velocidade": 65, "aceleracao": 68, "resistencia": 78
      }
    },
    {
      "id": 2,
      "nome": "Gustavo Gómez",
      "posicao": "ZAGUEIRO",
      "idade": 30,
      "valorMercado": 25000000,
      "atributos": {
        "reflexos": 30, "pegada": 40, "umContraUm": 50, "saidaDeGol": 20, "lancamento": 68,
        "desarme": 89, "marcacao": 88, "cabeceio": 90, "passeCurto": 78, "cruzamento": 45,
        "drible": 55, "passe": 75, "primeiroToque": 72, "finalizacao": 60, "chuteDeLonge": 50, "controleBola": 70,
        "posicionamento": 89, "concentracao": 87, "sangueFrio": 82, "antecipacao": 88, "bravura": 92,
        "trabalhoEquipe": 86, "decisao": 85, "semBola": 65, "visaoJogo": 72, "criatividade": 55,
        "agressividade": 85, "lideranca": 92, "regularidade": 88,
        "agilidade": 72, "impulsao": 88, "forca": 90, "velocidade": 75, "aceleracao": 74, "resistencia": 84
      }
    },
    {
      "id": 3,
      "nome": "Ayrton Lucas",
      "posicao": "LATERAL",
      "idade": 26,
      "valorMercado": 18000000,
      "atributos": {
        "reflexos": 35, "pegada": 45, "umContraUm": 55, "saidaDeGol": 20, "lancamento": 70,
        "desarme": 78, "marcacao": 75, "cabeceio": 65, "passeCurto": 80, "cruzamento": 84,
        "drible": 82, "passe": 78, "primeiroToque": 79, "finalizacao": 68, "chuteDeLonge": 72, "controleBola": 80,
        "posicionamento": 76, "concentracao": 78, "sangueFrio": 74, "antecipacao": 77, "bravura": 75,
        "trabalhoEquipe": 84, "decisao": 78, "semBola": 82, "visaoJogo": 76, "criatividade": 74,
        "agressividade": 70, "lideranca": 68, "regularidade": 80,
        "agilidade": 85, "impulsao": 74, "forca": 76, "velocidade": 89, "aceleracao": 90, "resistencia": 91
      }
    },
    {
      "id": 4,
      "nome": "André Trindade",
      "posicao": "VOLANTE",
      "idade": 22,
      "valorMercado": 35000000,
      "atributos": {
        "reflexos": 40, "pegada": 50, "umContraUm": 60, "saidaDeGol": 20, "lancamento": 82,
        "desarme": 86, "marcacao": 84, "cabeceio": 70, "passeCurto": 88, "cruzamento": 65,
        "drible": 78, "passe": 87, "primeiroToque": 84, "finalizacao": 65, "chuteDeLonge": 78, "controleBola": 85,
        "posicionamento": 85, "concentracao": 84, "sangueFrio": 86, "antecipacao": 84, "bravura": 82,
        "trabalhoEquipe": 89, "decisao": 86, "semBola": 78, "visaoJogo": 86, "criatividade": 78,
        "agressividade": 80, "lideranca": 76, "regularidade": 85,
        "agilidade": 80, "impulsao": 76, "forca": 82, "velocidade": 78, "aceleracao": 80, "resistencia": 89
      }
    },
    {
      "id": 5,
      "nome": "Giorgian De Arrascaeta",
      "posicao": "MEIA",
      "idade": 29,
      "valorMercado": 40000000,
      "atributos": {
        "reflexos": 35, "pegada": 40, "umContraUm": 50, "saidaDeGol": 15, "lancamento": 88,
        "desarme": 55, "marcacao": 50, "cabeceio": 68, "passeCurto": 92, "cruzamento": 88,
        "drible": 90, "passe": 93, "primeiroToque": 91, "finalizacao": 86, "chuteDeLonge": 85, "controleBola": 92,
        "posicionamento": 84, "concentracao": 82, "sangueFrio": 89, "antecipacao": 86, "bravura": 70,
        "trabalhoEquipe": 85, "decisao": 91, "semBola": 86, "visaoJogo": 94, "criatividade": 95,
        "agressividade": 60, "lideranca": 82, "regularidade": 86,
        "agilidade": 88, "impulsao": 70, "forca": 72, "velocidade": 78, "aceleracao": 82, "resistencia": 80
      }
    },
    {
      "id": 6,
      "nome": "Pedro Guilherme",
      "posicao": "ATACANTE",
      "idade": 26,
      "valorMercado": 38000000,
      "atributos": {
        "reflexos": 30, "pegada": 35, "umContraUm": 45, "saidaDeGol": 15, "lancamento": 60,
        "desarme": 40, "marcacao": 35, "cabeceio": 89, "passeCurto": 82, "cruzamento": 60,
        "drible": 82, "passe": 78, "primeiroToque": 88, "finalizacao": 93, "chuteDeLonge": 82, "controleBola": 86,
        "posicionamento": 92, "concentracao": 85, "sangueFrio": 92, "antecipacao": 88, "bravura": 78,
        "trabalhoEquipe": 80, "decisao": 89, "semBola": 90, "visaoJogo": 80, "criatividade": 82,
        "agressividade": 68, "lideranca": 75, "regularidade": 86,
        "agilidade": 78, "impulsao": 84, "forca": 86, "velocidade": 78, "aceleracao": 80, "resistencia": 82
      }
    }
  ]
}
""".trimIndent()
