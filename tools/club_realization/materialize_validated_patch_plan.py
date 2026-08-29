#!/usr/bin/env python3
"""Materializa um plano JÁ VALIDADO de clubes reais e seus escudos.

Este utilitário deliberadamente não faz fuzzy matching nem descobre clubes por conta
própria. Ele aceita apenas um plano explícito 1:1 contra os 1.907 slots procedurais
congelados, valida PNG/SVG e copia os bytes originais sem redimensionar/reencodar.
Assim, escudos vindos do patch Brasfoot ou de uma fonte externa já auditada mantêm
seu formato original e sua proveniência fica registrada no manifesto final.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import re
import shutil
import struct
import unicodedata
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
EXPECTED_REPLACEMENTS = 1907
EXPECTED_FACTUAL_CLUBS = 617
FROZEN_GENERIC_CLUB_TOKENS = {"fc", "cf", "sc", "ac", "afc", "cd", "ca", "fk", "nk", "hnk", "gnk", "sk", "bk", "club", "clube"}
ALIAS_GENERIC_CLUB_TOKENS = FROZEN_GENERIC_CLUB_TOKENS | {"ad", "acd", "mfk", "csk", "bsk", "sa", "ccd", "csd", "fbc", "sd", "ud"}
ALIAS_CONNECTOR_TOKENS = {"de", "del", "da", "do", "dos", "das", "the"}
SUPPORTED_CREST_EXTENSIONS = {".png", ".svg"}


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
    canonical_club_key: str
    crest_file_name: str
    source_kind: str
    source_revision: str
    source_identity_path: str
    source_crest_path: str
    source_crest_sha256: str


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



def frozen_canonical_identity_key(value: str) -> str:
    """Canonicalização histórica usada para validar as chaves já congeladas."""
    folded = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode().casefold()
    return "".join(
        token for token in re.findall(r"[a-z0-9]+", folded)
        if token not in FROZEN_GENERIC_CLUB_TOKENS
    )


def canonical_identity_key(value: str) -> str:
    """Alias conservador forte para bloquear o mesmo clube escrito de formas diferentes."""
    folded = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode().casefold()
    folded = folded.replace("&", " and ")
    folded = re.sub(
        r"\b(?:[a-z]\.){2,}[a-z]?\.?",
        lambda match: match.group(0).replace(".", ""),
        folded,
    )
    tokens = re.findall(r"[a-z0-9]+", folded)
    normalized: list[str] = []
    for token in tokens:
        if token in ALIAS_GENERIC_CLUB_TOKENS or token in ALIAS_CONNECTOR_TOKENS:
            continue
        if re.fullmatch(r"(?:18|19|20)\d{2}", token):
            continue
        normalized.append(token)
    while normalized and normalized[-1] in {"1", "2", "ii", "b"}:
        normalized.pop()
    return "".join(normalized)


def looks_like_non_club_entity(value: str) -> bool:
    """Rejeita logos de competição/seleção sem bloquear nomes legítimos de clubes."""
    folded = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode().casefold()
    folded = " ".join(re.findall(r"[a-z0-9]+", folded))
    if "national team" in folded or "football association" in folded or "football federation" in folded:
        return True
    if folded.startswith(("copa ", "cup ")):
        return True
    if folded.startswith("liga ") and not folded.startswith("liga deportiva "):
        return True
    if folded.endswith((" liga", " league")):
        return True
    return False


def load_factual_keys(path: Path) -> set[tuple[str, str]]:
    with path.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    required = {"country", "clubName", "canonicalClubKey"}
    if rows:
        missing = required - set(rows[0])
        if missing:
            raise ValueError(f"Baseline factual sem colunas obrigatórias: {sorted(missing)}")
    if len(rows) != EXPECTED_FACTUAL_CLUBS:
        raise ValueError(f"Baseline factual deveria ter {EXPECTED_FACTUAL_CLUBS} clubes; recebeu {len(rows)}")

    result: set[tuple[str, str]] = set()
    for row in rows:
        country = row["country"].strip()
        club_name = row["clubName"].strip()
        canonical = row["canonicalClubKey"].strip().casefold()
        expected = frozen_canonical_identity_key(club_name)
        if not country or not club_name or not canonical:
            raise ValueError("Baseline factual contém país, nome ou chave canônica vazios")
        if canonical != expected:
            raise ValueError(
                f"Baseline factual possui canonicalClubKey inconsistente para {country} / {club_name}: "
                f"expected={expected!r} actual={canonical!r}"
            )
        # A baseline factual congela clubes, não chaves únicas. Se dois factuais
        # colapsarem sob a regra conservadora de alias, a chave continua proibida.
        result.add((country.casefold(), canonical))
        alias = canonical_identity_key(club_name)
        if not alias:
            raise ValueError(f"Baseline factual não produz alias utilizável: {country} / {club_name}")
        result.add((country.casefold(), alias))
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
        "canonicalClubKey",
        "crestFileName",
        "sourceKind",
        "sourceRevision",
        "sourceIdentityPath",
        "sourceCrestPath",
        "sourceCrestSha256",
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
            canonical_club_key=row["canonicalClubKey"].strip(),
            crest_file_name=row["crestFileName"].strip(),
            source_kind=row["sourceKind"].strip(),
            source_revision=row["sourceRevision"].strip(),
            source_identity_path=row["sourceIdentityPath"].strip(),
            source_crest_path=row["sourceCrestPath"].strip(),
            source_crest_sha256=row["sourceCrestSha256"].strip().lower(),
        )
        for row in rows
    ]


def validate_png(path: Path) -> str:
    data = path.read_bytes()
    if len(data) < 33 or not data.startswith(PNG_SIGNATURE):
        raise ValueError(f"PNG inválido: {path}")
    if data[12:16] != b"IHDR":
        raise ValueError(f"PNG sem IHDR inicial: {path}")
    width, height, _bit_depth, color_type = struct.unpack(">IIBB", data[16:26])
    if width <= 0 or height <= 0:
        raise ValueError(f"Dimensões inválidas: {path}")
    # 4=grayscale+alpha, 6=truecolor+alpha. Paletted PNG (3) may carry tRNS transparency.
    has_alpha = color_type in (4, 6) or (color_type == 3 and b"tRNS" in data)
    if not has_alpha:
        raise ValueError(f"Escudo PNG sem transparência preservável: {path}")
    return hashlib.sha256(data).hexdigest()


def validate_svg(path: Path) -> str:
    data = path.read_bytes()
    if not data.strip():
        raise ValueError(f"SVG vazio: {path}")
    folded = data.lower()
    for forbidden in (b"<!doctype", b"<!entity", b"<script", b"javascript:"):
        if forbidden in folded:
            raise ValueError(f"SVG contém conteúdo não permitido ({forbidden.decode(errors='ignore')}): {path}")
    try:
        root = ET.fromstring(data)
    except ET.ParseError as exc:
        raise ValueError(f"SVG XML inválido: {path}: {exc}") from exc
    local_name = root.tag.rsplit("}", 1)[-1].casefold()
    if local_name != "svg":
        raise ValueError(f"Arquivo .svg não tem raiz <svg>: {path}")
    has_geometry = bool(root.get("viewBox")) or bool(root.get("width") and root.get("height"))
    if not has_geometry:
        raise ValueError(f"SVG sem viewBox ou dimensões: {path}")
    return hashlib.sha256(data).hexdigest()


def validate_crest(path: Path) -> str:
    extension = path.suffix.casefold()
    if extension == ".png":
        return validate_png(path)
    if extension == ".svg":
        return validate_svg(path)
    raise ValueError(f"Formato de escudo não suportado: {path.name}")


def kotlin_string(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def validate_plan(
    slots: dict[int, Slot],
    plan: list[PlanRow],
    crests_dir: Path,
    factual_keys: set[tuple[str, str]],
) -> list[tuple[PlanRow, str]]:
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
    canonical_keys: set[tuple[str, str]] = set()
    alias_keys: set[tuple[str, str]] = set()
    crest_keys: set[str] = set()
    crest_digests: set[str] = set()
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
        if not row.canonical_club_key:
            raise ValueError(f"Chave canônica vazia no slot {row.legacy_team_id}")
        expected_canonical = frozen_canonical_identity_key(row.real_club_name)
        if row.canonical_club_key.casefold() != expected_canonical:
            raise ValueError(
                f"canonicalClubKey inconsistente no slot {row.legacy_team_id}: "
                f"expected={expected_canonical!r} actual={row.canonical_club_key!r}"
            )
        if looks_like_non_club_entity(row.real_club_name):
            raise ValueError(
                f"Identidade não representa clube masculino principal: {row.country} / {row.real_club_name}"
            )

        provenance_fields = {
            "sourceKind": row.source_kind,
            "sourceRevision": row.source_revision,
            "sourceIdentityPath": row.source_identity_path,
            "sourceCrestPath": row.source_crest_path,
        }
        for field, value in provenance_fields.items():
            if not value:
                raise ValueError(f"Proveniência {field} vazia no slot {row.legacy_team_id}")
        if not re.fullmatch(r"[0-9a-f]{64}", row.source_crest_sha256):
            raise ValueError(f"sourceCrestSha256 inválido no slot {row.legacy_team_id}")
        if row.source_crest_sha256 in crest_digests:
            raise ValueError(
                f"Bytes de escudo reutilizados por mais de um clube: sha256={row.source_crest_sha256}"
            )
        crest_digests.add(row.source_crest_sha256)

        if Path(row.crest_file_name).name != row.crest_file_name:
            raise ValueError(f"Nome de escudo deve ser basename puro: {row.crest_file_name}")
        if Path(row.crest_file_name).suffix.casefold() not in SUPPORTED_CREST_EXTENSIONS:
            raise ValueError(f"Escudo deve preservar PNG ou SVG original: {row.crest_file_name}")

        real_key = (row.country.casefold(), row.real_club_name.casefold())
        if real_key in real_keys:
            raise ValueError(f"Clube real repetido: {row.country} / {row.real_club_name}")
        real_keys.add(real_key)

        canonical_key = (row.country.casefold(), row.canonical_club_key.casefold())
        if canonical_key in canonical_keys:
            raise ValueError(
                f"Identidade canônica reutilizada por mais de um clube: "
                f"{row.country} / {row.canonical_club_key}"
            )
        canonical_keys.add(canonical_key)

        alias_name = canonical_identity_key(row.real_club_name)
        if not alias_name:
            raise ValueError(f"Nome real não produz identidade canônica utilizável no slot {row.legacy_team_id}")
        alias_key = (row.country.casefold(), alias_name)
        if alias_key in alias_keys:
            raise ValueError(
                f"Alias de clube reutilizado por mais de uma substituição: {row.country} / {row.real_club_name}"
            )
        alias_keys.add(alias_key)
        if canonical_key in factual_keys or alias_key in factual_keys:
            raise ValueError(
                f"Substituição reutiliza um dos {EXPECTED_FACTUAL_CLUBS} clubes factuais preservados: "
                f"{row.country} / {row.real_club_name}"
            )

        crest_key = row.crest_file_name.casefold()
        if crest_key in crest_keys:
            raise ValueError(f"Escudo reutilizado: {row.crest_file_name}")
        crest_keys.add(crest_key)

        crest_path = crests_dir / row.crest_file_name
        if not crest_path.is_file():
            raise FileNotFoundError(f"Escudo não encontrado: {crest_path}")
        digest = validate_crest(crest_path)
        if digest != row.source_crest_sha256:
            raise ValueError(
                f"Escudo local não corresponde ao SHA-256 auditado da fonte no slot {row.legacy_team_id}: "
                f"expected={row.source_crest_sha256} actual={digest}"
            )
        checked.append((row, digest))

    return checked


def write_kotlin(checked: list[tuple[PlanRow, str]], output: Path) -> None:
    lines = [
        "package com.example.data",
        "",
        "/** Gerado exclusivamente do plano validado de clubes reais. Não editar manualmente. */",
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
    for existing in target_dir.iterdir():
        if existing.is_file() and existing.suffix.casefold() in SUPPORTED_CREST_EXTENSIONS and existing.name not in expected_names:
            existing.unlink()
    for row, expected_digest in checked:
        src = source_dir / row.crest_file_name
        dst = target_dir / row.crest_file_name
        shutil.copyfile(src, dst)
        actual = hashlib.sha256(dst.read_bytes()).hexdigest()
        if actual != expected_digest:
            raise IOError(f"Cópia alterou bytes do escudo: {row.crest_file_name}")


def write_digest_manifest(checked: list[tuple[PlanRow, str]], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.writer(handle)
        writer.writerow([
            "legacyTeamId",
            "country",
            "division",
            "realClubName",
            "canonicalClubKey",
            "crestFileName",
            "sourceKind",
            "sourceRevision",
            "sourceIdentityPath",
            "sourceCrestPath",
            "sourceCrestSha256",
            "sha256",
        ])
        for row, digest in checked:
            writer.writerow([
                row.legacy_team_id,
                row.country,
                row.division,
                row.real_club_name,
                row.canonical_club_key,
                row.crest_file_name,
                row.source_kind,
                row.source_revision,
                row.source_identity_path,
                row.source_crest_path,
                row.source_crest_sha256,
                digest,
            ])


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, default=Path("docs/club-realization/generated-filler-slots.csv"))
    parser.add_argument(
        "--factual-baseline",
        type=Path,
        default=Path("docs/club-realization/preserved-factual-clubs.csv"),
    )
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
    factual_keys = load_factual_keys(args.factual_baseline)
    plan = load_plan(args.plan)
    checked = validate_plan(slots, plan, args.crests_dir, factual_keys)
    write_kotlin(checked, args.kotlin_output)
    copy_original_crests(checked, args.crests_dir, args.asset_output_dir)
    write_digest_manifest(checked, args.digest_output)
    print(f"validated_replacements={len(checked)}")
    print(f"bundled_crests={len(checked)}")


if __name__ == "__main__":
    main()
