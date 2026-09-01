package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.data.GamePreferencesRepository
import com.example.data.dataStore
import com.example.data.local.SlotDatabaseFactory
import com.example.data.repository.GameSaveRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CoachAvatarPersistenceTest {

    private lateinit var context: Context
    private lateinit var databaseFactory: SlotDatabaseFactory
    private lateinit var saveRepository: GameSaveRepository
    private lateinit var repository: GamePreferencesRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        databaseFactory = SlotDatabaseFactory(context)
        saveRepository = GameSaveRepository(context, databaseFactory)
        repository = GamePreferencesRepository(context.dataStore, context, saveRepository)
    }

    @After
    fun tearDown() = runBlocking {
        saveRepository.closeAllDatabases()
        context.dataStore.edit { it.clear() }
        context.getSharedPreferences("brasfut_retro_saves", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        Unit
    }

    @Test
    fun `selected avatar is isolated by career and survives repository reopen`() = runBlocking {
        repository.setCoachAvatarId("1", "coach_3")
        repository.setCoachAvatarId("2", "coach_4")

        assertEquals("coach_3", repository.coachAvatarId("1").first())
        assertEquals("coach_4", repository.coachAvatarId("2").first())
        assertEquals(
            GamePreferencesRepository.DEFAULT_COACH_AVATAR_ID,
            repository.coachAvatarId("3").first()
        )

        val reopened = GamePreferencesRepository(context.dataStore, context, saveRepository)
        assertEquals("coach_3", reopened.coachAvatarId("1").first())
        assertEquals("coach_4", reopened.coachAvatarId("2").first())
    }
}
