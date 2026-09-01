from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
path = ROOT / "app/src/test/java/com/example/usecase/FullSeasonSimulationDoesNotHangRegressionTest.kt"
text = path.read_text(encoding="utf-8")
old_import = "import kotlinx.coroutines.test.runTest\n"
new_import = "import kotlinx.coroutines.runBlocking\n"
old_sig = "fun `week four monthly boundary completes inside a bounded execution`() = runTest {"
new_sig = "fun `week four monthly boundary completes inside a bounded execution`() = runBlocking {"
if old_import not in text or old_sig not in text:
    raise SystemExit("generated bounded regression no longer matches expected source")
text = text.replace(old_import, new_import, 1).replace(old_sig, new_sig, 1)
path.write_text(text, encoding="utf-8")
print("Bounded week-four regression now measures real wall-clock time instead of virtual test time.")
