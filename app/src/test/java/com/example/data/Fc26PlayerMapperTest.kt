package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Fc26PlayerMapperTest {
    @Test fun `FC26 overall and potential are preserved exactly`() {
        val source = samplePlayer(overall = 88, potential = 93)
        val mapped = Fc26PlayerMapper.toPlayer(source, teamId = 42L)

        assertEquals(88, mapped.force)
        assertEquals(93, mapped.potential)
        assertEquals(42L, mapped.teamId)
        assertEquals(source.atributos, mapped.atributos)
        assertEquals(source.atributos.finalizacao, mapped.getAtributosObject().finalizacao)
        assertFalse(mapped.isOnLoan)
        assertNull(mapped.originalTeamId)
        assertTrue(mapped.atributosJson.orEmpty().contains("\"sourcePlayerId\":12345"))
        assertTrue(mapped.atributosJson.orEmpty().contains("\"primaryPosition\":\"CAM\""))
        assertTrue(mapped.atributosJson.orEmpty().contains("\"alternativePositions\":[\"CM\"]"))
        assertFalse(mapped.atributosJson.orEmpty().contains("\"reflexos\""))
        val metadata = mapped.sourceMetadataOrNull()
        assertEquals(12345L, metadata?.sourcePlayerId)
        assertEquals("FC26", metadata?.source)
    }

    @Test fun `default attributes use compact lossless Room representation`() {
        val converter = AtributosConverter()
        val encoded = converter.fromAtributos(Atributos())

        assertEquals("{}", encoded)
        assertEquals(Atributos(), converter.toAtributos(encoded))
    }

    @Test fun `position mapping keeps current gameplay codes`() {
        assertEquals("GOL", Fc26PositionMapper.simplified(listOf("GK")))
        assertEquals("ZAG", Fc26PositionMapper.simplified(listOf("CB")))
        assertEquals("LAT", Fc26PositionMapper.simplified(listOf("LB")))
        assertEquals("VOL", Fc26PositionMapper.simplified(listOf("CDM")))
        assertEquals("MEI", Fc26PositionMapper.simplified(listOf("CAM")))
        assertEquals("ATA", Fc26PositionMapper.simplified(listOf("RW")))
    }

    @Test fun `source monetary fields use one central EUR BRL conversion`() {
        val source = samplePlayer(valueEur = 1_000_000L, wageEur = 10_000L)
        val mapped = Fc26PlayerMapper.toPlayer(source, 42L)
        assertEquals(Fc26MoneyPolicy.eurToGameCurrency(1_000_000L), mapped.market_value)
        assertEquals(Fc26MoneyPolicy.eurToGameCurrency(10_000L), mapped.salary)
    }

    private fun samplePlayer(
        overall: Int = 80,
        potential: Int = 85,
        valueEur: Long = 10_000_000L,
        wageEur: Long = 50_000L
    ) = Fc26NormalizedPlayer(
        sourcePlayerId = 12345L,
        shortName = "J. Test",
        fullName = "Jogador de Teste",
        sourceAge = 24,
        birthDateIso = "2001-01-15",
        heightCm = 180,
        weightKg = 75,
        nationality = "Brazil",
        positions = listOf("CAM", "CM"),
        overall = overall,
        potential = potential,
        valueEur = valueEur,
        wageEur = wageEur,
        leagueId = 13L,
        leagueName = "Premier League",
        clubTeamId = 1L,
        clubName = "Arsenal",
        clubPosition = "CAM",
        clubLoanedFrom = null,
        contractUntilYear = 2029,
        preferredFoot = "Right",
        weakFoot = 4,
        skillMoves = 4,
        internationalReputation = 3,
        workRate = "High/High",
        releaseClauseEur = 20_000_000L,
        summaryPace = 78,
        summaryShooting = 82,
        summaryPassing = 85,
        summaryDribbling = 86,
        summaryDefending = 61,
        summaryPhysic = 76,
        atributos = Atributos(
            reflexos = 15, pegada = 12, umContraUm = 14, saidaDeGol = 13,
            lancamento = 84, desarme = 65, marcacao = 60, cabeceio = 70,
            passeCurto = 88, cruzamento = 80, drible = 87, passe = 86,
            primeiroToque = 88, finalizacao = 82, chuteDeLonge = 84, controleBola = 89,
            posicionamento = 83, concentracao = 87, sangueFrio = 88, antecipacao = 76,
            bravura = 80, trabalhoEquipe = 88, decisao = 89, semBola = 83, visaoJogo = 90,
            criatividade = 89, agressividade = 70, lideranca = 82, regularidade = 86,
            agilidade = 85, impulsao = 73, forca = 75, velocidade = 78, aceleracao = 80, resistencia = 84
        )
    )
}
