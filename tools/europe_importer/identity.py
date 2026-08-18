from __future__ import annotations
import json
import unicodedata
from pathlib import Path

REAL_PLAYER_ID_FLOOR = 100_000_000_000_000
REAL_PLAYER_ID_SPAN = 8_000_000_000_000_000
MASK_64 = (1 << 64) - 1
MASK_POSITIVE_LONG = (1 << 63) - 1
IDENTITY_NAME_PREFIX = "identity-name-v1:"

_TRANSLITERATION = str.maketrans({
    "Ø":"O","ø":"o","Ł":"L","ł":"l","Đ":"D","đ":"d","Ð":"D","ð":"d",
    "Þ":"Th","þ":"th","Æ":"Ae","æ":"ae","Œ":"Oe","œ":"oe","ß":"ss","ı":"i",
})

def normalize_identity_text(value: str) -> str:
    transliterated = value.strip().translate(_TRANSLITERATION)
    decomposed = unicodedata.normalize("NFKD", transliterated)
    no_marks = "".join(ch for ch in decomposed if not unicodedata.combining(ch)).lower()
    cleaned = "".join(ch if ("a" <= ch <= "z" or "0" <= ch <= "9") else " " for ch in no_marks)
    return " ".join(cleaned.split())

def _fnv_like_long(value: str) -> int:
    h = 1469598103934665603
    for ch in value:
        h = ((h ^ ord(ch)) * 1099511628211) & MASK_64
    return h & MASK_POSITIVE_LONG

def _identity_components(full_name: str, disambiguator: str) -> tuple[str, str]:
    if disambiguator.startswith(IDENTITY_NAME_PREFIX):
        identity_name = disambiguator[len(IDENTITY_NAME_PREFIX):].strip()
        if not identity_name:
            raise ValueError("identity-name-v1 alias requires the previous canonical name")
        return identity_name, ""
    return full_name, disambiguator

def stable_player_id(full_name: str, birth_date_iso: str, disambiguator: str = "") -> int:
    identity_name, effective_disambiguator = _identity_components(full_name, disambiguator)
    canonical = "|".join([
        normalize_identity_text(identity_name),
        birth_date_iso,
        normalize_identity_text(effective_disambiguator),
    ])
    return REAL_PLAYER_ID_FLOOR + (_fnv_like_long(canonical) % REAL_PLAYER_ID_SPAN)

class StableTeamIdentityContract:
    def __init__(self, mapping: dict[tuple[str, str], int]):
        self._mapping = mapping
        ids = list(mapping.values())
        if len(ids) != len(set(ids)):
            raise ValueError("stable team identity contract contains duplicate teamId")

    @classmethod
    def from_json(cls, path: Path) -> "StableTeamIdentityContract":
        doc = json.loads(path.read_text(encoding="utf-8"))
        country = doc["country"]
        return cls({(country.casefold(), item["name"].casefold()): int(item["teamId"]) for item in doc["teams"]})

    def id_for(self, country: str, club_name: str) -> int:
        key = (country.strip().casefold(), club_name.strip().casefold())
        if key not in self._mapping:
            raise ValueError(f"club is not mapped by StableTeamIdentityRegistry contract: {country}/{club_name}")
        return self._mapping[key]
