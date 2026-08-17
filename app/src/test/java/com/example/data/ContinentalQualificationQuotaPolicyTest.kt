package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinentalQualificationQuotaPolicyTest {

    private val conmebolCountries = listOf(
        "Brasil",
        "Argentina",
        "Bolívia",
        "Chile",
        "Colômbia",
        "Equador",
        "Paraguai",
        "Peru",
        "Uruguai",
        "Venezuela"
    )

    @Test
    fun conmebolLibertadoresUsesTwentySixBaseAndSixSupplementalSlots() {
        val plan = requireNotNull(
            ContinentalQualificationQuotaPolicy.planFor("CONMEBOL", "CONTINENTAL_T1")
        )

        assertTrue(plan.enabled)
        assertEquals(32, plan.targetFieldSize)
        assertEquals(6, plan.supplementalSlots)
        assertEquals(5, plan.directCountryQuotas.getValue("Brasil"))
        assertEquals(5, plan.directCountryQuotas.getValue("Argentina"))
        conmebolCountries.drop(2).forEach { country ->
            assertEquals(2, plan.directCountryQuotas.getValue(country))
        }
        assertEquals(26, plan.directCountryQuotas.values.sum())
    }

    @Test
    fun conmebolSudamericanaUsesTwentyEightBaseAndFourSupplementalSlots() {
        val plan = requireNotNull(
            ContinentalQualificationQuotaPolicy.planFor("CONMEBOL", "CONTINENTAL_T2")
        )

        assertTrue(plan.enabled)
        assertEquals(32, plan.targetFieldSize)
        assertEquals(4, plan.supplementalSlots)
        assertEquals(6, plan.directCountryQuotas.getValue("Brasil"))
        assertEquals(6, plan.directCountryQuotas.getValue("Argentina"))
        conmebolCountries.drop(2).forEach { country ->
            assertEquals(2, plan.directCountryQuotas.getValue(country))
        }
        assertEquals(28, plan.directCountryQuotas.values.sum())
    }

    @Test
    fun quotaSelectionPreservesBasePlacesAndKeepsTierFieldsDisjoint() {
        val candidates = conmebolUniverse()
        val tier1Plan = requireNotNull(
            ContinentalQualificationQuotaPolicy.planFor("CONMEBOL", "CONTINENTAL_T1")
        )
        val tier2Plan = requireNotNull(
            ContinentalQualificationQuotaPolicy.planFor("CONMEBOL", "CONTINENTAL_T2")
        )

        val tier1 = ContinentalQualificationQuotaPolicy.selectField(candidates, tier1Plan)
        val tier2 = ContinentalQualificationQuotaPolicy.selectField(
            candidates = candidates,
            plan = tier2Plan,
            excludedTeamIds = tier1.teams.map { it.id }.toSet()
        )

        assertEquals(32, tier1.teams.size)
        assertEquals(32, tier2.teams.size)
        assertTrue(tier1.teams.map { it.id }.toSet().intersect(tier2.teams.map { it.id }.toSet()).isEmpty())

        assertEquals(5, tier1.directSelectedByCountry.getValue("Brasil"))
        assertEquals(5, tier1.directSelectedByCountry.getValue("Argentina"))
        assertEquals(6, tier1.supplementalTeamIds.size)

        assertEquals(6, tier2.directSelectedByCountry.getValue("Brasil"))
        assertEquals(6, tier2.directSelectedByCountry.getValue("Argentina"))
        assertEquals(4, tier2.supplementalTeamIds.size)

        conmebolCountries.drop(2).forEach { country ->
            assertEquals(2, tier1.directSelectedByCountry.getValue(country))
            assertEquals(2, tier2.directSelectedByCountry.getValue(country))
        }
    }

    @Test
    fun currentDefaultDataCanFillBothCONMEBOLFieldsUsingOnlyTopFlightClubs() {
        var nextId = 1L
        val candidates = conmebolCountries.flatMap { country ->
            DefaultData.getTeamsForCountry(country)
                .filter { it.division == 1 }
                .map { template ->
                    Team(
                        id = nextId++,
                        name = template.name,
                        city = template.city,
                        state = template.state,
                        country = country,
                        division = template.division,
                        rating = template.rating,
                        stadiumName = template.stadium
                    )
                }
        }.sortedWith(
            compareBy<Team> { it.division }
                .thenByDescending { it.rating }
                .thenBy { it.id }
        )

        val fields = CupCompetitionSystem.selectContinentalFields(candidates, "CONMEBOL")

        assertEquals(32, fields.tier1.size)
        assertEquals(32, fields.tier2.size)
        assertTrue(fields.tier3.isEmpty())
        assertEquals(64, fields.allTeamIds.size)
        assertTrue((fields.tier1 + fields.tier2).all { it.division == 1 })
        assertEquals(conmebolCountries.toSet(), (fields.tier1 + fields.tier2).map { it.country }.toSet())
    }

    @Test
    fun missingCountryQuotaIsRedistributedWithoutShrinkingField() {
        val candidates = conmebolUniverse().filterNot { it.country == "Bolívia" }
        val plan = requireNotNull(
            ContinentalQualificationQuotaPolicy.planFor("CONMEBOL", "CONTINENTAL_T1")
        )

        val selected = ContinentalQualificationQuotaPolicy.selectField(candidates, plan)

        assertEquals(32, selected.teams.size)
        assertEquals(0, selected.directSelectedByCountry.getValue("Bolívia"))
        assertEquals(8, selected.supplementalTeamIds.size)
        assertEquals(32, selected.teams.map { it.id }.toSet().size)
    }

    @Test
    fun conmebolTierThreeIsDisabledWithoutChangingOtherConfederations() {
        val tier3 = requireNotNull(
            ContinentalQualificationQuotaPolicy.planFor("CONMEBOL", "CONTINENTAL_T3")
        )

        assertFalse(tier3.enabled)
        assertEquals(0, tier3.targetFieldSize)
        assertTrue(ContinentalQualificationQuotaPolicy.hasExplicitPolicy("CONMEBOL"))
        assertFalse(ContinentalQualificationQuotaPolicy.hasExplicitPolicy("UEFA"))
        assertEquals(null, ContinentalQualificationQuotaPolicy.planFor("UEFA", "CONTINENTAL_T1"))
    }

    private fun conmebolUniverse(): List<Team> {
        var nextId = 1L
        return conmebolCountries.flatMapIndexed { countryIndex, country ->
            (0 until 20).map { teamIndex ->
                Team(
                    id = nextId++,
                    name = "$country Clube ${teamIndex + 1}",
                    city = "Cidade",
                    state = country.take(2),
                    country = country,
                    division = 1,
                    rating = 100 - countryIndex * 2 - teamIndex
                )
            }
        }
    }
}
