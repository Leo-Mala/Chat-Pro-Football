package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `seven reported Brazilian clubs override stale remote URLs and Coil decodes them`() = runBlocking {
        val clubs = listOf(
            4009 to "Flamengo",
            4014 to "Palmeiras",
            4002 to "Atlético Mineiro",
            4008 to "Cruzeiro",
            4017 to "Santos",
            4004 to "Botafogo",
            4018 to "São Paulo",
        )
        val loader = ImageLoader.Builder(context).build()

        for ((id, name) in clubs) {
            val remote = "https://invalid.example.test/${id}.svg"
            val uri = BundledClubCrests.resolve(context, id, remote)
            assertEquals(
                "$name should resolve the factual bundled crest",
                "file:///android_asset/club_crests/factual_${id}.webp",
                uri,
            )
            val result = loader.execute(
                ImageRequest.Builder(context)
                    .data(uri)
                    .size(256)
                    .build()
            )
            assertTrue("Coil failed to decode $name from $uri: $result", result is SuccessResult)
        }
    }

    @Test
    fun `runtime bundle contains and Android decodes all 2524 club crests`() {
        val names = context.assets.list("club_crests")?.toList().orEmpty()
        assertEquals(2524, names.size)
        assertEquals(617, names.count { it.startsWith("factual_") && it.endsWith(".webp") })
        assertEquals(1907, names.count { it.startsWith("club_") })

        for (name in names) {
            context.assets.open("club_crests/$name").use { input ->
                if (!name.endsWith(".svg", ignoreCase = true)) {
                    assertNotNull(
                        "Android failed to decode club_crests/$name",
                        BitmapFactory.decodeStream(input),
                    )
                }
            }
        }
    }
}
