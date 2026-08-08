# OpenLootr

OpenLootr is a Paper-first personal-loot plugin. The current `0.1.0-SNAPSHOT`
implements the first playable vertical slice for single structure chests and
barrels. It is an engineering alpha, not a stable release.

## Target

- Paper/Folia 1.21.11
- Java 21 bytecode
- SQLite supplied by the Paper runtime
- GPL-3.0

## Build

```bash
./gradlew build spikeJar
```

The production JAR is written to `build/libs/OpenLootr-0.1.0-SNAPSHOT.jar`.
The development-only probe plugin is
`build/libs/OpenLootrSpikes-0.1.0-SNAPSHOT-dev.jar`; never ship the latter to a
production server.

Run a Paper development server with `./gradlew runServer`. Folia probes use the
same two JARs on a pinned Folia 1.21.11 server and the checklist in
[`docs/spikes/README.md`](docs/spikes/README.md).

## Current guarantees

- First-open access will only be allowed after its complete SQLite row commits.
- Later inventory mutations can only offer a normally bounded crash-dupe window
  while storage is healthy; Minecraft player persistence and SQLite are separate
  transaction domains.
- Managed containers will never silently fall back to vanilla behavior.
- Normal containers with no loot table and no OpenLootr metadata are untouched.
- Candidate identity is adopted only on a valid player right-click and stores a
  version, random UUID, original loot-table key and original seed in tile PDC.
- Personal contents are committed before first access. Later mutations use
  monotonic CAS revisions, 250 ms coalescing, and close/quit/shutdown flushes.
- A degraded transition captures the exact open top inventory on its entity
  scheduler before detaching the session. Transient failures may recover after
  a successful retry; CAS conflicts remain quarantined until reload/repair.
- Decode/size failures, partial PDC, managed double chests and CAS conflicts fail
  closed; OpenLootr never regenerates or overwrites those rows.

## Alpha scope

- Supported: single chests, barrels, vanilla/datapack loot tables, mutable menus.
- Protected: break, explosions and hopper transfer for candidate/managed blocks.
- Deliberately vanilla: completely unmanaged double chests.
- Not implemented yet: managed double chests, entity containers, vanilla
  animation/sound side effects, repair tooling and refresh/network modes.

`/openlootr inspect` reports resolution/metadata and asynchronously counts the
container's personal instances. Live Paper/Folia gameplay probes in
[`docs/spikes/README.md`](docs/spikes/README.md) remain release gates.
