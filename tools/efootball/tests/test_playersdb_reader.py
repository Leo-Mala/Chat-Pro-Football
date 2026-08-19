from __future__ import annotations

import csv
import json
import tempfile
import unittest
from pathlib import Path

from tools.efootball.playersdb_reader import EXPECTED_FIELDS, read_csv, read_jsonl, semantic_mismatches


class PlayersDbReaderTest(unittest.TestCase):
    def _record(self):
        return [
            "133543", ["Erling Haaland", "E. Haaland"], ["HAALAND"], "Erling Braut Haaland",
            None, None, None, None, None, None, ["209"], 26, "2000-07-21", 195, 94,
            1, 0, 5, 12, [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2], None, "2026-08-13",
            "fixture", "2026-08-13", ["test"], ["eFootball 2026"], ["eFootball 2026"], False, None,
        ]

    def test_header_array_jsonl_and_csv_are_semantically_equivalent(self):
        with tempfile.TemporaryDirectory() as tmp:
            tmp = Path(tmp)
            jsonl = tmp / "players.jsonl"
            csv_path = tmp / "players.csv"
            jsonl.write_text(json.dumps(EXPECTED_FIELDS) + "\n" + json.dumps(self._record()) + "\n", encoding="utf-8")
            with csv_path.open("w", encoding="utf-8", newline="") as handle:
                writer = csv.writer(handle)
                writer.writerow(EXPECTED_FIELDS)
                row = []
                for field, value in zip(EXPECTED_FIELDS, self._record(), strict=True):
                    if isinstance(value, (list, dict)):
                        row.append(json.dumps(value))
                    elif value is None:
                        row.append("")
                    elif isinstance(value, bool):
                        row.append("True" if value else "False")
                    else:
                        row.append(value)
                writer.writerow(row)
            left = read_csv(csv_path)
            right = read_jsonl(jsonl)
            self.assertEqual([], semantic_mismatches(left, right))
            self.assertEqual("133543", right[0]["konamiID"])
            self.assertIsInstance(right[0]["playerName"], list)


if __name__ == "__main__":
    unittest.main()
