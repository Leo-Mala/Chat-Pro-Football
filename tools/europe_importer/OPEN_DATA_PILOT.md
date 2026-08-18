# Premier League 2026/27 Open-Data Pilot

This pilot avoids paid football-data subscriptions.

## Sources

- English Wikipedia club pages are used only to locate the current/first-team squad section and discover linked player pages.
- Wikidata provides the structured player facts consumed by the collector. Wikidata main/property/lexeme structured data is CC0.
- No Wikipedia article text, images, logos, provider ratings, external IDs, or external numeric identities are written into the canonical game dataset.

## Collection contract

The collector resolves each linked squad player to Wikidata and requires:

- current club membership (`P54`) matching the club;
- full date of birth (`P569`);
- nationality (`P27`);
- playing position (`P413`);
- shirt/sport number (`P1618`) when available.

`P1642` is inspected for `loan` acquisition. Loan candidates are reported but are not automatically materialized until both endpoints can be resolved safely through the game's stable global identity system.

## Fail-closed behavior

The collector writes `open_data_audit.json` before canonical validation. Missing factual fields are reported rather than invented. The production gate requires every club to retain at least 18 accepted factual players and the existing canonical gameplay-ready position invariants.

The CI artifact contains only the canonical shards plus the audit/summary. HTTP cache under `tools/europe_importer/.cache/` remains untracked.

## Run

```bash
python3 -m tools.europe_importer.real_pilot
```

No API key is required.
