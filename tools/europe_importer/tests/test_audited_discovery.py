from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from tools.europe_importer.audited_discovery import (
    install_current_squad_only_discovery,
    install_p1532_discovery_bridge,
)


class _Client:
    def __init__(self):
        self.calls = []

    def get(self, endpoint, params):
        self.calls.append((endpoint, params))
        if params.get("prop") == "sections":
            return {
                "parse": {
                    "sections": [
                        {"index": "5", "line": "First Team", "level": "2"},
                        {"index": "6", "line": "Goalkeepers", "level": "3"},
                        {"index": "7", "line": "First Team out on loan", "level": "3"},
                        {"index": "8", "line": "Club staff", "level": "2"},
                    ]
                }
            }
        if params.get("prop") == "links" and params.get("section") == "5":
            return {
                "parse": {
                    "links": [
                        {"ns": 0, "title": "Active One"},
                        {"ns": 0, "title": "Active Two"},
                        {"ns": 0, "title": "Active Goalkeeper"},
                        {"ns": 0, "title": "Loaned Player"},
                        {"ns": 14, "title": "Category:Example"},
                    ]
                }
            }
        if params.get("prop") == "links" and params.get("section") == "7":
            return {"parse": {"links": [{"ns": 0, "title": "Loaned Player"}]}}
        raise AssertionError(params)

    def entities(self, qids):
        payload = {
            "Q1": {
                "claims": {
                    "P1532": [
                        {"rank": "preferred", "mainsnak": {"datavalue": {"value": {"id": "Q10"}}}}
                    ]
                }
            },
            "Q2": {
                "claims": {
                    "P1532": [
                        {"rank": "normal", "mainsnak": {"datavalue": {"value": {"id": "Q10"}}}},
                        {"rank": "normal", "mainsnak": {"datavalue": {"value": {"id": "Q11"}}}},
                    ]
                }
            },
            "Q3": {
                "claims": {
                    "P27": [
                        {"rank": "normal", "mainsnak": {"datavalue": {"value": {"id": "Q20"}}}}
                    ],
                    "P1532": [
                        {"rank": "preferred", "mainsnak": {"datavalue": {"value": {"id": "Q10"}}}}
                    ],
                }
            },
        }
        return {qid: payload[qid] for qid in qids if qid in payload}


class _Provider:
    def __init__(self):
        self.client = _Client()


class AuditedDiscoveryTest(unittest.TestCase):
    def test_current_squad_discovery_keeps_active_nested_groups_and_excludes_loans(self):
        provider = _Provider()
        install_current_squad_only_discovery(provider)
        section, links = provider.client.current_squad_links("Example F.C.")

        self.assertEqual("First Team", section)
        self.assertEqual(["Active Goalkeeper", "Active One", "Active Two"], links)
        self.assertNotIn("Loaned Player", links)
        sections_requested = [
            params.get("section")
            for _, params in provider.client.calls
            if params.get("prop") == "links"
        ]
        self.assertEqual(["5", "7"], sections_requested)

    def test_p1532_only_player_receives_transient_discovery_bridge(self):
        provider = _Provider()
        overrides = {"verifiedAsOfIso": "2026-08-18"}
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overrides.json"
            path.write_text(json.dumps(overrides), encoding="utf-8")
            install_p1532_discovery_bridge(provider, path)
            entities = provider.client.entities(["Q1", "Q2", "Q3"])

        bridged_p27 = entities["Q1"]["claims"]["P27"]
        self.assertEqual("Q10", bridged_p27[0]["mainsnak"]["datavalue"]["value"]["id"])
        self.assertNotIn("P27", entities["Q2"]["claims"])
        self.assertEqual("Q20", entities["Q3"]["claims"]["P27"][0]["mainsnak"]["datavalue"]["value"]["id"])
        self.assertEqual({"Q1"}, provider.p1532_discovery_bridged_qids)


if __name__ == "__main__":
    unittest.main()
