package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 10.1 regression coverage for the immutable attribute tables used by the 60k monthly tick.
 * The performance refactor must not change training-focus classification or multipliers.
 */
class PlayerEvolutionRuleRegressionTest {

    @Test
    fun cachedAttributeCategoriesPreserveTrainingFocusSemantics() {
        assertTrue(PlayerEvolutionSystem.isPhysicalAttribute("VELOCIDADE"))
        assertTrue(PlayerEvolutionSystem.isPhysicalAttribute("resistencia"))
        assertFalse(PlayerEvolutionSystem.isPhysicalAttribute("passe"))

        assertTrue(PlayerEvolutionSystem.isTechnicalAttribute("PASSE"))
        assertTrue(PlayerEvolutionSystem.isTechnicalAttribute("primeiroToque"))
        assertFalse(PlayerEvolutionSystem.isTechnicalAttribute("lideranca"))

        assertTrue(PlayerEvolutionSystem.isMentalAttribute("LIDERANCA"))
        assertTrue(PlayerEvolutionSystem.isMentalAttribute("visaoJogo"))
        assertFalse(PlayerEvolutionSystem.isMentalAttribute("aceleracao"))
    }

    @Test
    fun cachedAttributeCategoriesPreserveFocusMultipliers() {
        assertEquals(2.0, PlayerEvolutionSystem.calculateFocusFactor("velocidade", "FISICO"), 0.0)
        assertEquals(2.0, PlayerEvolutionSystem.calculateFocusFactor("passe", "TECNICO"), 0.0)
        assertEquals(2.0, PlayerEvolutionSystem.calculateFocusFactor("lideranca", "MENTAL"), 0.0)
        assertEquals(0.5, PlayerEvolutionSystem.calculateFocusFactor("passe", "FISICO"), 0.0)
        assertEquals(1.0, PlayerEvolutionSystem.calculateFocusFactor("passe", null), 0.0)
    }
}
