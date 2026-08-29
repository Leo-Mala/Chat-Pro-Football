package com.example.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BrasfootRealClubIdentityTest {
    @After
    fun tearDown() = BrasfootRealClubIdentity.resetForTests()

    @Test
    fun realClubKeepsLegacySlotAliasAndOriginalPngPath() {
        BrasfootRealClubIdentity.install(
            listOf(
                BrasfootRealClubIdentity.Replacement(
                    legacyTeamId = 4242L,
                    country = "Brasil",
                    division = 2,
                    legacySlotName = "Belo Horizonte FC 2",
                    realClubName = "Clube Real de Teste",
                    crestFileName = "clube_real_teste.png"
                )
            )
        )

        assertEquals(4242L, BrasfootRealClubIdentity.legacyTeamIdFor("Brasil", "Clube Real de Teste"))
        assertEquals(
            "Belo Horizonte FC 2",
            BrasfootRealClubIdentity.legacySlotNameFor("brásil", "CLUBE REAL DE TESTE")
        )
        assertEquals(
            "file:///android_asset/club_crests/clube_real_teste.png",
            BrasfootRealClubIdentity.crestAssetUriFor("Brasil", "Clube Real de Teste")
        )
        assertEquals(1, BrasfootRealClubIdentity.installedReplacementCount())
    }

    @Test
    fun unknownClubDoesNotInventAliasOrCrest() {
        assertNull(BrasfootRealClubIdentity.legacySlotNameFor("Brasil", "Desconhecido"))
        assertNull(BrasfootRealClubIdentity.crestAssetUriFor("Brasil", "Desconhecido"))
    }

    @Test
    fun rejectsSlotOrCrestReuse() {
        val duplicateSlot = listOf(
            BrasfootRealClubIdentity.Replacement(1001L, "Brasil", 2, "Slot A", "Real A", "a.png"),
            BrasfootRealClubIdentity.Replacement(1002L, "Brasil", 2, "Slot A", "Real B", "b.png")
        )
        assertThrows(IllegalArgumentException::class.java) {
            BrasfootRealClubIdentity.install(duplicateSlot)
        }

        val duplicateCrest = listOf(
            BrasfootRealClubIdentity.Replacement(1001L, "Brasil", 2, "Slot A", "Real A", "same.png"),
            BrasfootRealClubIdentity.Replacement(1002L, "Brasil", 2, "Slot B", "Real B", "SAME.PNG")
        )
        assertThrows(IllegalArgumentException::class.java) {
            BrasfootRealClubIdentity.install(duplicateCrest)
        }

        val duplicateId = listOf(
            BrasfootRealClubIdentity.Replacement(1001L, "Brasil", 2, "Slot A", "Real A", "a.png"),
            BrasfootRealClubIdentity.Replacement(1001L, "Brasil", 2, "Slot B", "Real B", "b.png")
        )
        assertThrows(IllegalArgumentException::class.java) {
            BrasfootRealClubIdentity.install(duplicateId)
        }
    }
}
