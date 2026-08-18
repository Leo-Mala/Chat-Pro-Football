from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.europe_importer.open_data_postprocess import (
    apply_verified_open_data_facts,
    install_verified_squad_discovery_overrides,
)
from tools.europe_importer.real_pilot import (
    _apply_verified_loan_provenance,
    _rewrite_open_data_provenance,
)


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

    def current_squad_links(self, title):
        return "First-team squad", ["Existing Player"]


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
    def test_verified_membership_augments_discovery_without_replacing_existing_links(self):
        overrides = {
            "squadMemberships": [{
                "club": "Manchester City",
                "clubWikipediaPage": "Manchester City F.C.",
                "fullName": "Rodri",
                "wikipediaTitle": "Rodri (footballer, born 1996)",
                "source": "https://example.test/official-city",
            }]
        }
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            provider = _FakeProvider()
            install_verified_squad_discovery_overrides(provider, path)
            section, links = provider.client.current_squad_links("Manchester City F.C.")

        self.assertEqual("First-team squad", section)
        self.assertEqual(
            ["Existing Player", "Rodri (footballer, born 1996)"],
            links,
        )

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
        self.assertEqual(1, provider.last_audit["sportNationality"]["playersChangedFromCitizenship"])
        self.assertEqual(0, provider.last_audit["sportNationality"]["playersUsingCitizenshipFallback"])

    def test_canonical_provenance_rewrites_provider_and_keeps_official_loan_source(self):
        dataset = {
            "provider": "api-football",
            "leagues": [{"sourceRefs": ["provider://api-football/Inglaterra/Premier League/2026_27"]}],
            "loans": [{
                "player": {"fullName": "Test Player"},
                "sourceRefs": ["provider://api-football/Inglaterra/Premier League/2026_27"],
            }],
        }
        rewritten = _rewrite_open_data_provenance(dataset)
        rewritten["provider"] = "wikimedia-open-data"

        overrides = {
            "loans": [{
                "fullName": "Test Player",
                "source": "https://example.test/official",
            }]
        }
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            _apply_verified_loan_provenance(rewritten, path)

        self.assertEqual("wikimedia-open-data", rewritten["provider"])
        self.assertEqual(
            ["provider://wikimedia-open-data/Inglaterra/Premier League/2026_27"],
            rewritten["leagues"][0]["sourceRefs"],
        )
        self.assertEqual(
            ["https://example.test/official"],
            rewritten["loans"][0]["sourceRefs"],
        )


if __name__ == "__main__":
    unittest.main()
