from __future__ import annotations

import unittest

from tools.efootball.name_normalization import normalize_name


class DeterminismTest(unittest.TestCase):
    def test_normalization_is_stable(self):
        value = "  João-Félix D'Ávila  "
        first = normalize_name(value)
        for _ in range(100):
            self.assertEqual(first, normalize_name(value))


if __name__ == "__main__":
    unittest.main()
