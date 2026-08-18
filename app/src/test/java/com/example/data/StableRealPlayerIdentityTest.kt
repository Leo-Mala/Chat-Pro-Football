package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StableRealPlayerIdentityTest {

    @Test
    fun `identity is independent from club and normalizes harmless spelling differences`() {
        val accented = StableRealPlayerIdentity.idFor("José Álvarez", "2000-03-17")
        val plain = StableRealPlayerIdentity.idFor("Jose Alvarez", "2000-03-17")
        val upper = StableRealPlayerIdentity.idFor("  JOSE   ALVAREZ ", "2000-03-17")

        assertEquals(accented, plain)
        assertEquals(accented, upper)
        assertTrue(StableRealPlayerIdentity.isRealPlayerId(accented))
    }

    @Test
    fun `identity transliterates european letters that NFKD does not decompose`() {
        val variants = listOf(
            Triple("Martin Ødegaard", "Martin Odegaard", "1998-12-17"),
            Triple("Altay Bayındır", "Altay Bayindir", "1998-04-14"),
            Triple("Łukasz Skorupski", "Lukasz Skorupski", "1991-05-05"),
            Triple("Đorđe Petrović", "Dorde Petrovic", "1999-10-08"),
            Triple("Jørgen Strand Larsen", "Jorgen Strand Larsen", "2000-02-06")
        )

        variants.forEach { (nativeSpelling, asciiSpelling, birthDate) ->
            assertEquals(
                "Grafias equivalentes divergiram para $nativeSpelling",
                StableRealPlayerIdentity.idFor(nativeSpelling, birthDate),
                StableRealPlayerIdentity.idFor(asciiSpelling, birthDate)
            )
        }
    }

    @Test
    fun `identity rejects impossible calendar birth dates`() {
        val invalidDates = listOf(
            "2001-02-29",
            "2000-02-30",
            "2002-04-31",
            "2002-13-01",
            "2002-00-10",
            "2002-01-00"
        )

        invalidDates.forEach { invalidDate ->
            var failed = false
            try {
                StableRealPlayerIdentity.idFor("Invalid Date", invalidDate)
            } catch (_: IllegalArgumentException) {
                failed = true
            }
            assertTrue("Data impossível foi aceita: $invalidDate", failed)
        }

        // 2000 é bissexto; esta data precisa continuar válida.
        assertTrue(StableRealPlayerIdentity.isRealPlayerId(
            StableRealPlayerIdentity.idFor("Leap Year", "2000-02-29")
        ))
    }

    @Test
    fun `birth date and explicit disambiguator separate different people`() {
        val first = StableRealPlayerIdentity.idFor("Alex Silva", "2001-01-01")
        val second = StableRealPlayerIdentity.idFor("Alex Silva", "2002-01-01")
        val disambiguated = StableRealPlayerIdentity.idFor("Alex Silva", "2001-01-01", "person-2")

        assertNotEquals(first, second)
        assertNotEquals(first, disambiguated)
        assertNotEquals(second, disambiguated)
    }

    @Test
    fun `gameplay seed is deterministic but transfer keeps persisted identity and attributes`() {
        val template = EuropeanRealPlayerTemplate(
            fullName = "Jogador de Teste",
            birthDateIso = "2002-10-04",
            nationality = "Portugal",
            position = "MEI",
            shirtNumber = 8
        )

        val firstSeed = template.toGameplayPlayer(teamId = 601L, teamRating = 82)
        val secondSeed = template.toGameplayPlayer(teamId = 601L, teamRating = 82)
        assertEquals(firstSeed, secondSeed)
        assertEquals(template.stableId, firstSeed.id)

        val transferred = firstSeed.copy(teamId = 3L)
        assertEquals(firstSeed.id, transferred.id)
        assertEquals(firstSeed.force, transferred.force)
        assertEquals(firstSeed.finishing, transferred.finishing)
        assertEquals(firstSeed.passing, transferred.passing)
        assertEquals(firstSeed.market_value, transferred.market_value)
    }

    @Test
    fun `real player namespace does not overlap current procedural player namespace`() {
        val realId = StableRealPlayerIdentity.idFor("Namespace Test", "1999-12-31")
        val largestCurrentGeneratedTeamId = 9_000_000_000L
        val largestProceduralPlayerId = largestCurrentGeneratedTeamId * 1000L + 999L

        assertTrue(realId >= StableRealPlayerIdentity.REAL_PLAYER_ID_FLOOR)
        assertTrue(realId > largestProceduralPlayerId)
    }

    @Test
    fun `template derives age on 2026 season start without current clock dependency`() {
        val alreadyHadBirthday = EuropeanRealPlayerTemplate(
            "A", "2000-07-31", "França", "ZAG"
        )
        val birthdayLater = EuropeanRealPlayerTemplate(
            "B", "2000-08-02", "França", "ZAG"
        )

        assertEquals(26, alreadyHadBirthday.ageAt2026SeasonStart())
        assertEquals(25, birthdayLater.ageAt2026SeasonStart())
    }

    @Test
    fun `template rejects future or underage factual dates instead of coercing age`() {
        val invalidForGameplay = listOf(
            "2030-01-01",
            "2012-01-01",
            "2011-08-02"
        )

        invalidForGameplay.forEach { birthDate ->
            var failed = false
            try {
                EuropeanRealPlayerTemplate("Too Young", birthDate, "England", "MEI")
            } catch (_: IllegalArgumentException) {
                failed = true
            }
            assertTrue("Data factual deveria ser recusada no baseline: $birthDate", failed)
        }

        val exactlyFifteen = EuropeanRealPlayerTemplate(
            "Exactly Fifteen", "2011-08-01", "England", "MEI"
        )
        assertEquals(15, exactlyFifteen.ageAt2026SeasonStart())
    }
}
