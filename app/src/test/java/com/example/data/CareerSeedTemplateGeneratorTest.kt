package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CareerSeedTemplateGeneratorTest {
    @Test
    fun generateDeterministicProceduralTemplateWhenExplicitlyRequested() = runBlocking {
        val outputPath = System.getenv("CAREER_SEED_TEMPLATE_OUTPUT").orEmpty()
        assumeTrue("CAREER_SEED_TEMPLATE_OUTPUT not defined", outputPath.isNotBlank())
        val context = ApplicationProvider.getApplicationContext<Context>()
        EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedLowerTierClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedFactualBaselinesA3Materializer2026_27.installIntoDefaultData()

        val teams = buildList {
            for (countryKey in GlobalFootballSystem.keys) {
                for (template in DefaultData.getTeamsForCountry(countryKey)) {
                    val globalId = GlobalFootballSystem.getGlobalId(countryKey, template.name)
                    add(
                        Team(
                            id = globalId,
                            name = template.name,
                            city = template.city,
                            state = template.state,
                            country = countryKey,
                            division = template.division,
                            isPlayerControlled = false,
                            rating = template.rating,
                            stadiumName = template.stadium,
                            logoUrl = DefaultData.getLogoForTeam(template.name, countryKey)
                        )
                    )
                }
            }
        }
        require(teams.size >= CareerSeedTemplateContract.MINIMUM_TEAM_COUNT)
        val players = buildList {
            teams.forEach { team ->
                val roster = DefaultData.generateRosterForTeam(team.id, team.rating, team.name, team.country)
                require(roster.size == CareerSeedTemplateContract.EXPECTED_PLAYERS_PER_TEAM)
                addAll(roster)
            }
        }
        require(players.size == teams.size * CareerSeedTemplateContract.EXPECTED_PLAYERS_PER_TEAM)
        require(players.map { it.id }.distinct().size == players.size)

        val dbName = "career_seed_template_generator.db"
        context.deleteDatabase(dbName)
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()
        try {
            val repository = GameRepository(db)
            repository.runInTransaction {
                repository.saveTeams(teams)
                repository.savePlayers(players)
                val sqlite = db.openHelper.writableDatabase
                sqlite.execSQL("CREATE TABLE IF NOT EXISTS career_seed_template_marker (id INTEGER NOT NULL PRIMARY KEY, schemaVersion INTEGER NOT NULL, assetSha256 TEXT NOT NULL, teamCount INTEGER NOT NULL, playerCount INTEGER NOT NULL)")
                sqlite.execSQL(
                    "INSERT OR REPLACE INTO career_seed_template_marker (id, schemaVersion, assetSha256, teamCount, playerCount) VALUES (1, ?, ?, ?, ?)",
                    arrayOf<Any>(APP_DATABASE_SCHEMA_VERSION, CareerSeedTemplateContract.EXPECTED_PROCEDURAL_SEED_ID, teams.size, players.size)
                )
            }
            require(repository.getAllTeams().size == teams.size)
            require(db.playerDao().getTotalPlayerCount() == players.size)
            db.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true))
            }
        } finally {
            db.close()
        }
        val databaseFile = context.getDatabasePath(dbName)
        require(databaseFile.isFile && databaseFile.length() > 0L)
        val output = File(outputPath)
        output.parentFile?.mkdirs()
        databaseFile.copyTo(output, overwrite = true)
        require(output.isFile && output.length() > 0L)
        context.deleteDatabase(dbName)
        Unit
    }
}
