#!/usr/bin/env python3
"""Materializa um plano JÁ VALIDADO de clubes reais/escudos do patch Brasfoot.

Este utilitário deliberadamente não faz fuzzy matching nem consulta fontes externas.
Ele aceita apenas um plano explícito 1:1 contra os 1.907 slots procedurais congelados,
valida PNGs e copia os bytes originais sem redimensionar/reencodar.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import shutil
import struct
from dataclasses import dataclass
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
EXPECTED_REPLACEMENTS = 1907


@dataclass(frozen=True)
class Slot:
    legacy_team_id: int
    country: str
    division: int
    legacy_slot_name: str


@dataclass(frozen=True)
class PlanRow:
    legacy_team_id: int
    country: str
    division: int
    legacy_slot_name: str
    real_club_name: str
    crest_file_name: str


def load_slots(path: Path) -> dict[int, Slot]:
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    result: dict[int, Slot] = {}
    for row in rows:
        slot = Slot(
            legacy_team_id=int(row["legacyTeamId"]),
            country=row["country"].strip(),
            division=int(row["division"]),
            legacy_slot_name=row["legacySlotName"].strip(),
        )
        if slot.legacy_team_id in result:
            raise ValueError(f"ID de slot duplicado: {slot.legacy_team_id}")
        result[slot.legacy_team_id] = slot
    if len(result) != EXPECTED_REPLACEMENTS:
        raise ValueError(f"Baseline deveria ter {EXPECTED_REPLACEMENTS} slots; recebeu {len(result)}")
    return result


def load_plan(path: Path) -> list[PlanRow]:
    with path.open(encoding="utf-8", newline="") as handle:
        rows = list(csv.DictReader(handle))
    required = {
        "legacyTeamId",
        "country",
        "division",
        "legacySlotName",
        "realClubName",
        "crestFileName",
    }
    if rows:
        missing = required - set(rows[0])
        if missing:
            raise ValueError(f"Plano sem colunas obrigatórias: {sorted(missing)}")
    return [
        PlanRow(
            legacy_team_id=int(row["legacyTeamId"]),
            country=row["country"].strip(),
            division=int(row["division"]),
            legacy_slot_name=row["legacySlotName"].strip(),
            real_club_name=row["realClubName"].strip(),
            crest_file_name=row["crestFileName"].strip(),
        )
        for row in rows
    ]


def validate_png(path: Path) -> tuple[int, int, int, str]:
    data = path.read_bytes()
    if len(data) < 33 or not data.startswith(PNG_SIGNATURE):
        raise ValueError(f"PNG inválido: {path}")
    if data[12:16] != b"IHDR":
        raise ValueError(f"PNG sem IHDR inicial: {path}")
    width, height, bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    if width <= 0 or height <= 0:
        raise ValueError(f"Dimensões inválidas: {path}")
    # 4=grayscale+alpha, 6=truecolor+alpha. Paletted PNG (3) may carry tRNS transparency.
    has_alpha = color_type in (4, 6) or (color_type == 3 and b"tRNS" in data)
    if not has_alpha:
        raise ValueError(f"Escudo sem transparência preservável: {path}")
    digest = hashlib.sha256(data).hexdigest()
    return width, height, color_type, digest


def kotlin_string(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def validate_plan(slots: dict[int, Slot], plan: list[PlanRow], crests_dir: Path) -> list[tuple[PlanRow, str]]:
    if len(plan) != EXPECTED_REPLACEMENTS:
        raise ValueError(f"Plano deve conter exatamente {EXPECTED_REPLACEMENTS} linhas; recebeu {len(plan)}")

    ids = [row.legacy_team_id for row in plan]
    if len(set(ids)) != len(ids):
        raise ValueError("Plano reutiliza legacyTeamId")
    if set(ids) != set(slots):
        missing = sorted(set(slots) - set(ids))[:20]
        extra = sorted(set(ids) - set(slots))[:20]
        raise ValueError(f"Plano não cobre exatamente o baseline. missing={missing} extra={extra}")

    real_keys: set[tuple[str, str]] = set()
    crest_keys: set[str] = set()
    checked: list[tuple[PlanRow, str]] = []

    for row in plan:
        slot = slots[row.legacy_team_id]
        if (row.country, row.division, row.legacy_slot_name) != (
            slot.country,
            slot.division,
            slot.legacy_slot_name,
        ):
            raise ValueError(
                f"Slot alterado no plano para ID {row.legacy_team_id}: "
                f"baseline={(slot.country, slot.division, slot.legacy_slot_name)!r}, "
                f"plan={(row.country, row.division, row.legacy_slot_name)!r}"
            )
        if not row.real_club_name:
            raise ValueError(f"Clube real vazio no slot {row.legacy_team_id}")
        if not row.crest_file_name.lower().endswith(".png"):
            raise ValueError(f"Escudo não é PNG: {row.crest_file_name}")
        if Path(row.crest_file_name).name != row.crest_file_name:
            raise ValueError(f"Nome de escudo deve ser basename puro: {row.crest_file_name}")

        real_key = (row.country.casefold(), row.real_club_name.casefold())
        if real_key in real_keys:
            raise ValueError(f"Clube real repetido: {row.country} / {row.real_club_name}")
        real_keys.add(real_key)

        crest_key = row.crest_file_name.casefold()
        if crest_key in crest_keys:
            raise ValueError(f"PNG reutilizado: {row.crest_file_name}")
        crest_keys.add(crest_key)

        crest_path = crests_dir / row.crest_file_name
        if not crest_path.is_file():
            raise FileNotFoundError(f"PNG não encontrado: {crest_path}")
        _, _, _, digest = validate_png(crest_path)
        checked.append((row, digest))

    return checked


def write_kotlin(checked: list[tuple[PlanRow, str]], output: Path) -> None:
    lines = [
        "package com.example.data",
        "",
        "/** Gerado exclusivamente do plano validado do patch Brasfoot. Não editar manualmente. */",
        "object BrasfootRealClubReplacementData {",
        f"    const val EXPECTED_REPLACEMENT_COUNT: Int = {EXPECTED_REPLACEMENTS}",
        "",
        "    val replacements: List<BrasfootRealClubIdentity.Replacement> = listOf(",
    ]
    for row, _ in checked:
        lines.append(
            "        BrasfootRealClubIdentity.Replacement(" +
            f"legacyTeamId = {row.legacy_team_id}L, country = {kotlin_string(row.country)}, division = {row.division}, " +
            f"legacySlotName = {kotlin_string(row.legacy_slot_name)}, " +
            f"realClubName = {kotlin_string(row.real_club_name)}, " +
            f"crestFileName = {kotlin_string(row.crest_file_name)}),"
        )
    lines += [
        "    )",
        "",
        "    val crests: List<BrasfootPatchCrests.Entry> = replacements.map { replacement ->",
        "        BrasfootPatchCrests.Entry(replacement.country, replacement.realClubName, replacement.crestFileName)",
        "    }",
        "}",
        "",
    ]
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")


def copy_original_crests(checked: list[tuple[PlanRow, str]], source_dir: Path, target_dir: Path) -> None:
    target_dir.mkdir(parents=True, exist_ok=True)
    expected_names = {row.crest_file_name for row, _ in checked}
    for existing in target_dir.glob("*.png"):
        if existing.name not in expected_names:
            existing.unlink()
    for row, expected_digest in checked:
        src = source_dir / row.crest_file_name
        dst = target_dir / row.crest_file_name
        shutil.copyfile(src, dst)
        actual = hashlib.sha256(dst.read_bytes()).hexdigest()
        if actual != expected_digest:
            raise IOError(f"Cópia alterou bytes do PNG: {row.crest_file_name}")


def write_digest_manifest(checked: list[tuple[PlanRow, str]], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow(["legacyTeamId", "country", "division", "realClubName", "crestFileName", "sha256"])
        for row, digest in checked:
            writer.writerow([
                row.legacy_team_id,
                row.country,
                row.division,
                row.real_club_name,
                row.crest_file_name,
                digest,
            ])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, default=Path("docs/club-realization/generated-filler-slots.csv"))
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--crests-dir", type=Path, required=True)
    parser.add_argument(
        "--kotlin-output",
        type=Path,
        default=Path("app/src/main/java/com/example/data/BrasfootRealClubReplacementData.kt"),
    )
    parser.add_argument(
        "--asset-output-dir",
        type=Path,
        default=Path("app/src/main/assets/club_crests"),
    )
    parser.add_argument(
        "--digest-output",
        type=Path,
        default=Path("docs/club-realization/crest-sha256.csv"),
    )
    args = parser.parse_args()

    slots = load_slots(args.baseline)
    plan = load_plan(args.plan)
    checked = validate_plan(slots, plan, args.crests_dir)
    write_kotlin(checked, args.kotlin_output)
    copy_original_crests(checked, args.crests_dir, args.asset_output_dir)
    write_digest_manifest(checked, args.digest_output)
    print(f"validated_replacements={len(checked)}")
    print(f"bundled_crests={len(checked)}")


if __name__ == "__main__":
    main()
