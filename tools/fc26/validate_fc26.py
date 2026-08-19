#!/usr/bin/env python3
from __future__ import annotations

import argparse
import csv
import gzip
import hashlib
import json
from pathlib import Path

REQUIRED_MAPPED = {
    "source_player_id", "full_name", "dob", "nationality", "positions", "overall", "potential",
    "reflexos", "pegada", "um_contra_um", "saida_de_gol", "lancamento", "desarme", "marcacao",
    "cabeceio", "passe_curto", "cruzamento", "drible", "passe", "primeiro_toque", "finalizacao",
    "chute_de_longe", "controle_bola", "posicionamento", "concentracao", "sangue_frio",
    "antecipacao", "bravura", "trabalho_equipe", "decisao", "sem_bola", "visao_jogo",
    "criatividade", "agressividade", "lideranca", "regularidade", "agilidade", "impulsao", "forca",
    "velocidade", "aceleracao", "resistencia",
}


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("asset", type=Path)
    p.add_argument("manifest", type=Path)
    args = p.parse_args()
    manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
    actual_sha = hashlib.sha256(args.asset.read_bytes()).hexdigest()
    if actual_sha != manifest["assetSha256"]:
        raise SystemExit("asset sha256 does not match manifest")
    ids: set[int] = set()
    count = 0
    with gzip.open(args.asset, "rt", encoding="utf-8", newline="") as fh:
        reader = csv.DictReader(fh, dialect="excel-tab")
        missing = REQUIRED_MAPPED - set(reader.fieldnames or [])
        if missing:
            raise SystemExit(f"missing normalized fields: {sorted(missing)}")
        for row in reader:
            count += 1
            pid = int(row["source_player_id"])
            if pid in ids:
                raise SystemExit(f"duplicate source_player_id {pid}")
            ids.add(pid)
            for key in {"overall", "potential"} | (REQUIRED_MAPPED - {"source_player_id", "full_name", "dob", "nationality", "positions"}):
                value = int(row[key])
                if not 1 <= value <= 99:
                    raise SystemExit(f"{key} outside 1..99 for player {pid}: {value}")
    if count != manifest["playerCount"]:
        raise SystemExit(f"player count mismatch: {count} != {manifest['playerCount']}")
    print(json.dumps({"validatedPlayers": count, "sha256": actual_sha, "status": "VALIDATED"}, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
