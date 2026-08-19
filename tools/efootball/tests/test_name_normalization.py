from __future__ import annotations

import unittest

from tools.efootball.name_normalization import normalize_name, token_signature


class NameNormalizationTest(unittest.TestCase):
    def test_accents_hyphens_apostrophes_and_spaces(self):
        self.assertEqual("vinicius jose", normalize_name("  Vinícius-José  "))
        self.assertEqual("gatlin o donkor", normalize_name("Gatlin O'Donkor"))

    def test_non_latin_suffix_does_not_change_latin_identity(self):
        self.assertEqual("ji hoon jeong", normalize_name("Ji-hoon Jeong정지훈"))

    def test_narrow_a3_romanization_equivalence(self):
        self.assertEqual(token_signature("Yoon-Sung Kang"), token_signature("Kang Yoon Seong"))
        self.assertEqual(token_signature("Ji-hoon Jeong"), token_signature("Jung Ji-Hoon"))


if __name__ == "__main__":
    unittest.main()
