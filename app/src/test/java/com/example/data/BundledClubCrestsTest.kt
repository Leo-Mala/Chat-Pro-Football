package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BundledClubCrestsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        BundledClubCrests.resetForTests()
    }

    @Test
    fun `seven reported Brazilian clubs override stale remote URLs with certified factual assets`() {
        val clubs = listOf(
            4009L to "Flamengo",
            4014L to "Palmeiras",
            4002L to "Atlético Mineiro",
            4008L to "Cruzeiro",
            4017L to "Santos",
            4004L to "Botafogo",
            4018L to "São Paulo",
        )

        for ((id, name) in clubs) {
            val remote = "https://invalid.example.test/${id}.svg"
            val expectedPath = "club_crests/factual_${id}.webp"
            val uri = BundledClubCrests.resolve(context, id, remote)
            assertEquals(
                "$name should resolve the factual bundled crest",
                "file:///android_asset/$expectedPath",
                uri,
            )
            assertEquals(expectedPath, BundledClubCrests.assetPathFor(context, id))
            context.assets.open(expectedPath).use { input ->
                assertTrue("$name certified crest must not be empty", input.read() >= 0)
            }
        }
    }

    @Test
    fun `certified runtime bundle contains exactly 2524 structurally valid WebP crests`() {
        val names = context.assets.list("club_crests")?.toList().orEmpty()
        assertEquals(2524, names.size)
        assertEquals(617, names.count { it.startsWith("factual_") && it.endsWith(".webp") })
        assertEquals(1907, names.count { it.startsWith("club_") && it.endsWith(".webp") })
        assertTrue("Certified crest runtime must contain only WebP files", names.all { it.endsWith(".webp") })

        for (name in names) {
            context.assets.open("club_crests/$name").use { input ->
                val header = ByteArray(12)
                val bytesRead = input.read(header)
                assertEquals("club_crests/$name must contain a complete WebP header", 12, bytesRead)
                assertEquals("RIFF", header.copyOfRange(0, 4).toString(Charsets.US_ASCII))
                assertEquals("WEBP", header.copyOfRange(8, 12).toString(Charsets.US_ASCII))
            }
        }
    }
}
