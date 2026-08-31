package com.example.data

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
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
    fun `seven reported Brazilian clubs override stale remote URLs and Coil decodes them`() {
        val clubs = listOf(
            4009L to "Flamengo",
            4014L to "Palmeiras",
            4002L to "Atlético Mineiro",
            4008L to "Cruzeiro",
            4017L to "Santos",
            4004L to "Botafogo",
            4018L to "São Paulo",
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
            assertCoilDecodes(loader, name, requireNotNull(uri))
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

    private fun assertCoilDecodes(loader: ImageLoader, clubName: String, uri: String) {
        val completed = AtomicBoolean(false)
        val success = AtomicReference<SuccessResult?>()
        val failure = AtomicReference<Throwable?>()
        val request = ImageRequest.Builder(context)
            .data(uri)
            .size(256)
            .listener(
                onSuccess = { _, result ->
                    success.set(result)
                    completed.set(true)
                },
                onError = { _, result ->
                    failure.set(result.throwable)
                    completed.set(true)
                },
            )
            .build()

        loader.enqueue(request)
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (!completed.get() && System.nanoTime() < deadline) {
            mainLooper.idle()
            Thread.sleep(5)
        }
        mainLooper.idle()

        failure.get()?.let { throw AssertionError("Coil failed to decode $clubName from $uri", it) }
        assertTrue("Coil timed out decoding $clubName from $uri", completed.get())
        assertNotNull("Coil did not return SuccessResult for $clubName from $uri", success.get())
    }
}
