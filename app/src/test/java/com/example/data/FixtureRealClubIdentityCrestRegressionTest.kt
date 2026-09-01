package com.example.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class FixtureRealClubIdentityCrestRegressionTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: GameRepository

    @Before fun setup() {
        BrasfootRealClubIdentity.resetForTests()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
            .allowMainThreadQueries().build()
        repository = GameRepository(db)
    }
    @After fun tearDown() { db.close(); BrasfootRealClubIdentity.resetForTests() }

    @Test fun `legacy fixture id resolves canonical name and crest from same identity`() = runTest {
        val replacement = BrasfootRealClubIdentity.Replacement(
            legacyTeamId = 4242L, country = "Argentina", division = 1,
            legacySlotName = "Quilmes 1", realClubName = "Rosario Test",
            crestFileName = "rosario_test.png"
        )
        BrasfootRealClubIdentity.install(listOf(replacement))
        repository.saveTeams(listOf(Team(id = 4242L, name = "Quilmes 1", city = "Rosario", state = "SF", country = "Argentina", division = 1)))
        val direct = requireNotNull(repository.getTeam(4242L))
        val reactive = requireNotNull(repository.getTeamFlow(4242L).first())
        assertEquals(4242L, direct.id)
        assertEquals("Rosario Test", direct.name)
        assertEquals("Rosario Test", reactive.name)
        assertEquals("file:///android_asset/club_crests/rosario_test.png", BrasfootRealClubIdentity.crestAssetUriFor("Argentina", direct.name))
    }
}
