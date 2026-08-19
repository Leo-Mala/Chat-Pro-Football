#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import io
import json
from collections import Counter
from pathlib import Path
from statistics import mean

DATASET_SOURCE = "FC26"
DATASET_VERSION = "2025-09-19"
EXPECTED_ROWS = 18405
EUR_TO_BRL_REFERENCE = 6.2567
EUR_TO_BRL_REFERENCE_DATE = "2025-09-19"
EUR_TO_BRL_REFERENCE_SOURCE = "ECB reference exchange rate"

REQUIRED_COLUMNS = {
    "player_id", "short_name", "long_name", "player_positions", "overall", "potential",
    "value_eur", "wage_eur", "age", "dob", "height_cm", "weight_kg", "league_id",
    "league_name", "club_team_id", "club_name", "club_position", "club_loaned_from",
    "club_contract_valid_until_year", "nationality_name", "preferred_foot", "weak_foot",
    "skill_moves", "international_reputation", "work_rate", "release_clause_eur",
    "pace", "shooting", "passing", "dribbling", "defending", "physic",
    "attacking_crossing", "attacking_finishing", "attacking_heading_accuracy",
    "attacking_short_passing", "skill_dribbling", "skill_curve", "skill_long_passing",
    "skill_ball_control", "movement_acceleration", "movement_sprint_speed", "movement_agility",
    "movement_reactions", "movement_balance", "power_shot_power", "power_jumping",
    "power_stamina", "power_strength", "power_long_shots", "mentality_aggression",
    "mentality_interceptions", "mentality_positioning", "mentality_vision", "mentality_penalties",
    "mentality_composure", "defending_marking_awareness", "defending_standing_tackle",
    "defending_sliding_tackle", "goalkeeping_diving", "goalkeeping_handling",
    "goalkeeping_kicking", "goalkeeping_positioning", "goalkeeping_reflexes", "goalkeeping_speed",
}

OUTPUT_COLUMNS = [
    "source_player_id", "short_name", "full_name", "source_age", "dob", "height_cm", "weight_kg",
    "nationality", "positions", "overall", "potential", "value_eur", "wage_eur", "league_id",
    "league_name", "club_team_id", "club_name", "club_position", "club_loaned_from",
    "contract_until_year", "preferred_foot", "weak_foot", "skill_moves", "international_reputation",
    "work_rate", "release_clause_eur", "summary_pace", "summary_shooting", "summary_passing",
    "summary_dribbling", "summary_defending", "summary_physic",
    "reflexos", "pegada", "um_contra_um", "saida_de_gol", "lancamento", "desarme", "marcacao",
    "cabeceio", "passe_curto", "cruzamento", "drible", "passe", "primeiro_toque", "finalizacao",
    "chute_de_longe", "controle_bola", "posicionamento", "concentracao", "sangue_frio",
    "antecipacao", "bravura", "trabalho_equipe", "decisao", "sem_bola", "visao_jogo",
    "criatividade", "agressividade", "lideranca", "regularidade", "agilidade", "impulsao", "forca",
    "velocidade", "aceleracao", "resistencia",
]

ATTRIBUTE_MAPPING = {
    "reflexos": "goalkeeping_reflexes",
    "pegada": "goalkeeping_handling",
    "um_contra_um": "avg(goalkeeping_reflexes, goalkeeping_positioning, goalkeeping_diving)",
    "saida_de_gol": "goalkeeping_positioning",
    "lancamento": "GK: goalkeeping_kicking; outfield: skill_long_passing",
    "desarme": "defending_standing_tackle",
    "marcacao": "defending_marking_awareness",
    "cabeceio": "attacking_heading_accuracy",
    "passe_curto": "attacking_short_passing",
    "cruzamento": "attacking_crossing",
    "drible": "skill_dribbling",
    "passe": "avg(attacking_short_passing, skill_long_passing, mentality_vision)",
    "primeiro_toque": "skill_ball_control",
    "finalizacao": "attacking_finishing",
    "chute_de_longe": "power_long_shots",
    "controle_bola": "skill_ball_control",
    "posicionamento": "GK: goalkeeping_positioning; outfield: mentality_positioning",
    "concentracao": "avg(movement_reactions, mentality_composure)",
    "sangue_frio": "mentality_composure",
    "antecipacao": "avg(movement_reactions, mentality_interceptions)",
    "bravura": "avg(mentality_aggression, movement_reactions, mentality_composure)",
    "trabalho_equipe": "avg(attacking_short_passing, mentality_vision, movement_reactions)",
    "decisao": "avg(movement_reactions, mentality_composure, mentality_vision)",
    "sem_bola": "mentality_positioning",
    "visao_jogo": "mentality_vision",
    "criatividade": "avg(mentality_vision, skill_curve, skill_ball_control)",
    "agressividade": "mentality_aggression",
    "lideranca": "avg(movement_reactions, mentality_composure, international_reputation scaled to 20..100)",
    "regularidade": "avg(movement_reactions, mentality_composure, power_stamina)",
    "agilidade": "movement_agility",
    "impulsao": "power_jumping",
    "forca": "power_strength",
    "velocidade": "GK: goalkeeping_speed when present; outfield: movement_sprint_speed",
    "aceleracao": "movement_acceleration",
    "resistencia": "power_stamina",
}


def clean(value: str | None) -> str:
    return (value or "").replace("\t", " ").replace("\r", " ").replace("\n", " ").strip()


def as_int(row: dict[str, str], key: str, *, required: bool = True) -> int | None:
    raw = clean(row.get(key))
    if raw == "":
        if required:
            raise ValueError(f"missing required integer {key}")
        return None
    try:
        return int(round(float(raw)))
    except ValueError as exc:
        raise ValueError(f"invalid integer {key}={raw!r}") from exc


def clamp_rating(value: float | int) -> int:
    return max(1, min(99, int(round(value))))


def avg(*values: int) -> int:
    return clamp_rating(sum(values) / len(values))


def is_goalkeeper(row: dict[str, str]) -> bool:
    primary = clean(row["player_positions"]).split(",", 1)[0].strip().upper()
    return primary == "GK"


def derive_attributes(row: dict[str, str]) -> dict[str, int]:
    gk = is_goalkeeper(row)
    v = lambda k: clamp_rating(as_int(row, k))
    reactions = v("movement_reactions")
    composure = v("mentality_composure")
    vision = v("mentality_vision")
    interceptions = v("mentality_interceptions")
    aggression = v("mentality_aggression")
    short_pass = v("attacking_short_passing")
    long_pass = v("skill_long_passing")
    curve = v("skill_curve")
    ball_control = v("skill_ball_control")
    stamina = v("power_stamina")
    reputation = max(1, min(5, as_int(row, "international_reputation")))
    reputation_scaled = 20 + (reputation - 1) * 20
    gk_speed = as_int(row, "goalkeeping_speed", required=False)

    return {
        "reflexos": v("goalkeeping_reflexes"),
        "pegada": v("goalkeeping_handling"),
        "um_contra_um": avg(v("goalkeeping_reflexes"), v("goalkeeping_positioning"), v("goalkeeping_diving")),
        "saida_de_gol": v("goalkeeping_positioning"),
        "lancamento": v("goalkeeping_kicking") if gk else long_pass,
        "desarme": v("defending_standing_tackle"),
        "marcacao": v("defending_marking_awareness"),
        "cabeceio": v("attacking_heading_accuracy"),
        "passe_curto": short_pass,
        "cruzamento": v("attacking_crossing"),
        "drible": v("skill_dribbling"),
        "passe": avg(short_pass, long_pass, vision),
        "primeiro_toque": ball_control,
        "finalizacao": v("attacking_finishing"),
        "chute_de_longe": v("power_long_shots"),
        "controle_bola": ball_control,
        "posicionamento": v("goalkeeping_positioning") if gk else v("mentality_positioning"),
        "concentracao": avg(reactions, composure),
        "sangue_frio": composure,
        "antecipacao": avg(reactions, interceptions),
        "bravura": avg(aggression, reactions, composure),
        "trabalho_equipe": avg(short_pass, vision, reactions),
        "decisao": avg(reactions, composure, vision),
        "sem_bola": v("mentality_positioning"),
        "visao_jogo": vision,
        "criatividade": avg(vision, curve, ball_control),
        "agressividade": aggression,
        "lideranca": avg(reactions, composure, reputation_scaled),
        "regularidade": avg(reactions, composure, stamina),
        "agilidade": v("movement_agility"),
        "impulsao": v("power_jumping"),
        "forca": v("power_strength"),
        "velocidade": clamp_rating(gk_speed) if gk and gk_speed not in (None, 0) else v("movement_sprint_speed"),
        "aceleracao": v("movement_acceleration"),
        "resistencia": stamina,
    }


def normalize_row(row: dict[str, str]) -> dict[str, str | int]:
    attrs = derive_attributes(row)
    value = {
        "source_player_id": as_int(row, "player_id"),
        "short_name": clean(row["short_name"]),
        "full_name": clean(row["long_name"]),
        "source_age": as_int(row, "age"),
        "dob": clean(row["dob"]),
        "height_cm": as_int(row, "height_cm"),
        "weight_kg": as_int(row, "weight_kg"),
        "nationality": clean(row["nationality_name"]),
        "positions": clean(row["player_positions"]),
        "overall": clamp_rating(as_int(row, "overall")),
        "potential": clamp_rating(as_int(row, "potential")),
        "value_eur": as_int(row, "value_eur", required=False) or 0,
        "wage_eur": as_int(row, "wage_eur", required=False) or 0,
        "league_id": as_int(row, "league_id", required=False) or 0,
        "league_name": clean(row["league_name"]),
        "club_team_id": as_int(row, "club_team_id", required=False) or 0,
        "club_name": clean(row["club_name"]),
        "club_position": clean(row["club_position"]),
        "club_loaned_from": clean(row["club_loaned_from"]),
        "contract_until_year": as_int(row, "club_contract_valid_until_year", required=False) or 0,
        "preferred_foot": clean(row["preferred_foot"]),
        "weak_foot": as_int(row, "weak_foot", required=False) or 0,
        "skill_moves": as_int(row, "skill_moves", required=False) or 0,
        "international_reputation": as_int(row, "international_reputation", required=False) or 0,
        "work_rate": clean(row["work_rate"]),
        "release_clause_eur": as_int(row, "release_clause_eur", required=False) or 0,
        "summary_pace": as_int(row, "pace", required=False) or 0,
        "summary_shooting": as_int(row, "shooting", required=False) or 0,
        "summary_passing": as_int(row, "passing", required=False) or 0,
        "summary_dribbling": as_int(row, "dribbling", required=False) or 0,
        "summary_defending": as_int(row, "defending", required=False) or 0,
        "summary_physic": as_int(row, "physic", required=False) or 0,
    }
    value.update(attrs)
    return value


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("csv_path", type=Path)
    p.add_argument("--output", type=Path, required=True)
    p.add_argument("--manifest", type=Path, required=True)
    p.add_argument("--report", type=Path, required=True)
    args = p.parse_args()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.manifest.parent.mkdir(parents=True, exist_ok=True)
    args.report.parent.mkdir(parents=True, exist_ok=True)

    source_sha = hashlib.sha256(args.csv_path.read_bytes()).hexdigest()
    rows: list[dict[str, str | int]] = []
    source_ids: set[int] = set()
    duplicates: list[int] = []
    null_counts: Counter[str] = Counter()
    clubs: dict[int, str] = {}
    leagues: set[int] = set()
    nationalities: Counter[str] = Counter()
    loans = 0
    free_agents = 0

    with args.csv_path.open("r", encoding="utf-8-sig", newline="") as fh:
        reader = csv.DictReader(fh)
        fieldnames = set(reader.fieldnames or [])
        missing = sorted(REQUIRED_COLUMNS - fieldnames)
        if missing:
            raise SystemExit(f"dataset missing required columns: {missing}")
        for idx, raw in enumerate(reader, start=2):
            for key, val in raw.items():
                if clean(val) == "":
                    null_counts[key] += 1
            try:
                normalized = normalize_row(raw)
            except Exception as exc:
                raise SystemExit(f"row {idx}: {exc}") from exc
            pid = int(normalized["source_player_id"])
            if pid in source_ids:
                duplicates.append(pid)
            source_ids.add(pid)
            club_id = int(normalized["club_team_id"])
            if club_id:
                clubs.setdefault(club_id, str(normalized["club_name"]))
            else:
                free_agents += 1
            league_id = int(normalized["league_id"])
            if league_id:
                leagues.add(league_id)
            nationalities[str(normalized["nationality"])] += 1
            if normalized["club_loaned_from"]:
                loans += 1
            rows.append(normalized)

    if len(rows) != EXPECTED_ROWS:
        raise SystemExit(f"expected {EXPECTED_ROWS} players, found {len(rows)}")
    if duplicates:
        raise SystemExit(f"duplicate player_id values: {duplicates[:20]}")
    if len(source_ids) != len(rows):
        raise SystemExit("player_id uniqueness validation failed")

    for row in rows:
        if not (1 <= int(row["overall"]) <= 99):
            raise SystemExit(f"invalid overall: {row['source_player_id']}")
        if not (1 <= int(row["potential"]) <= 99):
            raise SystemExit(f"invalid potential: {row['source_player_id']}")
        for key in ATTRIBUTE_MAPPING:
            if not (1 <= int(row[key]) <= 99):
                raise SystemExit(f"invalid mapped attribute {key}: {row['source_player_id']}")

    rows.sort(key=lambda r: int(r["source_player_id"]))
    # mtime=0 keeps the checked-in/runtime asset byte-for-byte reproducible.
    with args.output.open("wb") as raw_output:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw_output, mtime=0) as gz_bytes:
            with io.TextIOWrapper(gz_bytes, encoding="utf-8", newline="") as gz:
                writer = csv.DictWriter(gz, fieldnames=OUTPUT_COLUMNS, dialect="excel-tab", lineterminator="\n")
                writer.writeheader()
                writer.writerows(rows)

    asset_sha = hashlib.sha256(args.output.read_bytes()).hexdigest()
    overalls = [int(r["overall"]) for r in rows]
    potentials = [int(r["potential"]) for r in rows]

    manifest = {
        "schemaVersion": 1,
        "datasetSource": DATASET_SOURCE,
        "datasetVersion": DATASET_VERSION,
        "sourceFile": args.csv_path.name,
        "sourceSha256": source_sha,
        "assetFile": args.output.name,
        "assetSha256": asset_sha,
        "playerCount": len(rows),
        "clubCount": len(clubs),
        "leagueCount": len(leagues),
        "nationalityCount": len(nationalities),
        "freeAgentCount": free_agents,
        "loanedPlayerCount": loans,
        "validationStatus": "VALIDATED",
        "money": {
            "sourceCurrency": "EUR",
            "gameCurrency": "BRL",
            "eurToBrl": EUR_TO_BRL_REFERENCE,
            "referenceDate": EUR_TO_BRL_REFERENCE_DATE,
            "referenceSource": EUR_TO_BRL_REFERENCE_SOURCE,
        },
    }
    args.manifest.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    report = {
        **manifest,
        "duplicatePlayerIds": duplicates,
        "overall": {"min": min(overalls), "max": max(overalls), "average": round(mean(overalls), 6)},
        "potential": {"min": min(potentials), "max": max(potentials), "average": round(mean(potentials), 6)},
        "playersWithoutPosition": sum(1 for r in rows if not str(r["positions"])),
        "playersWithoutNationality": sum(1 for r in rows if not str(r["nationality"])),
        "nullCounts": dict(sorted(null_counts.items())),
        "playersByNationality": dict(nationalities.most_common()),
        "datasetClubs": [{"clubTeamId": k, "clubName": clubs[k]} for k in sorted(clubs)],
        "attributeMapping": ATTRIBUTE_MAPPING,
        "notes": [
            "Club matching is intentionally performed against the actual Pro Football Team seed at new-save runtime.",
            "Unmatched/ambiguous target clubs use the existing procedural fallback; the runtime planner exposes those counts.",
            "No FC26 source player_id is used directly as the Room Player primary key.",
        ],
    }
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({
        "players": len(rows), "clubs": len(clubs), "leagues": len(leagues),
        "free_agents": free_agents, "loans": loans,
        "asset_bytes": args.output.stat().st_size, "asset_sha256": asset_sha,
    }, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
