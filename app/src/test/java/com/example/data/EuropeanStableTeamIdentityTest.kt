package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EuropeanStableTeamIdentityTest {

    @Test
    fun `every factual UEFA top flight club has a stable id`() {
        val factualClubs = EuropeanDomesticBaseline2026_27.associations.flatMap { baseline ->
            baseline.verifiedTopFlightClubs.map { club -> baseline.country to club }
        }

        val resolved = factualClubs.map { (country, club) ->
            val id = StableTeamIdentityRegistry.idFor(country, club)
            assertNotNull("Sem teamId estável para $country/$club", id)
            requireNotNull(id)
        }

        assertEquals(factualClubs.size, resolved.size)
        assertEquals("Há colisão global entre clubes factuais UEFA", resolved.size, resolved.distinct().size)
    }

    @Test
    fun `audited 9_11A2 lower-tier clubs have unique stable ids in existing country windows`() {
        val targets = Fc26RemainingClubCoverage2026_27.lowerTierFactualTargets
        val ids = targets.map { target ->
            val id = StableTeamIdentityRegistry.idFor(target.country, target.canonicalName)
            assertNotNull("Sem teamId estável para ${target.country}/${target.canonicalName}", id)
            requireNotNull(id).also { resolved ->
                assertTrue(
                    "${target.country}/${target.canonicalName} caiu fora do namespace reservado: $resolved",
                    resolved in StableTeamIdentityRegistry.BASELINE_REAL_TEAM_ID_FLOOR until
                        StableTeamIdentityRegistry.BASELINE_REAL_TEAM_ID_CEILING_EXCLUSIVE
                )
            }
        }

        assertEquals(47, targets.size)
        assertEquals(ids.size, ids.distinct().size)
        val allKnownIds = StableTeamIdentityRegistry.all.map { it.id }
        assertEquals(allKnownIds.size, allKnownIds.distinct().size)
    }

    @Test
    fun `new factual identities live below the virtual namespace and outside legacy team blocks`() {
        val generatedCountries = EuropeanDomesticBaseline2026_27.verifiedTopFlightCountries -
            setOf("Inglaterra", "Espanha")

        generatedCountries.forEach { country ->
            val baseline = requireNotNull(EuropeanDomesticBaseline2026_27.forCountry(country))
            baseline.verifiedTopFlightClubs.forEach { club ->
                val id = requireNotNull(StableTeamIdentityRegistry.idFor(country, club))
                assertTrue(
                    "$country/$club caiu fora do namespace reservado: $id",
                    id in StableTeamIdentityRegistry.BASELINE_REAL_TEAM_ID_FLOOR until
                        StableTeamIdentityRegistry.BASELINE_REAL_TEAM_ID_CEILING_EXCLUSIVE
                )
                assertTrue(id < GlobalFootballSystem.VIRTUAL_TEAM_ID_FLOOR)
            }
        }
    }

    @Test
    fun `historical England and Spain ids remain frozen`() {
        assertEquals(2L, StableTeamIdentityRegistry.idFor("Inglaterra", "Arsenal FC"))
        assertEquals(27L, StableTeamIdentityRegistry.idFor("Inglaterra", "Coventry City"))
        assertEquals(201L, StableTeamIdentityRegistry.idFor("Espanha", "Real Madrid"))
        assertEquals(206L, StableTeamIdentityRegistry.idFor("Espanha", "Athletic Club"))
    }

    @Test
    fun `baseline hash snapshots and explicit collision overrides do not drift`() {
        assertEquals(103474L, StableTeamIdentityRegistry.idFor("Itália", "Atalanta"))
        assertEquals(105115L, StableTeamIdentityRegistry.idFor("Alemanha", "FC Bayern München"))
        assertEquals(117552L, StableTeamIdentityRegistry.idFor("Portugal", "FC Porto"))
        assertEquals(122151L, StableTeamIdentityRegistry.idFor("Países Baixos", "Ajax"))
        assertEquals(122152L, StableTeamIdentityRegistry.idFor("Países Baixos", "Go Ahead Eagles"))
        assertEquals(186494L, StableTeamIdentityRegistry.idFor("Grécia", "Olympiacos"))

        assertEquals(105143L, StableTeamIdentityRegistry.idFor("Alemanha", "Borussia Dortmund"))
        assertEquals(105144L, StableTeamIdentityRegistry.idFor("Alemanha", "MSV Duisburg"))
        assertEquals(108961L, StableTeamIdentityRegistry.idFor("Alemanha", "TSV Havelse"))
        assertEquals(108962L, StableTeamIdentityRegistry.idFor("Alemanha", "SSV Jahn Regensburg"))
    }

    @Test
    fun `repeated lookup is deterministic and independent from current division`() {
        val first = requireNotNull(StableTeamIdentityRegistry.idFor("Itália", "Atalanta"))
        val second = requireNotNull(StableTeamIdentityRegistry.idFor("Itália", "Atalanta"))
        assertEquals(first, second)

        val identity = requireNotNull(StableTeamIdentityRegistry.identityForId(first))
        assertEquals("Itália", identity.country)
        assertEquals("Atalanta", identity.canonicalName)
    }
}
