#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[2]
path = root / "app/src/main/java/com/example/ui/screens/TransfersScreen.kt"
text = path.read_text(encoding="utf-8")
old = """            val candidates = allPlayers.filter { player ->\n                player.id !in requestedKey.locallyPurchasedIds &&\n                    player.isTransferMarketCandidateFor(requestedKey.playerTeamId)\n            }\n"""
new = """            val candidates = allPlayers.filter { player ->\n                // Preserve the immediate local-removal invariant before Room/Flow reconciliation.\n                // The LaunchedEffect is keyed by locallyPurchasedIds, so this captured set belongs\n                // to the same requested search generation represented by requestedKey.\n                player.id !in locallyPurchasedIds &&\n                    player.isTransferMarketCandidateFor(requestedKey.playerTeamId)\n            }\n"""
if text.count(old) != 1:
    raise SystemExit(f"expected one generated candidate block, found {text.count(old)}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
print("immediate market removal contract preserved")
