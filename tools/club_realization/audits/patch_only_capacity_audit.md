# Patch-only real-club capacity audit

This audit records a fail-closed capacity check for the Brasfoot patch used as the club/crest source for the 1,907 procedural club slots.

- Source archive: `3 221 Mobile Brasfoot(1).rar`
- Source archive SHA-256: `83b5466af153f79709abae267d74a44aca4f048965c689415d57ee00fa3a7d8b`
- Procedural replacement demand: **1,907** slots across **51** countries/groups.
- Archive inventory observed during the audit: **8,310 `.ban`** files and **8,348 PNG** files.
- Parseable `.ban` files observed: **8,309**.
- Capacity rule used here is deliberately permissive: count a `.ban` only when its metadata can be parsed and a corresponding crest exists; do not yet subtract aliases, reserve/B/II teams, or clubs already present as factual clubs in the app.
- Result: **12** countries are already below demand under this permissive ceiling, with a **minimum aggregate deficit of 174 clubs**.

Because aliases/reserves/existing factual clubs have not yet been deducted, 174 is a lower bound, not the final deficit. The materializer must therefore not force the Brasfoot patch alone to cover all 1,907 slots. Supplemental sources require an explicit auditable club-country-crest relationship and canonical duplicate protection.

See `patch_only_capacity_audit.csv` for the full per-country counts.
