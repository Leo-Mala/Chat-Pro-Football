from __future__ import annotations

import unittest

from tools.europe_importer.audited_exclusions import apply_verified_squad_exclusions
from tools.europe_importer.open_data_postprocess import _select_current_sport_country


def _item(qid: str, *, rank: str = "normal", start: str | None = None, end: str | None = None):
    row = {
        "rank": rank,
        "mainsnak": {"datavalue": {"value": {"id": qid}}},
    }
    qualifiers = {}
    if start:
        qualifiers["P580"] = [{
            "datavalue": {"value": {"time": f"+{start}T00:00:00Z", "precision": 11}}
        }]
    if end:
        qualifiers["P582"] = [{
            "datavalue": {"value": {"time": f"+{end}T00:00:00Z", "precision": 11}}
        }]
    if qualifiers:
        row["qualifiers"] = qualifiers
    return row


def _player(name: str, birth: str, team_id: int):
    return {
        "player": {"name": name, "birth": {"date": birth}},
        "statistics": [{"team": {"id": team_id}, "games": {"position": "Midfielder"}}],
    }


class OverrideRegressionTest(unittest.TestCase):
    def test_declan_rice_historical_ireland_does_not_override_current_england(self):
        # Regression model for a player with senior international history for more than one country:
        # an ended Ireland statement must not win over the current preferred England statement.
        declan_rice = {
            "claims": {
                "P1532": [
                    _item("Q27", end="2019-02-12"),   # Ireland - historical/ended
                    _item("Q21", rank="preferred", start="2019-03-05"),  # England - current
                ]
            }
        }
        self.assertEqual(
            ("Q21", "PREFERRED"),
            _select_current_sport_country(declan_rice, "2026-08-18"),
        )

    def test_squad_exclusion_is_safe_noop_when_upstream_discovery_already_fixed_it(self):
        raw = {
            "teamsResponse": {"response": [{"team": {"id": 10, "name": "Liverpool FC"}}]},
            "playersResponse": {"response": []},
        }
        audit = {"verifiedOverridesUsed": []}
        overrides = {
            "squadExclusions": [{
                "club": "Liverpool FC",
                "fullName": "Ronald Araújo",
                "birthDateIso": "1999-03-07",
                "source": "https://example.test/barcelona",
                "reason": "Current Barcelona player",
            }]
        }
        apply_verified_squad_exclusions(raw, audit, overrides)
        self.assertEqual([], raw["playersResponse"]["response"])
        self.assertEqual("NOT_PRESENT", audit["verifiedOverridesUsed"][0]["status"])

    def test_squad_exclusion_removes_only_exact_club_and_birth_identity(self):
        raw = {
            "teamsResponse": {"response": [
                {"team": {"id": 10, "name": "Wrong Club"}},
                {"team": {"id": 20, "name": "Right Club"}},
            ]},
            "playersResponse": {"response": [
                _player("Alex Smith", "2000-01-01", 10),
                _player("Alex Smith", "2001-01-01", 20),
            ]},
        }
        audit = {"verifiedOverridesUsed": []}
        overrides = {
            "squadExclusions": [{
                "club": "Wrong Club",
                "fullName": "Alex Smith",
                "birthDateIso": "2000-01-01",
                "source": "https://example.test/official",
                "reason": "False membership",
            }]
        }
        apply_verified_squad_exclusions(raw, audit, overrides)
        remaining = raw["playersResponse"]["response"]
        self.assertEqual(1, len(remaining))
        self.assertEqual("2001-01-01", remaining[0]["player"]["birth"]["date"])
        self.assertEqual("REMOVED", audit["verifiedOverridesUsed"][0]["status"])

    def test_squad_exclusion_fails_closed_on_same_name_wrong_birth(self):
        raw = {
            "teamsResponse": {"response": [{"team": {"id": 10, "name": "Wrong Club"}}]},
            "playersResponse": {"response": [_player("Alex Smith", "2001-01-01", 10)]},
        }
        audit = {"verifiedOverridesUsed": []}
        overrides = {
            "squadExclusions": [{
                "club": "Wrong Club",
                "fullName": "Alex Smith",
                "birthDateIso": "2000-01-01",
                "source": "https://example.test/official",
                "reason": "False membership",
            }]
        }
        with self.assertRaisesRegex(RuntimeError, "identity mismatch"):
            apply_verified_squad_exclusions(raw, audit, overrides)


if __name__ == "__main__":
    unittest.main()
