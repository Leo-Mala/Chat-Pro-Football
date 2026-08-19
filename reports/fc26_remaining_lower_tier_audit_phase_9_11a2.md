# Phase 9.11A2 — Remaining / Lower-Tier FC26 Club Coverage — Pre-materialization audit

Audit base: `main@2f2ca08295c6fac086e8e61d7590137a06594c6b` / branch `agent/fc26-remaining-lower-tier-coverage`.

Source snapshot: FC26 `2025-09-19`, using the deterministic diagnostics produced by `FC26 Factual Club Target Materialization #12` on validated head `363a20b794bdf024f17ef73c39d7a40745d850e0`.

This is an audit-only gate. It does **not** add aliases, mappings, clubs, players, ratings, attributes, migrations, or Room schema changes.

## Safety rules

- No fuzzy/similarity score is accepted as identity evidence.
- `club_team_id` + factual identity + country + league/division context is authoritative.
- Existing stable targets are resolved through `StableTeamIdentityRegistry`; audited source-name variants must be explicit.
- A club outside the verified 2026/27 top-flight baseline is not silently mapped to a procedural/fuzzy candidate.
- Countries/leagues without a factual baseline remain blocked.
- Existing unchanged team IDs must remain unchanged.

## Baseline

- FC26 clubs unresolved after 9.11A1: **366**
- UNMATCHED: **364**
- AMBIGUOUS: **2**
- FC26 players blocked by these 366 clubs: **10,050**

## Exclusive classification

- `EXISTING_TARGET_NAME_VARIANT`: **42 clubs / 1,164 players**
- `LOWER_TIER_FACTUAL_CLUB`: **159 clubs / 4,405 players**
- `FACTUAL_COUNTRY_BASELINE_MISSING`: **163 clubs / 4,428 players**
- `AMBIGUOUS`: **2 clubs / 53 players**
- `TARGET_NOT_MATERIALIZED`: **0**
- `UNSAFE_TO_MAP`: **0** assigned from textual/fuzzy evidence; uncertain cases remain blocked instead of being guessed.
- `OTHER`: **0**

Cross-cutting counts:

- Factual identity solution is structurally available inside already-verified countries: **201 clubs / 5,569 players**.
- Immediately safe without creating a new club: **42 clubs / 1,164 players**.
- Require explicit lower-tier factual materialization before mapping: **159 clubs / 4,405 players**.
- Belong to countries/leagues without sufficient factual baseline: **165 clubs / 4,481 players** (includes the 2 ambiguous cases).
- Cannot be safely resolved with the current factual baseline: **165 clubs / 4,481 players**.

## Safe existing-target name variants

| FC26 club_team_id | FC26 source name | Country | Players | Stable factual target |
|---:|---|---|---:|---|
| 10029 | TSG 1899 Hoffenheim | Alemanha | 33 | TSG Hoffenheim |
| 278 | AEK Athens | Grécia | 32 | AEK |
| 10020 | GD Estoril Praia | Portugal | 32 | Estoril Praia |
| 80 | Hearts | Escócia | 32 | Heart of Midlothian |
| 112199 | Sarpsborg 08 FF | Noruega | 32 | Sarpsborg 08 |
| 231 | Club Brugge KV | Bélgica | 31 | Club Brugge |
| 718 | Estrela da Amadora | Portugal | 31 | Estrela Amadora |
| 1910 | NEC Nijmegen | Países Baixos | 31 | N.E.C. Nijmegen |
| 254 | SK Rapid | Áustria | 31 | SK Rapid Wien |
| 1756 | Hamarkameratene | Noruega | 30 | HamKam |
| 100087 | Oud-Heverlee Leuven | Bélgica | 30 | OH Leuven |
| 1906 | AZ Alkmaar | Países Baixos | 29 | AZ |
| 1750 | Cercle Brugge KSV | Bélgica | 29 | Cercle Brugge |
| 1971 | Excelsior | Países Baixos | 29 | Excelsior Rotterdam |
| 101025 | Gençlerbirliği SK | Turquia | 29 | Gençlerbirliği |
| 113459 | Kristiansund BK | Noruega | 29 | Kristiansund |
| 65 | Lille OSC | França | 29 | LOSC |
| 1896 | Sporting Clube de Braga | Portugal | 29 | SC Braga |
| 326 | Fenerbahçe SK | Turquia | 28 | Fenerbahçe |
| 252 | LASK Linz | Áustria | 28 | LASK |
| 101014 | Medipol Başakşehir FK | Turquia | 28 | İstanbul Başakşehir FK |
| 417 | Molde FK | Noruega | 28 | Molde |
| 300 | Viking FK | Noruega | 28 | Viking |
| 710 | Djurgårdens IF | Suécia | 27 | Djurgården |
| 918 | FK Bodø/Glimt | Noruega | 27 | Bodø/Glimt |
| 919 | SK Brann | Noruega | 27 | Brann |
| 2014 | Union Saint-Gilloise | Bélgica | 27 | Royale Union Saint-Gilloise |
| 327 | Beşiktaş JK | Turquia | 26 | Beşiktaş |
| 2041 | Fredrikstad FK | Noruega | 26 | Fredrikstad |
| 325 | Galatasaray SK | Turquia | 26 | Galatasaray |
| 15009 | SC Rheindorf Altach | Áustria | 26 | SCR Altach |
| 708 | Hammarby Fotboll | Suécia | 25 | Hammarby |
| 131491 | KFUM-Kameratene Oslo | Noruega | 25 | KFUM |
| 272 | Odense Boldklub | Dinamarca | 25 | OB |
| 670 | Royal Charleroi Sporting Club | Bélgica | 25 | Sporting Charleroi |
| 418 | Tromsø IL | Noruega | 25 | Tromsø |
| 111339 | Kasımpaşa SK | Turquia | 24 | Kasımpaşa |
| 680 | Sint-Truidense VV | Bélgica | 24 | STVV |
| 271 | Aarhus Gymnastikforening | Dinamarca | 23 | AGF |
| 101026 | Göztepe SK | Turquia | 23 | Göztepe |
| 920 | Vålerenga Fotball | Noruega | 23 | Vålerenga |
| 298 | Rosenborg BK | Noruega | 22 | Rosenborg |

These 42 cases can be implemented as explicit, snapshot-scoped `club_team_id` mappings to already-materialized stable identities. No new club is required.

## Lower-tier materialization demand by country/source league

| Country | FC26 source league | Clubs | Blocked players |
|---|---|---:|---:|
| Inglaterra | Championship | 13 | 377 |
| Inglaterra | League One | 17 | 470 |
| Inglaterra | League Two | 24 | 648 |
| Alemanha | 2. Bundesliga | 15 | 450 |
| Alemanha | 3. Liga | 20 | 547 |
| Alemanha | Bundesliga (now outside verified 2026/27 top flight) | 3 | 85 |
| França | Ligue 2 | 16 | 423 |
| França | Ligue 1 (now outside verified 2026/27 top flight) | 2 | 52 |
| Itália | Serie B | 13 | 363 |
| Itália | Serie A (now outside verified 2026/27 top flight) | 3 | 89 |
| Espanha | La Liga 2 | 10 | 284 |
| Espanha | La Liga (now outside verified 2026/27 top flight) | 1 | 26 |
| Noruega | Eliteserien | 3 | 79 |
| Turquia | Süper Lig | 3 | 83 |
| Suécia | Allsvenskan | 3 | 73 |
| Polônia | Ekstraklasa | 3 | 76 |
| Países Baixos | Eredivisie | 2 | 57 |
| Dinamarca | Superliga | 2 | 58 |
| Portugal | Primeira Liga | 2 | 54 |
| Bélgica | Pro League | 1 | 25 |
| Escócia | Premiership | 1 | 27 |
| Suíça | Super League | 1 | 30 |
| Áustria | Bundesliga | 1 | 29 |

## Highest blocked clubs, deterministic order

The complete 366-club ordering is preserved in the Phase 9.11A1 CI artifact `fc26_unmatched_candidates.json`; its ordering is `playerCount DESC`, then source name/id. The first 40 are repeated here as the human review gate:

| Players | FC26 club_team_id | Source club | League | Country context | Classification |
|---:|---:|---|---|---|---|
| 38 | 111629 | East Bengal FC | Super League | unknown | FACTUAL_COUNTRY_BASELINE_MISSING |
| 38 | 112115 | Gangwon FC | K League 1 | Coreia do Sul | FACTUAL_COUNTRY_BASELINE_MISSING |
| 38 | 2055 | Gimcheon Sangmu FC | K League 1 | Coreia do Sul | FACTUAL_COUNTRY_BASELINE_MISSING |
| 38 | 1477 | Jeonbuk Hyundai Motors | K League 1 | Coreia do Sul | FACTUAL_COUNTRY_BASELINE_MISSING |
| 38 | 1474 | Pohang Steelers | K League 1 | Coreia do Sul | FACTUAL_COUNTRY_BASELINE_MISSING |
| 37 | 171 | 1. FC Nürnberg | 2. Bundesliga | Alemanha | LOWER_TIER_FACTUAL_CLUB |
| 36 | 110588 | 1. FC Magdeburg | 2. Bundesliga | Alemanha | LOWER_TIER_FACTUAL_CLUB |
| 36 | 605 | Al Hilal | Pro League | Arábia Saudita | FACTUAL_COUNTRY_BASELINE_MISSING |
| 36 | 980 | Daejeon Citizen | K League 1 | Coreia do Sul | FACTUAL_COUNTRY_BASELINE_MISSING |
| 36 | 110955 | Shanghai Shenhua | Super League | China | FACTUAL_COUNTRY_BASELINE_MISSING |
| 35 | 111768 | Beijing Guoan | Super League | China | FACTUAL_COUNTRY_BASELINE_MISSING |
| 35 | 112540 | Shanghai Port | Super League | China | FACTUAL_COUNTRY_BASELINE_MISSING |
| 35 | 472 | UD Las Palmas | La Liga 2 | Espanha | LOWER_TIER_FACTUAL_CLUB |
| 34 | 113037 | Al Riyadh | Pro League | Arábia Saudita | FACTUAL_COUNTRY_BASELINE_MISSING |
| 34 | 110078 | Asociația Clubul Sportiv Petrolul 52 | Liga I | unknown | FACTUAL_COUNTRY_BASELINE_MISSING |
| 34 | 1794 | Sheffield United | Championship | Inglaterra | LOWER_TIER_FACTUAL_CLUB |
| 33 | 2038 | Avellino | Serie B | Itália | LOWER_TIER_FACTUAL_CLUB |
| 33 | 112555 | FC Anyang | K League 1 | Coreia do Sul | FACTUAL_COUNTRY_BASELINE_MISSING |
| 33 | 1874 | Ferencvárosi Torna Club | Nemzeti Bajnokság I | unknown | FACTUAL_COUNTRY_BASELINE_MISSING |
| 33 | 576 | Holstein Kiel | 2. Bundesliga | Alemanha | LOWER_TIER_FACTUAL_CLUB |
| 33 | 1478 | Jeju United FC | K League 1 | Coreia do Sul | FACTUAL_COUNTRY_BASELINE_MISSING |
| 33 | 97 | Millwall FC | Championship | Inglaterra | LOWER_TIER_FACTUAL_CLUB |
| 33 | 131173 | Qingdao Hainiu FC | Super League | China | FACTUAL_COUNTRY_BASELINE_MISSING |
| 33 | 101059 | Shakhtar Donetsk | Premier League | unknown | FACTUAL_COUNTRY_BASELINE_MISSING |
| 33 | 10029 | TSG 1899 Hoffenheim | Bundesliga | Alemanha | EXISTING_TARGET_NAME_VARIANT |
| 33 | 160 | VfL Bochum 1848 | 2. Bundesliga | Alemanha | LOWER_TIER_FACTUAL_CLUB |
| 32 | 278 | AEK Athens | Super League | Grécia | EXISTING_TARGET_NAME_VARIANT |
| 32 | 607 | Al Ittihad | Pro League | Arábia Saudita | FACTUAL_COUNTRY_BASELINE_MISSING |
| 32 | 112139 | Al Nassr | Pro League | Arábia Saudita | FACTUAL_COUNTRY_BASELINE_MISSING |
| 32 | 143 | Exeter City | League One | Inglaterra | LOWER_TIER_FACTUAL_CLUB |
| 32 | 10020 | GD Estoril Praia | Primeira Liga | Portugal | EXISTING_TARGET_NAME_VARIANT |
| 32 | 80 | Hearts | Premiership | Escócia | EXISTING_TARGET_NAME_VARIANT |
| 32 | 111779 | Henan FC | Super League | China | FACTUAL_COUNTRY_BASELINE_MISSING |
| 32 | 113040 | NorthEast United | Super League | unknown | FACTUAL_COUNTRY_BASELINE_MISSING |
| 32 | 112199 | Sarpsborg 08 FF | Eliteserien | Noruega | EXISTING_TARGET_NAME_VARIANT |
| 32 | 822 | Vejle Boldklub | Superliga | Dinamarca | LOWER_TIER_FACTUAL_CLUB |
| 32 | 1947 | Wrexham | Championship | Inglaterra | LOWER_TIER_FACTUAL_CLUB |
| 32 | 116361 | Wuhan Three Towns | Super League | China | FACTUAL_COUNTRY_BASELINE_MISSING |
| 31 | 583 | 1. FC Schweinfurt 05 | 3. Liga | Alemanha | LOWER_TIER_FACTUAL_CLUB |
| 31 | 112387 | Al Ahli SFC | Pro League | Arábia Saudita | FACTUAL_COUNTRY_BASELINE_MISSING |

## Ambiguous cases

- `110989 / Caracas FC` — 28 players. Two procedural Venezuelan targets share the same conservative core (`FC Caracas` and `Caracas`). Venezuela also lacks the required factual baseline. Keep blocked.
- `111139 / CF Montréal` — 25 players. Two procedural MLS targets share the same conservative core (`Montreal` and `Montreal FC`). United States/Canada also lacks the required factual baseline. Keep blocked.

## Materialization gate

No `LOWER_TIER_FACTUAL_CLUB` may be materialized from this classification alone. The implementation phase must first pin a 2026/27 lower-tier membership/division baseline from official/primary competition sources, then materialize only those audited identities. Clubs in countries without a sufficient factual baseline remain blocked. Ambiguous clubs remain blocked even when a textual candidate looks strong.
