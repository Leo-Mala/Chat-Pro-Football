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
        val replacement = BrasfootRealClubIdentity.Replacement(
            legacyTeamId = 4242L,
            country = "Brasil",
            division = 2,
            legacySlotName = "Belo Horizonte FC 2",
            realClubName = "Clube Real de Teste",
            crestFileName = "clube_real_teste.png"
        )
        BrasfootRealClubIdentity.install(listOf(replacement))

        assertEquals(4242L, BrasfootRealClubIdentity.legacyTeamIdFor("Brasil", "Clube Real de Teste"))
        assertEquals(replacement, BrasfootRealClubIdentity.replacementForLegacyTeamId(4242L))
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
    fun auditedSvgCrestKeepsLegacyIdentityAndAssetPath() {
        val replacement = BrasfootRealClubIdentity.Replacement(
            legacyTeamId = 5151L,
            country = "México",
            division = 2,
            legacySlotName = "Slot México",
            realClubName = "Clube SVG de Teste",
            crestFileName = "clube_svg.svg"
        )

        BrasfootRealClubIdentity.install(listOf(replacement))

        assertEquals(5151L, BrasfootRealClubIdentity.legacyTeamIdFor("México", "Clube SVG de Teste"))
        assertEquals(replacement, BrasfootRealClubIdentity.replacementForLegacyTeamId(5151L))
        assertEquals(
            "file:///android_asset/club_crests/clube_svg.svg",
            BrasfootRealClubIdentity.crestAssetUriFor("México", "Clube SVG de Teste")
        )
    }

    @Test
    fun rejectsUnsupportedOrNestedCrestPath() {
        assertThrows(IllegalArgumentException::class.java) {
            BrasfootRealClubIdentity.install(
                listOf(BrasfootRealClubIdentity.Replacement(7001L, "Brasil", 2, "Slot A", "Real A", "a.webp"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BrasfootRealClubIdentity.install(
                listOf(BrasfootRealClubIdentity.Replacement(7002L, "Brasil", 2, "Slot B", "Real B", "nested/b.svg"))
            )
        }
    }

    @Test
    fun unknownClubOrLegacyIdDoesNotInventIdentity() {
        assertNull(BrasfootRealClubIdentity.legacySlotNameFor("Brasil", "Desconhecido"))
        assertNull(BrasfootRealClubIdentity.crestAssetUriFor("Brasil", "Desconhecido"))
        assertNull(BrasfootRealClubIdentity.replacementForLegacyTeamId(999_999L))
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

    @Test
    fun rejectsRealNameThatCollidesWithAnotherLegacySlotAfterNormalization() {
        val ambiguousPlan = listOf(
            BrasfootRealClubIdentity.Replacement(1001L, "Brasil", 2, "São Paulo FC 2", "Real A", "a.png"),
            BrasfootRealClubIdentity.Replacement(1002L, "Brasil", 2, "Slot B", "Sao-Paulo FC 2", "b.png")
        )

        assertThrows(IllegalArgumentException::class.java) {
            BrasfootRealClubIdentity.install(ambiguousPlan)
        }
        assertEquals(0, BrasfootRealClubIdentity.installedReplacementCount())
    }
}
