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
                        {"index": "5", "line": "First-team squad"},
                        {"index": "6", "line": "Out on loan"},
                    ]
                }
            }
        if params.get("prop") == "wikitext":
            return {
                "parse": {
                    "wikitext": (
                        "{{Fs player|name=[[Active One]]}}\n"
                        "{{Fs player|name=[[Active Two]]}}\n"
                        "===Out on loan===\n"
                        "{{Fs player|name=[[Loaned Player]]}}\n"
                        "[[Category:Example]]\n"
                    )
                }
            }
        raise AssertionError(params)

    def entities(self, qids):
        payload = {
            "Q1": {
                "claims": {
                    "P1532": [
                        {
                            "rank": "preferred",
                            "mainsnak": {"datavalue": {"value": {"id": "Q10"}}},
                        }
                    ]
                }
            },
            "Q2": {
                "claims": {
                    "P1532": [
                        {
                            "rank": "normal",
                            "mainsnak": {"datavalue": {"value": {"id": "Q10"}}},
                        },
                        {
                            "rank": "normal",
                            "mainsnak": {"datavalue": {"value": {"id": "Q11"}}},
                        },
                    ]
                }
            },
            "Q3": {
                "claims": {
                    "P27": [
                        {
                            "rank": "normal",
                            "mainsnak": {"datavalue": {"value": {"id": "Q20"}}},
                        }
                    ],
                    "P1532": [
                        {
                            "rank": "preferred",
                            "mainsnak": {"datavalue": {"value": {"id": "Q10"}}},
                        }
                    ],
                }
            },
        }
        return {qid: payload[qid] for qid in qids if qid in payload}


class _Provider:
    def __init__(self):
        self.client = _Client()


class AuditedDiscoveryTest(unittest.TestCase):
    def test_current_squad_discovery_excludes_nested_out_on_loan_section(self):
        provider = _Provider()
        install_current_squad_only_discovery(provider)
        section, links = provider.client.current_squad_links("Example F.C.")

        self.assertEqual("First-team squad", section)
        self.assertEqual(["Active One", "Active Two"], links)
        self.assertNotIn("Loaned Player", links)

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
