package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Gerador opt-in do banco-base imutável usado para acelerar a primeira criação de carreira.
 *
 * Não roda em suites normais: o workflow de manutenção define CAREER_SEED_TEMPLATE_OUTPUT.
 * O arquivo é produzido pelo mesmo Room/schema e pelo mesmo Fc26SeedPlanner do aplicativo, evitando
 * manter uma segunda implementação de dados esportivos fora do runtime canônico.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CareerSeedTemplateGeneratorTest {
    @Test
    fun generateDeterministicProductionTemplateWhenExplicitlyRequested() = runBlocking {
        val outputPath = System.getenv("CAREER_SEED_TEMPLATE_OUTPUT").orEmpty()
        assumeTrue("CAREER_SEED_TEMPLATE_OUTPUT não definido; gerador permanece opt-in.", outputPath.isNotBlank())

        val context = ApplicationProvider.getApplicationContext<Context>()
        EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedLowerTierClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedFactualBaselinesA3Materializer2026_27.installIntoDefaultData()
        Fc26FactualAssetRuntime.initialize(context.assets)
        EuropeanFactualAssetRuntime.initialize(context.assets)

        val dataset = requireNotNull(Fc26FactualAssetRuntime.loadValidatedOrNull()) {
            "Asset FC26 VALIDATED é obrigatório para gerar o template de produção."
        }
        val teams = ProductionCareerSeedPrewarm.buildProductionTeamUniverse()
        require(teams.size >= 1_000) { "Universo de clubes incompleto: ${teams.size}" }

        val dbName = "career_seed_template_generator.db"
        context.deleteDatabase(dbName)
        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            dbName
        )
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .build()

        try {
            val repository = GameRepository(db)
            val report = EuropeanNewSaveSeedCoordinator.prepareForFc26(
                repositoryKey = repository,
                teams = teams,
                dataset = dataset
            )
            repository.runInTransaction {
                repository.saveTeams(teams)
                repository.savePlayers(emptyList())
                val sqlite = db.openHelper.writableDatabase
                sqlite.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS career_seed_template_marker (
                        id INTEGER NOT NULL PRIMARY KEY,
                        schemaVersion INTEGER NOT NULL,
                        assetSha256 TEXT NOT NULL,
                        teamCount INTEGER NOT NULL,
                        playerCount INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                sqlite.execSQL(
                    "INSERT OR REPLACE INTO career_seed_template_marker " +
                        "(id, schemaVersion, assetSha256, teamCount, playerCount) VALUES (1, ?, ?, ?, ?)",
                    arrayOf<Any>(
                        APP_DATABASE_SCHEMA_VERSION,
                        dataset.manifest.assetSha256,
                        teams.size,
                        report.bulkImportedFc26Players + report.fallbackPlayersGenerated
                    )
                )
            }

            val teamCount = repository.getAllTeams().size
            val playerCount = db.playerDao().getTotalPlayerCount()
            require(teamCount == teams.size) {
                "Template perdeu clubes: persisted=$teamCount expected=${teams.size}"
            }
            val expectedPlayerCount = report.bulkImportedFc26Players + report.fallbackPlayersGenerated
            require(playerCount == expectedPlayerCount) {
                "Template perdeu jogadores: persisted=$playerCount expected=$expectedPlayerCount"
            }
            require(report.bulkImportedFc26Players == dataset.players.size) {
                "Template FC26 incompleto: imported=${report.bulkImportedFc26Players} dataset=${dataset.players.size}"
            }

            db.openHelper.writableDatabase.query("PRAGMA quick_check").use { cursor ->
                require(cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)) {
                    "SQLite quick_check falhou no template."
                }
            }
        } finally {
            db.close()
        }

        val databaseFile = context.getDatabasePath(dbName)
        require(databaseFile.isFile && databaseFile.length() > 0L) {
            "Room não produziu o arquivo físico do template."
        }
        val output = File(outputPath)
        output.parentFile?.mkdirs()
        databaseFile.copyTo(output, overwrite = true)
        require(output.isFile && output.length() > 0L)
        context.deleteDatabase(dbName)
    }
}
