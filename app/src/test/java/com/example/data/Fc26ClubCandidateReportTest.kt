package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Fc26ClubCandidateReportTest {
    @Test
    fun `writes deterministic unresolved and missing target diagnostics`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataset = requireNotNull(Fc26NormalizedDatasetLoader.loadValidatedOrNull(context.assets))
        val teams = buildCurrentProFootballUniverse()

        val matches = Fc26ClubMatcher.match(dataset, teams)
        val unresolved = matches.filter { it.status != Fc26ClubMatchStatus.MATCHED }
        val audits = Fc26ClubMatcher.auditCandidates(dataset, teams)

        assertEquals(unresolved.size, audits.size)
        assertEquals(
            unresolved.map { it.sourceClubTeamId }.sorted(),
            audits.map { it.sourceClubTeamId }.sorted()
        )
        assertTrue(audits.none { it.candidates.size > 5 })

        val materializationCounts = audits
            .groupingBy { it.materializationStatus.name }
            .eachCount()
            .toSortedMap()

        val repoRoot = findRepositoryRoot()
        val gson = GsonBuilder().setPrettyPrinting().create()

        val candidatesReport = linkedMapOf<String, Any?>(
            "datasetSource" to dataset.manifest.datasetSource,
            "datasetVersion" to dataset.manifest.datasetVersion,
            "datasetClubs" to dataset.sourceClubs.size,
            "targetTeams" to teams.size,
            "unresolvedClubs" to audits.size,
            "unresolvedPlayers" to audits.sumOf { it.playerCount },
            "materializationCounts" to materializationCounts,
            "policy" to linkedMapOf(
                "candidateScoresAreReviewOnly" to true,
                "fuzzyCandidatesCanAutoMatch" to false,
                "sourceLeagueCountryAloneCanMatch" to false
            ),
            "clubs" to audits
        )

        val stableMissing = audits.filter {
            it.materializationStatus == Fc26TargetMaterializationStatus.STABLE_TARGET_MISSING
        }
        val missingReport = linkedMapOf<String, Any?>(
            "datasetSource" to dataset.manifest.datasetSource,
            "datasetVersion" to dataset.manifest.datasetVersion,
            "stableTargetsMissing" to stableMissing.size,
            "playersBlockedByStableTargetsMissing" to stableMissing.sumOf { it.playerCount },
            "unresolvedWithoutStableIdentity" to audits.count {
                it.materializationStatus == Fc26TargetMaterializationStatus.NO_STABLE_IDENTITY
            },
            "unknownCountryContext" to audits.count {
                it.materializationStatus == Fc26TargetMaterializationStatus.UNKNOWN_COUNTRY_CONTEXT
            },
            "clubs" to stableMissing
        )

        writeJson(File(repoRoot, "reports/fc26_unmatched_candidates.json"), candidatesReport, gson)
        writeJson(File(repoRoot, "reports/fc26_missing_target_clubs.json"), missingReport, gson)

        println(
            "FC26_CANDIDATE_REPORT unresolved=${audits.size} " +
                "stableMissing=${stableMissing.size} " +
                "blockedPlayers=${stableMissing.sumOf { it.playerCount }}"
        )
    }

    private fun buildCurrentProFootballUniverse(): List<Team> = buildList {
        for (countryKey in GlobalFootballSystem.keys) {
            for (template in DefaultData.getTeamsForCountry(countryKey)) {
                add(
                    Team(
                        id = GlobalFootballSystem.getGlobalId(countryKey, template.name),
                        name = template.name,
                        city = template.city,
                        state = template.state,
                        country = countryKey,
                        division = template.division,
                        rating = template.rating,
                        stadiumName = template.stadium
                    )
                )
            }
        }
    }

    private fun writeJson(file: File, value: Any, gson: Gson) {
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(value) + "\n", Charsets.UTF_8)
    }

    private fun findRepositoryRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (File(current, "settings.gradle.kts").isFile) return current
            current = current.parentFile ?: return@repeat
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
