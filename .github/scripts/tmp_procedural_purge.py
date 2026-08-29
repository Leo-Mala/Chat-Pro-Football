from pathlib import Path


def read(path):
    return Path(path).read_text()


def write(path, text):
    Path(path).write_text(text)


def replace_once(path, old, new):
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, got {count}: {old[:140]!r}")
    write(path, text.replace(old, new, 1))


# MainApplication: keep factual club catalogs, remove all factual-player dataset initialization/preload.
path = "app/src/main/java/com/example/MainApplication.kt"
text = read(path)
for line in [
    "import com.example.data.Fc26FactualAssetRuntime\n",
    "import kotlinx.coroutines.CoroutineScope\n",
    "import kotlinx.coroutines.Dispatchers\n",
    "import kotlinx.coroutines.SupervisorJob\n",
    "import kotlinx.coroutines.launch\n",
]:
    text = text.replace(line, "")
text = text.replace("    private val fc26WarmupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)\n\n", "")
text = text.replace("        Fc26FactualAssetRuntime.initialize(assets)\n", "")
text = text.replace("        EuropeanFactualAssetRuntime.initialize(assets)\n", "")
start = text.find("        // Antecipamos somente a parte imutável/factual do FC26:")
if start >= 0:
    end_marker = "        }\n\n        fixCursorWindowSize()"
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit("MainApplication warmup end marker not found")
    text = text[:start] + "        // Jogadores de novas carreiras são exclusivamente procedurais.\n\n        fixCursorWindowSize()" + text[end + len(end_marker):]
write(path, text)

# Calendar generation must not have player-seed side effects.
path = "app/src/main/java/com/example/usecase/GenerateCalendarUseCase.kt"
text = read(path).replace("import com.example.data.EuropeanNewSaveSeedCoordinator\n", "")
start = text.find("        // Este método é o checkpoint usado por performStartNewGameInternal")
if start >= 0:
    end = text.find("        val allFixtures = mutableListOf<Fixture>()", start)
    if end < 0:
        raise SystemExit("calendar seed block end not found")
    text = text[:start] + "        // Calendário não materializa nem substitui jogadores.\n" + text[end:]
write(path, text)

# Repository persists exactly the caller-provided procedural players.
path = "app/src/main/java/com/example/data/repository.kt"
text = read(path)
start = text.find("    suspend fun <R> withTransaction(block: suspend () -> R): R =")
end = text.find("    suspend fun <R> runInTransaction", start)
if start < 0 or end < 0:
    raise SystemExit("repository withTransaction block not found")
text = text[:start] + "    suspend fun <R> withTransaction(block: suspend () -> R): R = db.withTransaction(block)\n\n" + text[end:]
old = '''    suspend fun saveTeams(teams: List<Team>) = db.withTransaction {
        val teamsToPersist = EuropeanNewSaveSeedCoordinator.teamsFor(this@GameRepository, teams)
        if (teamsToPersist.isNotEmpty()) {
            db.teamDao().insertTeams(teamsToPersist)
        }
    }
'''
new = '''    suspend fun saveTeams(teams: List<Team>) = db.withTransaction {
        if (teams.isNotEmpty()) db.teamDao().insertTeams(teams)
    }
'''
if old not in text:
    raise SystemExit("repository saveTeams factual block not found")
text = text.replace(old, new, 1)
old = '''    suspend fun savePlayers(players: List<Player>) = db.withTransaction {
        val seed = EuropeanNewSaveSeedCoordinator.consumePlayers(this@GameRepository, players)
        val playersToPersist = seed.players
        if (playersToPersist.isNotEmpty()) {
            val isFirstPopulation = db.playerDao().getTotalPlayerCount() == 0
            if (isFirstPopulation) {
                persistFreshPlayersWithoutSecondaryIndexChurn(playersToPersist)
            } else {
                db.playerDao().insertPlayersReplace(playersToPersist)
            }
        }
        if (seed.loans.isNotEmpty()) {
            db.playerLoanDao().insertLoans(seed.loans)
        }
    }
'''
new = '''    suspend fun savePlayers(players: List<Player>) = db.withTransaction {
        if (players.isNotEmpty()) {
            val isFirstPopulation = db.playerDao().getTotalPlayerCount() == 0
            if (isFirstPopulation) {
                persistFreshPlayersWithoutSecondaryIndexChurn(players)
            } else {
                db.playerDao().insertPlayersReplace(players)
            }
        }
    }
'''
if old not in text:
    raise SystemExit("repository savePlayers factual block not found")
text = text.replace(old, new, 1)
text = text.replace("Uma carreira nova sempre apaga Player antes do seed canônico.", "Uma carreira nova sempre apaga Player antes do seed procedural.")
text = text.replace("enquanto ~60k linhas são inseridas", "enquanto o universo global de jogadores é inserido")
write(path, text)

# New-game player materialization: prebuilt procedural DB or deterministic procedural fallback.
path = "app/src/main/java/com/example/ui/viewmodel/GameViewModel.kt"
text = read(path)
start = text.find("        // 3. Materializa o seed factual já preparado")
end = text.find("        // 4. Preparação dos metadados do GameSave em memória", start)
if start < 0 or end < 0:
    raise SystemExit("GameViewModel player materialization block not found")
replacement = '''        // 3. Jogadores: banco-base procedural para slot novo; geração determinística como fallback.
        val rosterMaterializationStartedAtNs = System.nanoTime()
        val allPlayersToSave = if (usePrebuiltCareerSeed) {
            CareerCreationPerformanceMonitor.notePersistedPlayerCount(prebuiltSeedMarker!!.playerCount)
            emptyList()
        } else {
            buildList {
                for (t in dbTeams) {
                    addAll(DefaultData.generateRosterForTeam(t.id, t.rating, t.name, t.country))
                }
            }.also { CareerCreationPerformanceMonitor.notePersistedPlayerCount(it.size) }
        }
        val rosterMaterializationMs = (System.nanoTime() - rosterMaterializationStartedAtNs) / 1_000_000L
        Log.i(
            "CareerCreationPerformance",
            "PrebuiltProceduralSeed=$usePrebuiltCareerSeed proceduralPlayers=${allPlayersToSave.size}"
        )

'''
text = text[:start] + replacement + text[end:]
old_diag = '''"DIAG criação: total=${diagnostic.totalMs}ms | FC26=${diagnostic.factualSeedMaterializationMs}ms | " +
                    "banco=${diagnostic.persistenceMs}ms | calendário=${diagnostic.competitionCalendarMs}ms"'''
new_diag = '''"DIAG criação: total=${diagnostic.totalMs}ms | jogadores=${diagnostic.rosterMaterializationMs}ms | " +
                    "banco=${diagnostic.persistenceMs}ms | calendário=${diagnostic.competitionCalendarMs}ms"'''
if old_diag not in text:
    raise SystemExit("GameViewModel diagnostic FC26 label not found")
text = text.replace(old_diag, new_diag, 1)
write(path, text)

# DefaultData: remove FC26 fallback mode and all hard-coded real-player stars.
path = "app/src/main/java/com/example/data/DefaultData.kt"
text = read(path)
old = '''    fun generateRosterForTeam(teamId: Long, teamRating: Int, teamName: String, country: String): List<Player> =
        generateRosterForTeamInternal(teamId, teamRating, teamName, country, retainFc26FallbackOnly = false)

    internal fun generateFc26FallbackRosterForTeam(
        teamId: Long,
        teamRating: Int,
        teamName: String,
        country: String
    ): List<Player> =
        generateRosterForTeamInternal(teamId, teamRating, teamName, country, retainFc26FallbackOnly = true)

    private fun generateRosterForTeamInternal(
        teamId: Long,
        teamRating: Int,
        teamName: String,
        country: String,
        retainFc26FallbackOnly: Boolean
    ): List<Player> {
'''
new = '''    fun generateRosterForTeam(teamId: Long, teamRating: Int, teamName: String, country: String): List<Player> =
        generateRosterForTeamInternal(teamId, teamRating, teamName, country)

    private fun generateRosterForTeamInternal(
        teamId: Long,
        teamRating: Int,
        teamName: String,
        country: String
    ): List<Player> {
'''
if old not in text:
    raise SystemExit("DefaultData generator signature block not found")
text = text.replace(old, new, 1)
text = text.replace("        val realStars = getRealStarsForTeam(teamName)\n\n", "")
old = '''        for (i in positions.indices) {
            val pos = positions[i]
            val matchedStar = realStars.find { it.position == pos && generatedNames.none { generated -> generated == it.name } }
            
            val name = if (matchedStar != null) {
                matchedStar.name
            } else {
                val firstName = firstNames[rand.nextInt(firstNames.size)]
                val lastName = lastNames[rand.nextInt(lastNames.size)]
                "$firstName $lastName"
            }

            generatedNames.add(name)

            val force = if (matchedStar != null) {
                matchedStar.force.coerceIn(15, 99)
            } else {
                (teamRating + rand.safeNextInt(-5, 5)).coerceIn(15, 99)
            }

            val age = if (matchedStar != null) {
                matchedStar.age.coerceIn(17, 38)
            } else {
                when (rand.nextDouble()) {
                    in 0.0..0.2 -> rand.safeNextInt(17, 20)
                    in 0.2..0.7 -> rand.safeNextInt(21, 28)
                    else -> rand.safeNextInt(29, 38)
                }
            }
'''
new = '''        for (i in positions.indices) {
            val pos = positions[i]
            val firstName = firstNames[rand.nextInt(firstNames.size)]
            val lastName = lastNames[rand.nextInt(lastNames.size)]
            val name = "$firstName $lastName"
            generatedNames.add(name)

            val force = (teamRating + rand.safeNextInt(-5, 5)).coerceIn(15, 99)
            val age = when (rand.nextDouble()) {
                in 0.0..0.2 -> rand.safeNextInt(17, 20)
                in 0.2..0.7 -> rand.safeNextInt(21, 28)
                else -> rand.safeNextInt(29, 38)
            }
'''
if old not in text:
    raise SystemExit("DefaultData real-star generation block not found")
text = text.replace(old, new, 1)
text = text.replace('''            // Consumimos acima exatamente os mesmos sorteios do elenco canônico. Para FC26, os
            // dez índices que a política descartaria param aqui, antes das alocações/cálculos caros.
            if (retainFc26FallbackOnly && !fc26FallbackRetainsCanonicalIndex(i)) continue

''', "")
star_start = text.find("    private fun fc26FallbackRetainsCanonicalIndex")
logo_start = text.find("    fun getLogoForTeam(", star_start)
if star_start < 0 or logo_start < 0:
    raise SystemExit("DefaultData FC26/real-star helper range not found")
text = text[:star_start] + text[logo_start:]
write(path, text)

# The prebuilt database marker now represents the procedural generator, not an external dataset.
Path("app/src/main/java/com/example/data/CareerSeedTemplate.kt").write_text(r'''package com.example.data

data class CareerSeedTemplateMarker(
    val schemaVersion: Int,
    val seedId: String,
    val teamCount: Int,
    val playerCount: Int
)

object CareerSeedTemplateContract {
    const val ASSET_PATH = "databases/career_seed_template.db"
    const val TABLE_NAME = "career_seed_template_marker"
    const val EXPECTED_PROCEDURAL_SEED_ID = "defaultdata-procedural-v1-30-per-club"
    const val EXPECTED_PLAYERS_PER_TEAM = 30
    const val MINIMUM_TEAM_COUNT = 1_000
}

fun GameRepository.pristineCareerSeedTemplateOrNull(): CareerSeedTemplateMarker? {
    val sqlite = db.openHelper.readableDatabase
    val tableExists = sqlite.query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '${CareerSeedTemplateContract.TABLE_NAME}' LIMIT 1"
    ).use { it.moveToFirst() }
    if (!tableExists) return null

    val marker = sqlite.query(
        "SELECT schemaVersion, assetSha256, teamCount, playerCount " +
            "FROM ${CareerSeedTemplateContract.TABLE_NAME} WHERE id = 1"
    ).use { cursor ->
        if (!cursor.moveToFirst()) return null
        CareerSeedTemplateMarker(
            schemaVersion = cursor.getInt(0),
            seedId = cursor.getString(1),
            teamCount = cursor.getInt(2),
            playerCount = cursor.getInt(3)
        )
    }

    if (marker.schemaVersion != APP_DATABASE_SCHEMA_VERSION) return null
    if (marker.seedId != CareerSeedTemplateContract.EXPECTED_PROCEDURAL_SEED_ID) return null
    if (marker.teamCount < CareerSeedTemplateContract.MINIMUM_TEAM_COUNT) return null
    if (marker.playerCount != marker.teamCount * CareerSeedTemplateContract.EXPECTED_PLAYERS_PER_TEAM) return null

    fun count(sql: String): Int = sqlite.query(sql).use { cursor ->
        if (cursor.moveToFirst()) cursor.getInt(0) else -1
    }
    if (count("SELECT COUNT(*) FROM game_save") != 0) return null
    if (count("SELECT COUNT(*) FROM teams") != marker.teamCount) return null
    if (count("SELECT COUNT(*) FROM players") != marker.playerCount) return null
    return marker
}

fun GameRepository.consumePristineCareerSeedTemplate() {
    db.openHelper.writableDatabase.execSQL(
        "DELETE FROM ${CareerSeedTemplateContract.TABLE_NAME} WHERE id = 1"
    )
}
''')

# Opt-in generator for the shipped procedural-only Room database.
Path("app/src/test/java/com/example/data/CareerSeedTemplateGeneratorTest.kt").write_text(r'''package com.example.data

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
                    add(Team(globalId, template.name, template.city, template.state, countryKey, template.division, template.rating, template.stadium, DefaultData.getLogoForTeam(template.name, countryKey), false))
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
    }
}
''')

# Runtime baseline expectations.
path = "app/src/test/java/com/example/data/CareerSeedTemplateRuntimeTest.kt"
text = read(path)
text = text.replace("marker.assetSha256", "marker.seedId")
text = text.replace("CareerSeedTemplateContract.EXPECTED_FC26_ASSET_SHA256", "CareerSeedTemplateContract.EXPECTED_PROCEDURAL_SEED_ID")
text = text.replace("60_885", "75_720")
write(path, text)

# Focused CI references procedural-only tests, not deleted dataset tests.
path = ".github/workflows/manual-test-bugfix-apk.yml"
text = read(path)
for class_name in [
    "com.example.data.Fc26DatasetAssetTest",
    "com.example.data.Fc26PlayerMapperTest",
    "com.example.data.Fc26SeedPlannerTest",
]:
    text = "\n".join(line for line in text.split("\n") if class_name not in line)
marker = "            --tests com.example.data.CareerSeedTemplateRuntimeTest \\\n"
if marker not in text:
    raise SystemExit("manual CI runtime marker not found")
text = text.replace(marker, marker + "            --tests com.example.data.ProceduralRosterOnlyTest \\\n", 1)
write(path, text)

Path("app/src/test/java/com/example/data/ProceduralRosterOnlyTest.kt").write_text(r'''package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProceduralRosterOnlyTest {
    @Test
    fun `new rosters are deterministic fictional 30-player squads`() {
        val first = DefaultData.generateRosterForTeam(990001L, 88, "Real Madrid", "Espanha")
        val second = DefaultData.generateRosterForTeam(990001L, 88, "Real Madrid", "Espanha")
        assertEquals(30, first.size)
        assertEquals(first, second)
        assertEquals(30, first.map { it.id }.distinct().size)
        val legacyRealNames = setOf("Kylian Mbappé", "Vinícius Júnior", "Jude Bellingham")
        assertTrue(first.none { it.name in legacyRealNames })
        assertFalse(first.any { it.metadataJson.contains("FC26", ignoreCase = true) })
    }
}
''')
