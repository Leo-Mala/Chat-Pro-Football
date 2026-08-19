from __future__ import annotations

import unittest

from tools.efootball.classification import age_consistency


class ClassificationTest(unittest.TestCase):
    def test_age_bands(self):
        self.assertEqual("NORMAL", age_consistency({"birthdate":"2000-08-20", "age":25})["status"])
        self.assertEqual("SUSPICIOUS", age_consistency({"birthdate":"2000-08-20", "age":22})["status"])
        self.assertEqual("STRONGLY_SUSPICIOUS", age_consistency({"birthdate":"1980-01-25", "age":19})["status"])


if __name__ == "__main__":
    unittest.main()
