from __future__ import annotations
import copy
import json
import tempfile
import unittest
from pathlib import Path

from tools.europe_importer.fixture_builder import build_premier_league_api_fixture
from tools.europe_importer.identity import StableTeamIdentityContract, stable_player_id
from tools.europe_importer.pipeline import normalize, run_pipeline, validate
from tools.europe_importer.providers import FixtureProvider, ProviderRequest
from tools.europe_importer.sharding import write_sharded_dataset

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / "config" / "stable_team_identity_premier_league.json"
REQUEST = ProviderRequest(country="Inglaterra", league="Premier League", season_label="2026/27")

class ImporterPipelineTest(unittest.TestCase):
    def setUp(self):
        self.contract = StableTeamIdentityContract.from_json(CONTRACT)

    def test_full_premier_league_fixture_generates_valid_canonical_dataset(self):
        with tempfile.TemporaryDirectory() as temp:
            result = run_pipeline(
                FixtureProvider(payload=build_premier_league_api_fixture()), REQUEST, self.contract,
                generated_at="2026-08-18T14:00:00Z", dataset_kind="FIXTURE", output_dir=None,
                filename="premier_league.fixture.json",
            )
            manifest = write_sharded_dataset(result.dataset, Path(temp))
            self.assertEqual(20, manifest["clubCount"])
            self.assertEqual(361, manifest["playerCount"])
            self.assertEqual(1, manifest["loanCount"])
            self.assertEqual("FIXTURE_ONLY", manifest["validationStatus"])
            self.assertEqual(20, len(manifest["datasetFiles"]))
            self.assertEqual(21, len(list(Path(temp).glob("*.json"))))
            serialized = json.dumps(result.dataset)
            self.assertNotIn('"teamId"', serialized)
            self.assertNotIn('"playerId"', serialized)
            self.assertNotIn("photo", serialized)
            self.assertNotIn("logo", serialized)
            self.assertNotIn("rating", serialized.lower())

    def test_provider_ids_never_become_game_ids(self):
        result = run_pipeline(FixtureProvider(payload=build_premier_league_api_fixture()), REQUEST, self.contract,
                              generated_at="2026-08-18T14:00:00Z", dataset_kind="FIXTURE")
        text = json.dumps(result.dataset)
        self.assertNotIn("9001", text)
        self.assertNotIn("199999", text)
        self.assertEqual(2, self.contract.id_for("Inglaterra", "Arsenal FC"))

    def test_stable_player_identity_matches_known_algorithm_contract(self):
        first = stable_player_id("Martin Ødegaard", "1998-12-17")
        alias = stable_player_id("Martin Odegaard", "1998-12-17")
        self.assertEqual(first, alias)
        self.assertGreaterEqual(first, 100_000_000_000_000)

    def test_duplicate_player_and_missing_position_groups_are_rejected(self):
        normalized = normalize(build_premier_league_api_fixture(), REQUEST)
        duplicate = copy.deepcopy(normalized["teams"][0]["players"][0])
        normalized["teams"][1]["players"].append(duplicate)
        normalized["teams"][2]["players"] = [p for p in normalized["teams"][2]["players"] if p["position"] != "GOL"]
        errors = validate(normalized, self.contract)
        self.assertTrue(any("two clubs" in e for e in errors))
        self.assertTrue(any("without goalkeepers" in e for e in errors))

    def test_inconsistent_loan_is_rejected(self):
        normalized = normalize(build_premier_league_api_fixture(), REQUEST)
        normalized["loans"][0]["borrowerProviderTeamId"] = normalized["loans"][0]["ownerProviderTeamId"]
        errors = validate(normalized, self.contract)
        self.assertTrue(any("owner=borrower" in e for e in errors))

if __name__ == "__main__":
    unittest.main()
