from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.europe_importer.verified_membership_materializer import materialize_missing_verified_memberships


class _Client:
    def qids_for_titles(self, titles):
        return {titles[0]: "Q132719377"}

    def entities(self, qids):
        payload = {
            "Q132719377": {
                "claims": {
                    "P106": [
                        {
                            "mainsnak": {
                                "datavalue": {
                                    "value": {"id": "Q937857"}
                                }
                            }
                        }
                    ],
                    "P569": [
                        {
                            "mainsnak": {
                                "datavalue": {
                                    "value": {
                                        "time": "+2005-08-04T00:00:00Z",
                                        "precision": 11,
                                    }
                                }
                            }
                        }
                    ],
                    "P1532": [
                        {
                            "mainsnak": {
                                "datavalue": {
                                    "value": {"id": "Q1041"}
                                }
                            }
                        }
                    ],
                },
                "labels": {"en": {"value": "Modou Kéba Cissé"}},
                "descriptions": {"en": {"value": "Senegalese footballer"}},
            },
            "Q1041": {
                "claims": {},
                "labels": {"en": {"value": "Senegal"}},
            },
        }
        return {qid: payload[qid] for qid in qids if qid in payload}


class _Provider:
    def __init__(self):
        self.client = _Client()
        self.last_audit = {"verifiedOverridesUsed": [], "warnings": []}


class VerifiedMembershipMaterializerTest(unittest.TestCase):
    def test_materializes_p1532_only_player_from_verified_membership(self):
        raw = {
            "teamsResponse": {
                "response": [
                    {
                        "team": {"id": 100, "name": "Aston Villa"},
                        "venue": {},
                    }
                ]
            },
            "playersResponse": {"response": []},
            "openDataAudit": {"verifiedOverridesUsed": [], "warnings": []},
        }
        overrides = {
            "positions": [
                {
                    "fullName": "Modou Kéba Cissé",
                    "position": "Defender",
                    "source": "https://example.test/official-position",
                }
            ],
            "squadMemberships": [
                {
                    "club": "Aston Villa",
                    "clubWikipediaPage": "Aston Villa F.C.",
                    "fullName": "Modou Kéba Cissé",
                    "wikipediaTitle": "Modou Kéba Cissé",
                    "source": "https://example.test/official-membership",
                }
            ],
        }
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            provider = _Provider()
            materialize_missing_verified_memberships(provider, raw, path)

        players = raw["playersResponse"]["response"]
        self.assertEqual(1, len(players))
        player = players[0]
        self.assertEqual("Modou Kéba Cissé", player["player"]["name"])
        self.assertEqual("2005-08-04", player["player"]["birth"]["date"])
        self.assertEqual("Senegal", player["player"]["nationality"])
        self.assertEqual("Defender", player["statistics"][0]["games"]["position"])

    def test_does_not_duplicate_existing_verified_membership(self):
        raw = {
            "teamsResponse": {
                "response": [
                    {"team": {"id": 100, "name": "Aston Villa"}, "venue": {}}
                ]
            },
            "playersResponse": {
                "response": [
                    {
                        "player": {
                            "id": 132719377,
                            "name": "Modou Kéba Cissé",
                            "birth": {"date": "2005-08-04"},
                            "nationality": "Senegal",
                        },
                        "statistics": [
                            {
                                "team": {"id": 100},
                                "games": {"position": "Defender", "number": None},
                            }
                        ],
                    }
                ]
            },
            "openDataAudit": {"verifiedOverridesUsed": [], "warnings": []},
        }
        overrides = {
            "squadMemberships": [
                {
                    "club": "Aston Villa",
                    "clubWikipediaPage": "Aston Villa F.C.",
                    "fullName": "Modou Kéba Cissé",
                    "wikipediaTitle": "Modou Kéba Cissé",
                    "source": "https://example.test/official-membership",
                }
            ]
        }
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            materialize_missing_verified_memberships(_Provider(), raw, path)
        self.assertEqual(1, len(raw["playersResponse"]["response"]))


if __name__ == "__main__":
    unittest.main()
