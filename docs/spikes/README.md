# Phase-0 live probe protocol

Build both artifacts with `./gradlew build spikeJar`, install the production and
`-dev` spike JARs, then run the same matrix on pinned Paper and Folia 1.21.11.
The probe plugin appends machine-readable evidence to
`plugins/OpenLootrSpikes/spike-results.jsonl`.

## Commands

- `/olspike status` — show the evidence path.
- `/olspike inspect` — record the targeted block state, loot table and identity.
- `/olspike mark` — attach disposable OpenLootr test metadata to a targeted lootable tile.
- `/olspike codec` — round-trip the held item through the production codec.

Only run `mark` on disposable fixtures. The harness is intentionally absent from
the production OpenLootr JAR.

## Required matrix

1. Create structure-loot fixtures for a chest, barrel and valid double chest.
2. Capture inspect → LOWEST interaction → loot-generation → MONITOR interaction → inventory-open.
3. Repeat through hopper extraction, break, Silk Touch, explosion and place.
4. Test a datapack loot table and a listener that modifies and cancels plugin-generated loot.
5. Move a storage minecart across region boundaries on Folia and record scheduler threads.
6. Mark both double-chest halves, reload chunks, split/merge fixtures, and inspect both halves.
7. Run codec probes with custom metadata, enchanted items and nested shulkers.
8. Execute the SQLite failure checklist in ADR 0011.

Copy the relevant JSONL excerpts and exact server build into each ADR. Never mark
an ADR Accepted from assumptions alone.
