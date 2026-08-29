package com.example.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BrasfootPatchCrestsTest {
    @After
    fun tearDown() = BrasfootPatchCrests.resetForTests()

    @Test
    fun resolvesBundledPngWithoutChangingOriginalFileName() {
        BrasfootPatchCrests.install(
            listOf(BrasfootPatchCrests.Entry("Brasil", "Cruzeiro", "cruzeiro_bra.png"))
        )
        val uri = BrasfootPatchCrests.assetUriFor("brásil", "CRUZEIRO")
        assertEquals("file:///android_asset/club_crests/cruzeiro_bra.png", uri)
        assertTrue(BrasfootPatchCrests.isBundledAssetUri(uri))
        assertFalse(BrasfootPatchCrests.isBundledAssetUri("https://example.invalid/crest.png"))
        assertFalse(BrasfootPatchCrests.isBundledAssetUri(null))
        assertEquals(1, BrasfootPatchCrests.installedCount())
    }

    @Test
    fun resolvesBundledSvgWithoutConvertingItsFormat() {
        BrasfootPatchCrests.install(
            listOf(BrasfootPatchCrests.Entry("México", "Clube Real", "club-real.svg"))
        )
        assertEquals(
            "file:///android_asset/club_crests/club-real.svg",
            BrasfootPatchCrests.assetUriFor("mexico", "CLUBE REAL")
        )
    }

    @Test
    fun unknownClubHasNoInventedLocalCrest() {
        assertNull(BrasfootPatchCrests.assetUriFor("Brasil", "Clube desconhecido"))
    }

    @Test
    fun rejectsOneCrestAssignedToDifferentClubs() {
        assertThrows(IllegalArgumentException::class.java) {
            BrasfootPatchCrests.install(
                listOf(
                    BrasfootPatchCrests.Entry("Brasil", "Clube A", "same.svg"),
                    BrasfootPatchCrests.Entry("Brasil", "Clube B", "SAME.SVG")
                )
            )
        }
    }

    @Test
    fun rejectsUnsupportedOrPathTraversingCrestNames() {
        assertThrows(IllegalArgumentException::class.java) {
            BrasfootPatchCrests.install(
                listOf(BrasfootPatchCrests.Entry("Brasil", "Clube A", "crest.webp"))
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            BrasfootPatchCrests.install(
                listOf(BrasfootPatchCrests.Entry("Brasil", "Clube A", "../crest.png"))
            )
        }
    }
}
