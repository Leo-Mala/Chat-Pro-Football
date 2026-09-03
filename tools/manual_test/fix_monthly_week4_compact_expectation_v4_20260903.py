from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
path = ROOT / "app/src/test/java/com/example/usecase/MonthlyEvolutionWeekFourRegressionTest.kt"
text = path.read_text(encoding="utf-8")
old = '''        assertEquals(playerCount, plan.expectedPlayerCount)\n        assertEquals(playerCount, plan.expectedInputs.size)\n        assertTrue(useCase.commitMonthlyEvolution(plan))\n'''
new = '''        assertEquals(playerCount, plan.expectedPlayerCount)\n        assertTrue("production plan must not retain full monthly snapshots", plan.expectedInputs.isEmpty())\n        assertEquals(playerCount, requireNotNull(plan.expectedUniverseCommitment).size)\n        assertTrue(useCase.commitMonthlyEvolution(plan))\n'''
if old not in text:
    raise SystemExit("week4 compact expectation block not found")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("week4 compact commitment expectation patched")
