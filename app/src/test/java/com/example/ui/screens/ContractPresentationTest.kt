package com.example.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContractPresentationTest {

    @Test
    fun `53 weeks keeps regular presentation`() {
        val result = contractPresentation(53)

        assertEquals(ContractAttention.REGULAR, result.attention)
        assertEquals("53 semanas restantes", result.weeksText)
        assertNull(result.badgeText)
    }

    @Test
    fun `52 weeks starts last year highlight`() {
        val result = contractPresentation(52)

        assertEquals(ContractAttention.LAST_YEAR, result.attention)
        assertEquals("52 semanas restantes", result.weeksText)
        assertEquals("ÚLTIMO ANO", result.badgeText)
    }

    @Test
    fun `two weeks remains in last year highlight`() {
        val result = contractPresentation(2)

        assertEquals(ContractAttention.LAST_YEAR, result.attention)
        assertEquals("2 semanas restantes", result.weeksText)
        assertEquals("ÚLTIMO ANO", result.badgeText)
    }

    @Test
    fun `one week uses critical contract presentation`() {
        val result = contractPresentation(1)

        assertEquals(ContractAttention.EXPIRING_THIS_WEEK, result.attention)
        assertEquals("1 semana restante", result.weeksText)
        assertEquals("ENCERRA ESTA SEMANA", result.badgeText)
    }

    @Test
    fun `expired or negative duration is normalized without changing persistence`() {
        val zero = contractPresentation(0)
        val negative = contractPresentation(-3)

        assertEquals(ContractAttention.EXPIRING_THIS_WEEK, zero.attention)
        assertEquals("0 semanas restantes", zero.weeksText)
        assertEquals(ContractAttention.EXPIRING_THIS_WEEK, negative.attention)
        assertEquals("0 semanas restantes", negative.weeksText)
    }
}
