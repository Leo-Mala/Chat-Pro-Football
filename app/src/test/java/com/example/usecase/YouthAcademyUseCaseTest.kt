package com.example.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouthAcademyUseCaseTest {

    private val useCase = YouthAcademyUseCase()

    @Test
    fun `generated prospects use canonical json and remain parseable`() {
        val raw = useCase.generateInitialProspects("Brasil")

        assertTrue(raw.trim().startsWith("["))
        val prospects = useCase.parseProspects(raw)
        assertEquals(2, prospects.size)
        assertTrue(prospects.all { it.name.isNotBlank() })
        assertTrue(prospects.all { it.position in setOf("GOL", "ZAG", "LAT", "VOL", "MEI", "ATA") })
    }

    @Test
    fun `legacy semicolon prospects remain readable and can be normalized`() {
        val legacy = "Bruno Mendes;15;ZAG;40;75|Rodrigo Silva;16;ATA;44;82"

        val prospects = useCase.parseProspects(legacy)
        assertEquals(2, prospects.size)
        assertEquals("Bruno Mendes", prospects[0].name)
        assertEquals(75, prospects[0].potential)
        assertEquals("Rodrigo Silva", prospects[1].name)

        val normalized = useCase.serializeProspects(prospects)
        assertTrue(normalized.startsWith("["))
        assertFalse(normalized.contains(";"))
        assertEquals(prospects, useCase.parseProspects(normalized))
    }

    @Test
    fun `invalid prospect payload fails closed without inventing players`() {
        assertTrue(useCase.parseProspects("conteudo-invalido").isEmpty())
        assertTrue(useCase.parseProspects("").isEmpty())
    }
}
