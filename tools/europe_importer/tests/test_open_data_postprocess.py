from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.europe_importer.open_data_postprocess import apply_verified_open_data_facts


class _FakeClient:
    def entities(self, qids):
        payload = {
            "Q1": {
                "claims": {
                    "P1532": [{
                        "mainsnak": {"datavalue": {"value": {"id": "Q10"}}}
                    }],
                    "P27": [{
                        "mainsnak": {"datavalue": {"value": {"id": "Q20"}}}
                    }],
                },
                "labels": {"en": {"value": "Test Player"}},
            },
            "Q10": {"claims": {}, "labels": {"en": {"value": "England"}}},
            "Q20": {"claims": {}, "labels": {"en": {"value": "United Kingdom"}}},
        }
        return {qid: payload[qid] for qid in qids if qid in payload}


class _FakeProvider:
    def __init__(self):
        self.client = _FakeClient()
        self.last_audit = {
            "loanCandidates": [{
                "player": "Test Player",
                "borrowerClub": "Aston Villa",
                "status": "DETECTED_NOT_MATERIALIZED",
            }],
            "verifiedOverridesUsed": [],
        }


class OpenDataPostprocessTest(unittest.TestCase):
    def test_prefers_sport_country_and_materializes_only_verified_loan(self):
        raw = {
            "teamsResponse": {"response": [
                {"team": {"id": 4, "name": "Chelsea FC"}, "venue": {}},
                {"team": {"id": 7, "name": "Aston Villa"}, "venue": {}},
            ]},
            "playersResponse": {"response": [{
                "player": {
                    "id": 1,
                    "name": "Test Player",
                    "birth": {"date": "2000-01-01"},
                    "nationality": "United Kingdom",
                },
                "statistics": [{
                    "team": {"id": 7},
                    "games": {"position": "Forward", "number": None},
                }],
            }]},
            "transfersResponse": {"response": []},
        }
        overrides = {
            "loans": [{
                "fullName": "Test Player",
                "ownerClub": "Chelsea FC",
                "borrowerClub": "Aston Villa",
                "verifiedAsOfIso": "2026-08-14",
                "source": "https://example.test/official",
            }]
        }

        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            provider = _FakeProvider()
            result = apply_verified_open_data_facts(provider, raw, path)

        player = result["playersResponse"]["response"][0]["player"]
        self.assertEqual("England", player["nationality"])
        transfers = result["transfersResponse"]["response"]
        self.assertEqual(1, len(transfers))
        transfer = transfers[0]["transfers"][0]
        self.assertEqual("Loan", transfer["type"])
        self.assertEqual(4, transfer["teams"]["out"]["id"])
        self.assertEqual(7, transfer["teams"]["in"]["id"])
        self.assertEqual(1, provider.last_audit["verifiedLoanCount"])
        self.assertEqual(
            "VERIFIED_MATERIALIZED",
            provider.last_audit["loanCandidates"][0]["status"],
        )
        self.assertEqual(1, provider.last_audit["sportNationality"]["playersUsingSportCountry"])


if __name__ == "__main__":
    unittest.main()
