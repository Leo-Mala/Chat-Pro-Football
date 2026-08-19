from __future__ import annotations

import unittest

from tools.efootball.crosswalks import derive_position_crosswalk, validate_position_crosswalk
from tools.efootball.match_engine import probable_matches, secure_matches


def fc(player_id, long_name, short_name, dob, pos="ST", height=180, weight=75, foot="Right", nation="England"):
    return {
        "player_id": str(player_id), "long_name": long_name, "short_name": short_name, "dob": dob,
        "player_positions": pos, "height_cm": str(height), "weight_kg": str(weight),
        "preferred_foot": foot, "nationality_name": nation, "overall": "70", "potential": "75",
        "club_team_id": "1", "club_name": "Fixture FC", "league_id": "1", "league_name": "Fixture League",
    }


def pb(konami, names, full, dob, reg=12, height=180, weight=75, foot=0, nationality="204"):
    positions = [0] * 13
    positions[reg] = 2
    return {
        "konamiID": str(konami), "playerName": names, "shirtName": None, "fullName": full,
        "jpPlayerName": None, "cnPlayerName": None, "fakeName": None, "fakeShirtName": None,
        "fakeJpPlayerName": None, "fakeCnPlayerName": None, "nationalities": [nationality], "age": 25,
        "birthdate": dob, "height": height, "weight": weight, "strongFoot": foot, "strongHand": 0,
        "starRating": 3, "registeredPosition": reg, "positions": positions, "youthClub": None,
        "update_at": "2026-08-13", "info_id": None, "added": "fixture", "source": ["fixture"],
        "game_versions": ["eFootball 2026"], "real_face": None, "is_system": False, "base_konami_id": None,
    }


class MatchEngineTest(unittest.TestCase):
    def test_a1_a2_and_a3_are_secure_and_one_to_one(self):
        fc_rows = [
            fc(1, "Erling Braut Haaland", "E. Haaland", "2000-07-21"),
            fc(2, "Robert Lewandowski", "R. Lewandowski", "1988-08-21"),
            fc(3, "Yoon-Sung Kang강윤성", "Y. Kang", "1997-07-15", pos="RB", height=175),
        ]
        pb_rows = [
            pb(101, ["Erling Haaland"], "Erling Braut Haaland", "2000-07-21"),
            pb(102, ["Robert Lewandowski", "R. Lewandowski"], "Robert L. Lewandowski", "1988-08-21"),
            pb(103, ["Kang Yun-Sung"], "Yoon Seong Kang", "1997-07-15", reg=3, height=175),
        ]
        rows, fc_ids, konami_ids = secure_matches(fc_rows, pb_rows)
        self.assertEqual(["A1", "A2", "A3"], [row["matchLevel"] for row in rows])
        self.assertEqual(3, len(fc_ids))
        self.assertEqual(3, len(konami_ids))

    def test_initial_surname_collision_is_never_secure(self):
        fc_rows = [
            fc(10, "Joshua Murphy", "J. Murphy", "1995-02-24", pos="LM"),
            fc(11, "Jacob Kai Murphy", "J. Murphy", "1995-02-24", pos="RW"),
        ]
        pb_rows = [pb(59797, ["J. Murphy", "MURPHY"], "Josh Murphy", "1995-02-24", reg=10)]
        rows, _, _ = secure_matches(fc_rows, pb_rows)
        self.assertEqual([], rows)

    def test_probable_report_preserves_multiple_candidates(self):
        calibration_fc = []
        calibration_pb = []
        anchors = {0:"GK",1:"CB",2:"LB",3:"RB",4:"CDM",5:"CM",6:"LM",7:"RM",8:"CAM",9:"LW",10:"RW",11:"ST",12:"ST"}
        for code, position in anchors.items():
            for n in range(5):
                pid = 1000 + code * 10 + n
                name = f"Calibration {code} {n}"
                calibration_fc.append(fc(pid, name, f"C{code}{n}", f"1990-{code+1:02d}-{n+1:02d}", pos=position))
                calibration_pb.append(pb(pid, [name], name, f"1990-{code+1:02d}-{n+1:02d}", reg=code))
        secure, matched_fc, matched_pb = secure_matches(calibration_fc, calibration_pb)
        fc_by_id = {row["player_id"]: row for row in calibration_fc}
        pb_by_id = {row["konamiID"]: row for row in calibration_pb}
        crosswalk = derive_position_crosswalk({(row["fc26PlayerId"], row["konamiId"]) for row in secure}, fc_by_id, pb_by_id)
        validate_position_crosswalk(crosswalk)

        target_fc = [
            fc(2001, "Joshua Murphy", "J. Murphy", "1995-02-24", pos="RW"),
            fc(2002, "Jacob Kai Murphy", "J. Murphy", "1995-02-24", pos="RW"),
        ]
        target_pb = [pb(59797, ["Jacob Murphy", "J. Murphy"], "Josh Murphy", "1995-02-24", reg=10)]
        probable, ids, _ = probable_matches(calibration_fc + target_fc, target_pb, matched_fc, matched_pb, crosswalk)
        self.assertEqual({"59797"}, ids)
        self.assertGreaterEqual(len(probable[0]["candidates"]), 1)
        self.assertEqual("PROBABLE", probable[0]["confidence"])


if __name__ == "__main__":
    unittest.main()
