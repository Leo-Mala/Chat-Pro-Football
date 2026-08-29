package com.example.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BrasfootPatchCrestsTest {
    @After
    fun tearDown() = BrasfootPatchCrests.resetForTests()

    @Test
    fun resolvesBundledPngWithoutChangingOriginalFileName() {
        BrasfootPatchCrests.install(
            listOf(BrasfootPatchCrests.Entry("Brasil", "Cruzeiro", "cruzeiro_bra.png"))
        )
        assertEquals(
            "file:///android_asset/club_crests/cruzeiro_bra.png",
            BrasfootPatchCrests.assetUriFor("brásil", "CRUZEIRO")
        )
        assertEquals(1, BrasfootPatchCrests.installedCount())
    }

    @Test
    fun unknownClubHasNoInventedLocalCrest() {
        assertNull(BrasfootPatchCrests.assetUriFor("Brasil", "Clube desconhecido"))
    }

    @Test
    fun rejectsOnePngAssignedToDifferentClubs() {
        assertThrows(IllegalArgumentException::class.java) {
            BrasfootPatchCrests.install(
                listOf(
                    BrasfootPatchCrests.Entry("Brasil", "Clube A", "same.png"),
                    BrasfootPatchCrests.Entry("Brasil", "Clube B", "SAME.PNG")
                )
            )
        }
    }
}
