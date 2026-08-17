package com.example.support

import com.example.data.AppDatabase
import com.example.data.GameRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object RelationalIntegrityAssertions {

    fun assertDatabasePragmas(db: AppDatabase) {
        val sqlite = db.openHelper.writableDatabase
        sqlite.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals("PRAGMA foreign_key_check deve retornar zero violações", 0, cursor.count)
        }
        sqlite.query("PRAGMA integrity_check").use { cursor ->
            assertTrue("PRAGMA integrity_check deve retornar uma linha", cursor.moveToFirst())
            assertEquals("ok", cursor.getString(0).lowercase())
        }
    }

    suspend fun assertRepositoryReferences(repository: GameRepository) {
        val teams = repository.getAllTeams()
        val players = repository.getAllPlayers()
        val fixtures = repository.getAllFixtures()
        val teamIds = teams.map { it.id }.toSet()

        assertTrue(
            "Player.teamId deve ser null ou referenciar Team existente",
            players.all { it.teamId == null || it.teamId in teamIds }
        )
        assertTrue(
            "Fixture.homeTeamId/awayTeamId devem referenciar Team existente",
            fixtures.all { it.homeTeamId in teamIds && it.awayTeamId in teamIds }
        )
        assertTrue(
            "Contratos não podem ser negativos",
            players.all { it.contractDurationWeeks >= 0 }
        )
    }
}
