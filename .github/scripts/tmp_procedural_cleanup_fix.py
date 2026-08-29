from pathlib import Path

for file in [
    "app/src/main/java/com/example/data/AuditedLowerTierClubCoverage2026_27.kt",
    "app/src/main/java/com/example/data/AuditedFactualBaselinesA3_2026_27.kt",
]:
    p = Path(file)
    if p.exists():
        text = p.read_text()
        text = text.replace("audited club source_DATASET_VERSION", "AUDITED_SOURCE_VERSION")
        p.write_text(text)
