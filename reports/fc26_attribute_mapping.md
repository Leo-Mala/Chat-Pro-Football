# FC26 -> Pro Football attribute mapping

The FC26 snapshot is the source of truth for initial `Player.force` (`overall`) and `Player.potential` (`potential`). No gameplay rating is randomly generated for imported players.

Detailed mapped attributes are emitted by `tools/fc26/import_fc26.py`. Direct mappings are used whenever an equivalent FC26 column exists. Pro Football-only mental attributes are deterministic formulas built only from FC26 fields.

| Pro Football | FC26 / formula |
|---|---|
| reflexos | `goalkeeping_reflexes` |
| pegada | `goalkeeping_handling` |
| umContraUm | avg(`goalkeeping_reflexes`, `goalkeeping_positioning`, `goalkeeping_diving`) |
| saidaDeGol | `goalkeeping_positioning` |
| lancamento | GK: `goalkeeping_kicking`; outfield: `skill_long_passing` |
| desarme | `defending_standing_tackle` |
| marcacao | `defending_marking_awareness` |
| cabeceio | `attacking_heading_accuracy` |
| passeCurto | `attacking_short_passing` |
| cruzamento | `attacking_crossing` |
| drible | `skill_dribbling` |
| passe | avg(`attacking_short_passing`, `skill_long_passing`, `mentality_vision`) |
| primeiroToque | `skill_ball_control` |
| finalizacao | `attacking_finishing` |
| chuteDeLonge | `power_long_shots` |
| controleBola | `skill_ball_control` |
| posicionamento | GK: `goalkeeping_positioning`; outfield: `mentality_positioning` |
| concentracao | avg(`movement_reactions`, `mentality_composure`) |
| sangueFrio | `mentality_composure` |
| antecipacao | avg(`movement_reactions`, `mentality_interceptions`) |
| bravura | avg(`mentality_aggression`, `movement_reactions`, `mentality_composure`) |
| trabalhoEquipe | avg(`attacking_short_passing`, `mentality_vision`, `movement_reactions`) |
| decisao | avg(`movement_reactions`, `mentality_composure`, `mentality_vision`) |
| semBola | `mentality_positioning` |
| visaoJogo | `mentality_vision` |
| criatividade | avg(`mentality_vision`, `skill_curve`, `skill_ball_control`) |
| agressividade | `mentality_aggression` |
| lideranca | avg(`movement_reactions`, `mentality_composure`, international reputation scaled 20..100) |
| regularidade | avg(`movement_reactions`, `mentality_composure`, `power_stamina`) |
| agilidade | `movement_agility` |
| impulsao | `power_jumping` |
| forca | `power_strength` |
| velocidade | GK: `goalkeeping_speed` when present; outfield: `movement_sprint_speed` |
| aceleracao | `movement_acceleration` |
| resistencia | `power_stamina` |

## Position compatibility

`GK -> GOL`, `CB -> ZAG`, `LB/RB/LWB/RWB -> LAT`, `CDM -> VOL`, `CM/CAM/LM/RM -> MEI`, `LW/RW/CF/ST -> ATA`.

The original FC26 primary and alternate positions are preserved in the import metadata envelope stored with the player, while `Player.position` remains compatible with the current match/tactics engine.

## Money normalization

FC26 exposes `value_eur`, `wage_eur`, and `release_clause_eur`. Runtime conversion is centralized in the FC26 money policy using the dataset snapshot reference rate stored in the manifest. No live network dependency is introduced.
