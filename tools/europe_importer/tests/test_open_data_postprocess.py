from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.europe_importer.identity import stable_player_id
from tools.europe_importer.open_data_postprocess import (
    _select_current_sport_country,
    apply_canonical_name_overrides,
    apply_verified_open_data_facts,
    install_verified_squad_discovery_overrides,
)
from tools.europe_importer.real_pilot import (
    _apply_verified_loan_provenance,
    _rewrite_open_data_provenance,
)


def _item(qid):
    return {"mainsnak": {"datavalue": {"value": {"id": qid}}}}


def _dated_item(qid, *, rank="normal", start=None, end=None):
    row = _item(qid)
    row["rank"] = rank
    qualifiers = {}
    if start:
        qualifiers["P580"] = [{"datavalue": {"value": {"time": f"+{start}T00:00:00Z", "precision": 11}}}]
    if end:
        qualifiers["P582"] = [{"datavalue": {"value": {"time": f"+{end}T00:00:00Z", "precision": 11}}}]
    if qualifiers:
        row["qualifiers"] = qualifiers
    return row


class _FakeClient:
    def __init__(self, player_claims=None):
        self.player_claims = player_claims or {
            "P1532": [_dated_item("Q10", rank="preferred")],
            "P27": [_item("Q20")],
        }

    def page_qid(self, title):
        if title == "Rodri (footballer, born 1996)":
            return "Q1"
        raise AssertionError(title)

    def entities(self, qids):
        payload = {
            "Q1": {
                "claims": self.player_claims,
                "labels": {"en": {"value": "Test Player"}},
            },
            "Q10": {"claims": {}, "labels": {"en": {"value": "England"}}},
            "Q11": {"claims": {}, "labels": {"en": {"value": "Ireland"}}},
            "Q20": {"claims": {}, "labels": {"en": {"value": "United Kingdom"}}},
        }
        return {qid: payload[qid] for qid in qids if qid in payload}

    def current_squad_links(self, title):
        return "First-team squad", ["Existing Player"]


class _FakeProvider:
    def __init__(self, player_claims=None):
        self.client = _FakeClient(player_claims)
        self.last_audit = {
            "loanCandidates": [{
                "player": "Test Player",
                "borrowerClub": "Aston Villa",
                "status": "DETECTED_NOT_MATERIALIZED",
            }],
            "verifiedOverridesUsed": [],
            "warnings": [],
        }


def _raw_player(name="Test Player", birth="2000-01-01", team_id=7):
    return {
        "player": {
            "id": 1,
            "name": name,
            "birth": {"date": birth},
            "nationality": "United Kingdom",
        },
        "statistics": [{
            "team": {"id": team_id},
            "games": {"position": "Forward", "number": None},
        }],
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
        self.assertEqual(["Existing Player", "Rodri (footballer, born 1996)"], links)

    def test_p1532_prefers_current_preferred_and_ignores_ended_and_deprecated(self):
        entity = {"claims": {"P1532": [
            _dated_item("Q11", end="2024-01-01"),
            _dated_item("Q11", rank="deprecated"),
            _dated_item("Q10", rank="preferred", start="2019-01-01"),
        ]}}
        self.assertEqual(("Q10", "PREFERRED"), _select_current_sport_country(entity, "2026-08-18"))

    def test_p1532_ambiguous_current_statements_fail_closed_without_override(self):
        claims = {
            "P1532": [_dated_item("Q10"), _dated_item("Q11")],
            "P27": [_item("Q20")],
        }
        raw = {
            "teamsResponse": {"response": [{"team": {"id": 7, "name": "Aston Villa"}, "venue": {}}]},
            "playersResponse": {"response": [_raw_player()]},
            "transfersResponse": {"response": []},
        }
        overrides = {"verifiedAsOfIso": "2026-08-18"}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            with self.assertRaisesRegex(RuntimeError, "Ambiguous active P1532"):
                apply_verified_open_data_facts(_FakeProvider(claims), raw, path)

    def test_official_nationality_override_resolves_ambiguous_p1532(self):
        claims = {
            "P1532": [_dated_item("Q10"), _dated_item("Q11")],
            "P27": [_item("Q20")],
        }
        raw = {
            "teamsResponse": {"response": [{"team": {"id": 7, "name": "Aston Villa"}, "venue": {}}]},
            "playersResponse": {"response": [_raw_player()]},
            "transfersResponse": {"response": []},
        }
        overrides = {
            "verifiedAsOfIso": "2026-08-18",
            "nationalities": [{
                "fullName": "Test Player",
                "nationality": "England",
                "source": "https://example.test/official-country",
            }],
        }
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            provider = _FakeProvider(claims)
            result = apply_verified_open_data_facts(provider, raw, path)
        self.assertEqual("England", result["playersResponse"]["response"][0]["player"]["nationality"])
        self.assertEqual(1, provider.last_audit["sportNationality"]["playersUsingOfficialOverride"])

    def test_verified_squad_exclusion_is_club_and_birth_specific(self):
        raw = {
            "teamsResponse": {"response": [
                {"team": {"id": 7, "name": "Aston Villa"}, "venue": {}},
                {"team": {"id": 8, "name": "Chelsea FC"}, "venue": {}},
            ]},
            "playersResponse": {"response": [
                _raw_player(name="Same Name", birth="1990-01-01", team_id=7),
                _raw_player(name="Same Name", birth="1991-01-01", team_id=8),
            ]},
            "transfersResponse": {"response": []},
        }
        overrides = {
            "verifiedAsOfIso": "2026-08-18",
            "squadExclusions": [{
                "club": "Aston Villa",
                "fullName": "Same Name",
                "birthDateIso": "1990-01-01",
                "reason": "Verified false association",
                "source": "https://example.test/official",
            }],
        }
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            result = apply_verified_open_data_facts(_FakeProvider(), raw, path)
        remaining = result["playersResponse"]["response"]
        self.assertEqual(1, len(remaining))
        self.assertEqual("1991-01-01", remaining[0]["player"]["birth"]["date"])

    def test_verified_name_correction_preserves_old_stable_id(self):
        old_id = stable_player_id("Emanuel Emegha", "2003-02-03", "")
        dataset = {
            "leagues": [{"clubs": [{"players": [{
                "fullName": "Emanuel Emegha",
                "birthDateIso": "2003-02-03",
                "nationality": "Netherlands",
                "position": "ATA",
                "shirtNumber": None,
                "identityDisambiguator": "",
            }]}]}],
            "loans": [],
        }
        overrides = {"playerNames": [{
            "currentName": "Emanuel Emegha",
            "officialName": "Emmanuel Emegha",
            "source": "https://example.test/chelsea",
        }]}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            applied = apply_canonical_name_overrides(dataset, path)
        player = dataset["leagues"][0]["clubs"][0]["players"][0]
        new_id = stable_player_id(player["fullName"], player["birthDateIso"], player["identityDisambiguator"])
        self.assertEqual("Emmanuel Emegha", player["fullName"])
        self.assertEqual(old_id, new_id)
        self.assertEqual(1, len(applied))

    def test_prefers_sport_country_and_materializes_only_verified_loan(self):
        raw = {
            "teamsResponse": {"response": [
                {"team": {"id": 4, "name": "Chelsea FC"}, "venue": {}},
                {"team": {"id": 7, "name": "Aston Villa"}, "venue": {}},
            ]},
            "playersResponse": {"response": [_raw_player()]},
            "transfersResponse": {"response": []},
        }
        overrides = {
            "verifiedAsOfIso": "2026-08-18",
            "loans": [{
                "fullName": "Test Player",
                "ownerClub": "Chelsea FC",
                "borrowerClub": "Aston Villa",
                "verifiedAsOfIso": "2026-08-14",
                "source": "https://example.test/official",
            }],
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
        self.assertEqual("Loan", transfers[0]["transfers"][0]["type"])
        self.assertEqual(1, provider.last_audit["verifiedLoanCount"])

    def test_unverified_loan_candidate_is_not_materialized(self):
        raw = {
            "teamsResponse": {"response": [{"team": {"id": 7, "name": "Aston Villa"}, "venue": {}}]},
            "playersResponse": {"response": [_raw_player()]},
            "transfersResponse": {"response": []},
        }
        overrides = {"verifiedAsOfIso": "2026-08-18"}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            provider = _FakeProvider()
            result = apply_verified_open_data_facts(provider, raw, path)
        self.assertEqual([], result["transfersResponse"]["response"])
        self.assertEqual("DETECTED_NOT_MATERIALIZED", provider.last_audit["loanCandidates"][0]["status"])

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
        overrides = {"loans": [{"fullName": "Test Player", "source": "https://example.test/official"}]}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            _apply_verified_loan_provenance(rewritten, path)
        self.assertEqual("wikimedia-open-data", rewritten["provider"])
        self.assertEqual(
            ["provider://wikimedia-open-data/Inglaterra/Premier League/2026_27"],
            rewritten["leagues"][0]["sourceRefs"],
        )
        self.assertEqual(["https://example.test/official"], rewritten["loans"][0]["sourceRefs"])


if __name__ == "__main__":
    unittest.main()
