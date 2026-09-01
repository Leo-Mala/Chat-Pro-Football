from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
match_path = ROOT / "app/src/main/java/com/example/ui/viewmodel/GameViewModelMatch.kt"
text = match_path.read_text(encoding="utf-8")
old = '''    _matchAwayScore.value = committedFixture.awayScore ?: awayScore
    _matchMinute.value = 90
    _matchState.value = GameViewModel.MatchState.FINISHED
    return committedFixture
}'''
new = '''    _matchAwayScore.value = committedFixture.awayScore ?: awayScore
    _matchMinute.value = 90
    // The caller still owns the weekly lifecycle. FINISHED is published only after CPU fixtures
    // and the durable weekly close have completed.
    return committedFixture
}'''
if old not in text:
    raise SystemExit("finishPreparedLiveFixture boundary no longer matches expected source")
match_path.write_text(text.replace(old, new, 1), encoding="utf-8")

# Strengthen the already-generated regression so Skip cannot reintroduce an early FINISHED publish.
test_path = ROOT / "app/src/test/java/com/example/ui/viewmodel/PostGameReturnToCentralRegressionTest.kt"
test = test_path.read_text(encoding="utf-8")
needle = '''        assertTrue(!exit.contains("processWeekEndEconomicAndEvolution"))
    }
'''
replacement = '''        assertTrue(!exit.contains("processWeekEndEconomicAndEvolution"))

        val preparedStart = match.indexOf("private suspend fun GameViewModel.finishPreparedLiveFixture")
        val preparedEnd = match.indexOf("fun GameViewModel.skipLiveMatch", preparedStart)
        val prepared = match.substring(preparedStart, preparedEnd)
        assertTrue(!prepared.contains("_matchState.value = GameViewModel.MatchState.FINISHED"))

        val skipStart = match.indexOf("fun GameViewModel.skipLiveMatch")
        val statsStart = match.indexOf("suspend fun GameViewModel.processMatchEventsAndStats", skipStart)
        val skip = match.substring(skipStart, statsStart)
        val skipCpu = skip.lastIndexOf("simulateCpuMatchesForCurrentWeek()")
        val skipClose = skip.lastIndexOf("processWeekEndEconomicAndEvolution()")
        val skipFinished = skip.lastIndexOf("_matchState.value = GameViewModel.MatchState.FINISHED")
        assertTrue(skipCpu >= 0)
        assertTrue(skipClose > skipCpu)
        assertTrue(skipFinished > skipClose)
    }
'''
if needle not in test:
    raise SystemExit("PostGame regression no longer matches expected generated source")
test_path.write_text(test.replace(needle, replacement, 1), encoding="utf-8")
print("Skip path now exposes FINISHED only after the durable weekly close.")
