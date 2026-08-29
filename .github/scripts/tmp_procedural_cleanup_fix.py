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

# Generator must use named Team arguments; Team.isPlayerControlled precedes rating in the entity.
p = Path("app/src/test/java/com/example/data/CareerSeedTemplateGeneratorTest.kt")
text = p.read_text()
old = '''                    add(Team(globalId, template.name, template.city, template.state, countryKey, template.division, template.rating, template.stadium, DefaultData.getLogoForTeam(template.name, countryKey), false))'''
new = '''                    add(
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
                    )'''
if old not in text:
    raise SystemExit("CareerSeedTemplateGeneratorTest Team constructor marker not found")
text = text.replace(old, new, 1)

# JUnit4 requires a Unit/void test method. Context.deleteDatabase returns Boolean, so force Unit
# as the final runBlocking expression instead of accidentally exposing Boolean to the runner.
old = '''        context.deleteDatabase(dbName)
    }
}
'''
new = '''        context.deleteDatabase(dbName)
        Unit
    }
}
'''
if old not in text:
    raise SystemExit("CareerSeedTemplateGeneratorTest final expression marker not found")
text = text.replace(old, new, 1)
p.write_text(text)

# This integration test exists solely for the removed factual cross-league player/loan importer.
p = Path("app/src/test/java/com/example/data/EuropeanCrossLeagueLoanImportTest.kt")
if p.exists():
    p.unlink()

# Player has no generic metadataJson field; fictional-name/determinism assertions are sufficient.
p = Path("app/src/test/java/com/example/data/ProceduralRosterOnlyTest.kt")
text = p.read_text()
text = text.replace('        assertFalse(first.any { it.metadataJson.contains("FC26", ignoreCase = true) })\n', '')
text = text.replace('import org.junit.Assert.assertFalse\n', '')
p.write_text(text)
