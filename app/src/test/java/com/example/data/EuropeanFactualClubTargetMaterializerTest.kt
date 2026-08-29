package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EuropeanFactualClubTargetMaterializerTest {

    @Test
    fun `dump exact preserved factual club runtime catalog`() {
        ApplicationProvider.getApplicationContext<Context>()
        EuropeanFactualClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedLowerTierClubTargetMaterializer2026_27.installIntoDefaultData()
        EuropeanAuditedFactualBaselinesA3Materializer2026_27.installIntoDefaultData()

        val workspace = requireNotNull(System.getenv("GITHUB_WORKSPACE"))
        val baseline = File(workspace, "docs/club-realization/generated-filler-slots.csv")
        val fillerIds = baseline.readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.substringBefore(',').trim().toLong() }
            .toSet()

        data class Row(val id: Long, val country: String, val division: Int, val name: String)

        val allRows = GlobalFootballSystem.keys.flatMap { country ->
            DefaultData.countriesMap.getValue(country).teams.map { team ->
                Row(
                    id = GlobalFootballSystem.getGlobalId(country, team.name),
                    country = country,
                    division = team.division,
                    name = team.name,
                )
            }
        }
        val factualRows = allRows.filter { it.id !in fillerIds }.sortedBy { it.id }
        val fillerRuntimeRows = allRows.filter { it.id in fillerIds }

        fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
        val outDir = File(workspace, "factual-club-audit").apply { mkdirs() }
        File(outDir, "preserved-factual-clubs.csv").bufferedWriter().use { writer ->
            writer.appendLine("globalId,country,division,clubName")
            factualRows.forEach { row ->
                writer.appendLine("${row.id},${csv(row.country)},${row.division},${csv(row.name)}")
            }
        }
        File(outDir, "runtime-summary.txt").writeText(
            buildString {
                appendLine("baseline_filler_ids=${fillerIds.size}")
                appendLine("runtime_rows=${allRows.size}")
                appendLine("runtime_unique_ids=${allRows.map { it.id }.distinct().size}")
                appendLine("runtime_filler_rows=${fillerRuntimeRows.size}")
                appendLine("runtime_factual_rows=${factualRows.size}")
                appendLine("runtime_factual_unique_ids=${factualRows.map { it.id }.distinct().size}")
                appendLine("stable_registry_identities=${StableTeamIdentityRegistry.all.size}")
            }
        )

        assertEquals(1907, fillerIds.size)
        assertEquals(1907, fillerRuntimeRows.size)
        assertEquals(617, factualRows.size)
        assertEquals(617, factualRows.map { it.id }.distinct().size)
        assertEquals(2524, allRows.size)
        assertEquals(2524, allRows.map { it.id }.distinct().size)
        assertTrue(factualRows.none { it.id in fillerIds })
    }
}
