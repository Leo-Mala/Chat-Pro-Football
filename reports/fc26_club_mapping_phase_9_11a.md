# Phase 9.11A — FC26 Club Mapping Expansion

Base: `main@80a372f820e81115255b7951f96125c38e0ea0cb`

## Safety contract

- FC26 remains the factual player source.
- `club_team_id` is never assumed to equal Pro Football `Team.id`.
- Automatic matches are limited to audited source-ID mappings, materialized stable identities, and the pre-existing conservative exact/core rules.
- Similarity scores are report-only and can never create a `MATCHED` result.
- Missing stable targets remain unresolved instead of being mapped to procedural city clubs.
- Explicit legacy templates without stable IDs are resolved by audited source ID + country + canonical template name, not by list position.
- No FC26 overall, potential, attributes, player identity or Room schema is changed by this phase.

## Before → audited expansion

| Metric | Before | After | Delta |
|---|---:|---:|---:|
| FC26 players | 18,405 | 18,405 | 0 |
| FC26 clubs | 662 | 662 | 0 |
| Target teams | 2,544 | 2,544 | 0 |
| Matched clubs | 139 | 160 | **+21** |
| Unmatched clubs | 511 | 492 | **-19** |
| Ambiguous clubs | 12 | 10 | **-2** |
| FC26 players imported | 3,979 | 4,583 | **+604** |
| FC26 players skipped | 14,426 | 13,822 | **-604** |
| Players with mapped club | 3,890 | 4,494 | **+604** |
| Fallback rosters required | 2,405 | 2,384 | **-21** |
| Skipped by unmatched club | 14,095 | 13,546 | -549 |
| Skipped by ambiguous club | 331 | 276 | -55 |

The 21 promoted clubs are the only club match states intentionally changed by this phase. The second diagnostic checkpoint verified that the additional 14 mappings changed only their own source clubs from `UNMATCHED` to `MATCHED`; no previously matched target was redirected.

## Audited source-ID mappings

### Stable identities

- `9` Liverpool → `3 / Liverpool FC` (28 players)
- `448` Athletic Club → `206 / Athletic Club` (27)
- `449` Real Betis Balompié → `207 / Real Betis` (32)
- `450` RC Celta → `212 / Celta de Vigo` (28)
- `452` RCD Espanyol → `221 / RCD Espanyol de Barcelona` (25)
- `459` Real Sporting de Gijón → `226 / Sporting de Gijón` (30)
- `242` RC Deportivo de La Coruña → `243 / RC Deportivo` (25)

### Existing explicit TeamTemplates

These mappings are pinned by FC26 source ID + country + canonical template name, so a future list reorder does not silently remap the identity.

- `100852` CD Castellón → Espanha / Castellón (28)
- `569` Vasco da Gama → Brasil / Vasco (20)
- `1035` Atlético Mineiro → Brasil / Atlético-MG (20)
- `101084` Gimnasia y Esgrima La Plata → Argentina / Gimnasia LP (37)
- `112965` Central Cordoba SdE → Argentina / Central Córdoba (33)
- `111020` Independiente Rivadavia → Argentina / Independiente Riv. (33)
- `111022` Belgrano de Córdoba → Argentina / Belgrano (32)
- `112713` Club Atlético Sarmiento → Argentina / Sarmiento Junín (32)
- `110404` CA Banfield → Argentina / Banfield (31)
- `101083` Estudiantes de La Plata → Argentina / Estudiantes LP (30)
- `110953` Instituto Atlético Central Córdoba → Argentina / Instituto ACC (30)
- `1013` San Lorenzo de Almagro → Argentina / San Lorenzo (29)
- `111716` Club Atlético Unión → Argentina / Unión Santa Fe (28)
- `112670` Talleres → Argentina / Talleres Córdoba (26)

## Materialization diagnosis

After the 21 safe mappings, 502 source clubs remain unresolved (`492 UNMATCHED + 10 AMBIGUOUS`). The important split is structural:

- `STABLE_TARGET_MISSING`: **130 clubs / 3,597 FC26 players**
- `NO_STABLE_IDENTITY`: **326 clubs**
- `UNKNOWN_COUNTRY_CONTEXT`: **46 clubs**
- unresolved `STABLE_TARGET_PRESENT`: **0**

This proves that the next large coverage gain cannot safely come from fuzzy aliases. For 130 factual clubs (for example Ajax, Juventus, Olympique de Marseille, Benfica and many other verified European clubs), Pro Football already reserves a stable factual identity but the corresponding `Team` is not physically materialized in the current `DefaultData` universe. Those clubs must be materialized factually; they must not be redirected to procedural city-name lookalikes.

The 130 missing stable targets block 3,597 FC26 players. By country, the largest blocked groups are Germany (494 players), Poland (407), France (381), Italy (301), Switzerland (274), Sweden (272), Netherlands (261), Belgium (260), Austria (227), Turkey (202) and Portugal (197).

## Diagnostic reports

The full reports are generated deterministically by `Fc26ClubCandidateReportTest` and uploaded as the `fc26-club-mapping-diagnostics` GitHub Actions artifact. They remain CI artifacts instead of adding more than 1 MB of generated review data to source control.

Audited run #3 hashes:

- `fc26_club_mapping_report.json` — SHA-256 `ec95d83810464176735a3c2151dccbcaf810bf93e3b182108182c6d0d64efbb3`
- `fc26_unmatched_candidates.json` — SHA-256 `e5a50c4ba60198a7ea1a52762874e788504d0173b070a3cc2c9538774a2aa2d0`
- `fc26_missing_target_clubs.json` — SHA-256 `14a91db20c7555c32a6c930ca06da2e8fa35214cf4d0e7b7fa4e452b69f3c59e`

Candidate similarity is diagnostic only. A score is never sufficient to promote a club to `MATCHED`.

## Next architectural boundary

Phase 9.11A should stop at safe identity reconciliation. The next major coverage step should be a separate factual target-materialization checkpoint that replaces procedural placeholders for verified clubs while preserving `StableTeamIdentityRegistry` IDs. It should not be mixed with fuzzy club matching.
