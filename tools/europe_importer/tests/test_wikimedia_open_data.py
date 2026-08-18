from __future__ import annotations

import unittest

from tools.europe_importer.wikimedia_open_data import (
    WIKIPEDIA_PAGES,
    _shirt_number,
    _statement_for_team,
    _time_to_iso,
)


class WikimediaOpenDataHelpersTest(unittest.TestCase):
    def test_all_premier_league_clubs_have_wikipedia_page_mapping(self):
        self.assertEqual(20, len(WIKIPEDIA_PAGES))
        self.assertEqual("Arsenal F.C.", WIKIPEDIA_PAGES["Arsenal FC"])
        self.assertEqual("Hull City A.F.C.", WIKIPEDIA_PAGES["Hull City"])

    def test_full_precision_wikidata_time_becomes_iso_date(self):
        self.assertEqual(
            "1998-12-17",
            _time_to_iso({"time": "+1998-12-17T00:00:00Z", "precision": 11}),
        )
        self.assertIsNone(_time_to_iso({"time": "+1998-00-00T00:00:00Z", "precision": 9}))

    def test_expired_membership_is_not_current(self):
        entity = {
            "claims": {
                "P54": [
                    {
                        "rank": "normal",
                        "mainsnak": {"datavalue": {"value": {"id": "Q1"}}},
                        "qualifiers": {
                            "P582": [
                                {
                                    "datavalue": {
                                        "value": {
                                            "time": "+2025-06-30T00:00:00Z",
                                            "precision": 11,
                                        }
                                    }
                                }
                            ]
                        },
                    }
                ]
            }
        }
        self.assertIsNone(_statement_for_team(entity, "Q1"))

    def test_current_membership_prefers_qualifier_shirt_number(self):
        statement = {
            "rank": "preferred",
            "mainsnak": {"datavalue": {"value": {"id": "Q1"}}},
            "qualifiers": {"P1618": [{"datavalue": {"value": "17"}}]},
        }
        entity = {
            "claims": {
                "P54": [statement],
                "P1618": [{"mainsnak": {"datavalue": {"value": "9"}}}],
            }
        }
        membership = _statement_for_team(entity, "Q1")
        self.assertIsNotNone(membership)
        self.assertEqual(17, _shirt_number(entity, membership or {}))


if __name__ == "__main__":
    unittest.main()
