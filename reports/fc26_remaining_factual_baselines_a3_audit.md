# Phase 9.11A3 — Remaining factual baselines audit

Base: `main@442856f1823cd3774ec1d20da8995881d5de0a9f`

FC26 snapshot: `2025-09-19`

Verified on: `2026-08-19`

This report is the pre-implementation factual gate for Phase 9.11A3. Text similarity is never accepted as identity evidence.

## Reproduced Phase 9.11A2 baseline

- Target teams: **2,524**
- MATCHED clubs: **385**
- UNMATCHED clubs: **275**
- AMBIGUOUS clubs: **2**
- Imported FC26 players: **10,852**
- Blocked FC26 players: **7,553**
- Legacy stable IDs changed: **0**
- Previously matched clubs lost: **0**
- Previously matched clubs redirected: **0**
- Room: **V21**

## Exclusive audit classification of the 277 remaining clubs

- `LOWER_TIER_FACTUAL_CLUB`: **112 clubs / 3,072 players**
- `FACTUAL_COUNTRY_BASELINE_MISSING`: **163 clubs / 4,428 players**
- `AMBIGUOUS`: **2 clubs / 53 players**

The 277-case classification was reproduced from the validated Phase 9.11A2 report. The Phase 9.11A3 acceptance test writes the complete post-A3 remaining-club detail to `reports/fc26_remaining_factual_baselines_a3_report.json`, which is uploaded by the dedicated CI workflow as an audit artifact.

## Phase 9.11A3 implementation decision

A deliberately small subset is accepted: **15 French clubs / 392 FC26 players** whose 2026/27 Ligue 2 BKT membership is explicitly present in the official LFP calendar published on 2026-06-10.

Primary factual source:
- LFP / Ligue1.com — `Calendrier 26/27 : Les dates à retenir en Ligue 2`
- https://ligue1.com/fr/articles/l1_article_3738-calendrier-26-27-les-dates-a-retenir-en-ligue-2

These clubs replace procedural France division-2 slots in-place. No target-team expansion is allowed.

| FC26 club_team_id | FC26 source name | Players | Canonical target | Division |
|---:|---|---:|---|---:|
| 115494 | FC Annecy | 29 | FC Annecy | 2 |
| 1815 | Clermont Foot 63 | 28 | Clermont Foot 63 | 2 |
| 111659 | Rodez Aveyron Football | 28 | Rodez AF | 2 |
| 379 | Stade de Reims | 28 | Stade de Reims | 2 |
| 111273 | Red Star FC | 27 | Red Star FC | 2 |
| 111376 | US Boulogne Cote d'Opale | 27 | US Boulogne CO | 2 |
| 1819 | AS Saint-Étienne | 26 | AS Saint-Étienne | 2 |
| 68 | FC Metz | 26 | FC Metz | 2 |
| 71 | FC Nantes | 26 | FC Nantes | 2 |
| 70 | Montpellier HSC | 26 | Montpellier HSC | 2 |
| 62 | En Avant Guingamp | 25 | En Avant Guingamp | 2 |
| 1823 | AS Nancy Lorraine | 24 | AS Nancy Lorraine | 2 |
| 110321 | Pau FC | 24 | Pau FC | 2 |
| 1814 | Stade Lavallois Mayenne FC | 24 | Stade Lavallois | 2 |
| 111276 | USL Dunkerque | 24 | USL Dunkerque | 2 |

Total: **15 clubs / 392 players**.

## Keep blocked

The other **97 lower-tier clubs / 2,680 players** remain blocked because Phase 9.11A3 does not pin an equally strong official 2026/27 competition field for them.

The **163 clubs / 4,428 players** without sufficient factual country baseline remain blocked.

The two ambiguous cases remain blocked:
- `110989 / Caracas FC` — 28 players.
- `111139 / CF Montréal` — 25 players.

## Expected acceptance transition

- MATCHED: **385 -> 400**
- UNMATCHED: **275 -> 260**
- AMBIGUOUS: **2 -> 2**
- Imported FC26 players: **10,852 -> 11,244**
- Blocked FC26 players: **7,553 -> 7,161**
- Newly resolved: **15 clubs / 392 players**
- Previously matched clubs lost: **0**
- Previously matched clubs redirected: **0**
- Legacy stable IDs changed: **0**
- Target-team count change: **0**
- Room migration: **none; V21 remains**
- FC26 overall/potential/attributes mutation: **none permitted**
