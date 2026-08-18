# Premier League 2026/27 Open-Data Pilot

This pilot avoids paid football-data subscriptions.

## Sources

- English Wikipedia club pages are used only to locate each club's current/first-team squad section and discover linked player pages.
- Wikidata provides the structured player facts consumed by the collector. Wikidata structured data is CC0.
- Official Premier League/club pages are used only for explicit, reviewable overrides when a required factual field is incomplete in Wikidata.
- No Wikipedia article text, images, logos, provider ratings, external IDs, or external numeric identities are written into the canonical game dataset.

## Collection contract

The current squad section is the primary source for present club membership. Wikidata `P54` is used as an additional cross-check rather than a hard gate because current-club statements can lag recent transfers.

For each accepted player the pilot requires:

- full date of birth (`P569`);
- football nationality from country for sport (`P1532`) when available, falling back to citizenship (`P27`) only when `P1532` is absent;
- playing position (`P413`) or an explicitly sourced position override;
- shirt/sport number only when it is attached to the current club membership; historical top-level numbers are ignored.

`P1642` is inspected for loan candidates. It is not sufficient by itself to materialize a loan because historical loan acquisition statements can remain present. A loan enters the canonical dataset only when it also has an explicit verified override with owner, borrower, verification date and official source, and both clubs resolve through the game's stable identity contract.

## Current validated snapshot

Verified as of 2026-08-18:

- 20 Premier League clubs;
- 486 stable player identities in total;
- 485 active squad players plus 1 verified loan identity;
- 1 materialized loan: Alejandro Garnacho, Chelsea FC -> Aston Villa;
- 483 player records use Wikidata `P1532` for football nationality and 3 use the `P27` fallback;
- 20 club shards;
- `validationStatus=VALIDATED`;
- zero canonical `provider://api-football` provenance references.

These figures describe the source snapshot and structural validation, not a promise that collaborative open data will remain unchanged after the verification date.

## Fail-closed behavior

The collector writes `open_data_audit.json` before canonical validation. Missing factual fields are reported rather than invented. The production gate requires every club to retain at least 18 accepted factual players and the existing canonical gameplay-ready position invariants.

The CI artifact contains only the canonical shards plus the audit/summary. HTTP cache under `tools/europe_importer/.cache/` remains untracked.

## Run

```bash
python3 -m tools.europe_importer.real_pilot
```

No API key is required.
