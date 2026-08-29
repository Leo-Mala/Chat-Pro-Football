#!/usr/bin/env python3
"""Audita a capacidade do catálogo de clubes extraído do patch Brasfoot.

Os quatro blocos binários foram preservados a partir do processamento read-only do RAR
fornecido pelo proprietário do repositório. Este utilitário não faz fuzzy matching, não
consulta fontes externas e não materializa nenhum clube no runtime. Ele apenas:

1. encontra de forma fail-closed a única ordem dos blocos que forma um gzip válido;
2. valida o CSV reconstruído;
3. compara a oferta bruta de candidatos por país com os fillers congelados;
4. emite um relatório legível para o GitHub Actions.

A oferta é deliberadamente BRUTA: duplicidades contra clubes factuais já existentes no
Pro Football são tratadas na etapa posterior de seleção/materialização, nunca aqui.
"""

from __future__ import annotations

import csv
import gzip
import hashlib
import itertools
import re
from collections import Counter
from io import StringIO
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE_DIR = ROOT / "docs" / "club-realization" / "patch-candidate-source"
BASELINE = ROOT / "docs" / "club-realization" / "generated-filler-slots.csv"
EXPECTED_FILLERS = 1907
EXPECTED_CANDIDATE_ROWS = 4182

CHUNKS = (
    SOURCE_DIR / "chunk-301c950af1c901c1.bin",
    SOURCE_DIR / "chunk-4aaeee499b48252d.bin",
    SOURCE_DIR / "chunk-2b51f78aee7d4408.bin",
    SOURCE_DIR / "chunk-7ffda5d3a6c21205.bin",
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def normalize_header(value: str) -> str:
    return re.sub(r"[^a-z0-9]", "", value.casefold())


def reconstruct_payload() -> tuple[bytes, tuple[Path, ...], bytes]:
    missing = [str(path.relative_to(ROOT)) for path in CHUNKS if not path.is_file()]
    if missing:
        raise FileNotFoundError(f"Blocos do catálogo ausentes: {missing}")

    contents = {path: path.read_bytes() for path in CHUNKS}
    if any(len(data) != 14_000 for data in contents.values()):
        sizes = {path.name: len(data) for path, data in contents.items()}
        raise ValueError(f"Tamanho inesperado nos blocos preservados: {sizes}")

    valid: list[tuple[bytes, tuple[Path, ...], bytes]] = []
    for order in itertools.permutations(CHUNKS):
        compressed = b"".join(contents[path] for path in order)
        if not compressed.startswith(b"\x1f\x8b"):
            continue
        try:
            payload = gzip.decompress(compressed)
        except (OSError, EOFError):
            continue
        valid.append((payload, order, compressed))

    if len(valid) != 1:
        orders = [[path.name for path in entry[1]] for entry in valid]
        raise ValueError(
            "Reconstrução fail-closed: esperado exatamente um gzip válido; "
            f"encontrados={len(valid)} orders={orders}"
        )
    return valid[0]


def parse_catalog(payload: bytes) -> tuple[list[dict[str, str]], list[str], str]:
    try:
        text = payload.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise ValueError("Catálogo descompactado não é UTF-8") from exc

    sample = text[:8192]
    try:
        dialect = csv.Sniffer().sniff(sample, delimiters=",;\t")
    except csv.Error:
        dialect = csv.excel

    reader = csv.DictReader(StringIO(text), dialect=dialect)
    headers = [header.strip() for header in (reader.fieldnames or []) if header is not None]
    if not headers:
        raise ValueError("Catálogo reconstruído não possui cabeçalho CSV")

    aliases = {
        "country",
        "pais",
        "patchcountry",
        "profootballcountry",
        "targetcountry",
        "mappedcountry",
    }
    country_field = next((header for header in headers if normalize_header(header) in aliases), None)
    if country_field is None:
        raise ValueError(f"Não foi possível identificar a coluna de país. headers={headers}")

    rows = []
    for row in reader:
        clean = {str(key).strip(): (value or "").strip() for key, value in row.items() if key is not None}
        if any(clean.values()):
            rows.append(clean)
    if not rows:
        raise ValueError("Catálogo reconstruído não possui linhas de clubes")
    return rows, headers, country_field


def load_filler_demand() -> Counter[str]:
    with BASELINE.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    if len(rows) != EXPECTED_FILLERS:
        raise ValueError(
            f"Baseline de fillers deveria conter {EXPECTED_FILLERS} linhas; recebeu {len(rows)}"
        )
    demand: Counter[str] = Counter()
    for row in rows:
        country = (row.get("country") or "").strip()
        if not country:
            raise ValueError("Baseline contém filler sem país")
        demand[country] += 1
    return demand


def main() -> None:
    payload, order, compressed = reconstruct_payload()
    rows, headers, country_field = parse_catalog(payload)
    if len(rows) != EXPECTED_CANDIDATE_ROWS:
        raise ValueError(
            f"Catálogo deveria conter {EXPECTED_CANDIDATE_ROWS} candidatos; recebeu {len(rows)}"
        )

    candidate_counts = Counter(row[country_field] for row in rows if row[country_field])
    demand = load_filler_demand()

    print("PATCH_CANDIDATE_AUDIT=PASS")
    print(f"compressed_bytes={len(compressed)}")
    print(f"compressed_sha256={sha256(compressed)}")
    print(f"payload_bytes={len(payload)}")
    print(f"payload_sha256={sha256(payload)}")
    print(f"candidate_rows={len(rows)}")
    print(f"csv_headers={headers}")
    print("chunk_order=" + ",".join(path.name for path in order))
    print(f"filler_rows={sum(demand.values())}")

    deficits: list[tuple[str, int, int, int]] = []
    print("COUNTRY_CAPACITY_BEGIN")
    for country in sorted(demand):
        fillers = demand[country]
        candidates = candidate_counts.get(country, 0)
        delta = candidates - fillers
        print(f"CAPACITY|{country}|fillers={fillers}|candidates={candidates}|delta={delta}")
        if delta < 0:
            deficits.append((country, fillers, candidates, delta))
    print("COUNTRY_CAPACITY_END")

    extra_countries = sorted(set(candidate_counts) - set(demand))
    if extra_countries:
        print("candidate_countries_without_fillers=" + ",".join(extra_countries))

    print(f"raw_deficit_country_count={len(deficits)}")
    for country, fillers, candidates, delta in deficits:
        print(
            f"RAW_DEFICIT|{country}|fillers={fillers}|candidates={candidates}|missing={-delta}"
        )


if __name__ == "__main__":
    main()
