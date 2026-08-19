"""Conservative, deterministic player-name normalization for FC26/PlayersDB reconciliation."""
from __future__ import annotations

import re
import unicodedata
from typing import Iterable

_SPECIAL_TRANSLITERATION = str.maketrans({
    "ß": "ss",
    "ø": "o",
    "đ": "d",
    "ð": "d",
    "þ": "th",
    "ł": "l",
})

# Narrow, audited romanization equivalences used only by the A3 token-signature
# rule. They bridge two recurring Korean romanization variants observed in the
# supplied snapshots; they are deliberately not a general fuzzy-name system.
_TOKEN_EQUIVALENCE = {
    "seong": "sung",
    "jeong": "jung",
}


def normalize_name(value: object) -> str:
    """Return a stable ASCII-ish comparison form without fuzzy rewriting."""
    if value is None:
        return ""
    text = str(value).strip().lower().translate(_SPECIAL_TRANSLITERATION)
    text = unicodedata.normalize("NFKD", text)
    text = "".join(ch for ch in text if not unicodedata.combining(ch))
    # Non-Latin text appended to a Latin FC name is intentionally ignored for
    # matching; punctuation, apostrophes and hyphens become token boundaries.
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return " ".join(text.split())


def normalized_tokens(value: object) -> tuple[str, ...]:
    return tuple(normalize_name(value).split())


def token_signature(value: object) -> frozenset[str]:
    return frozenset(_TOKEN_EQUIVALENCE.get(token, token) for token in normalized_tokens(value))


def is_robust_name(value: object) -> bool:
    """A robust identity name contains at least two normalized tokens."""
    return len(normalized_tokens(value)) >= 2


def normalized_aliases(values: Iterable[object]) -> set[str]:
    return {name for value in values if (name := normalize_name(value))}
