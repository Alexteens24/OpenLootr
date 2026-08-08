# OpenLootr

OpenLootr is a Paper-first personal-loot plugin. The current `0.7.0-SNAPSHOT`
implements the Phase 4–9 engineering slice: durable personal inventories,
single/double block containers, storage minecarts, selected vanilla side effects,
compatibility instrumentation and admin diagnostics. It is still an engineering
alpha, not a stable release.

## Target

- Paper/Folia 1.21.11
- Java 21 bytecode
- SQLite supplied by the Paper runtime
- GPL-3.0

## Build

```bash
./gradlew build spikeJar
```

The production JAR is written to `build/libs/OpenLootr-0.7.0-SNAPSHOT.jar`.
The development-only probe plugin is
`build/libs/OpenLootrSpikes-0.7.0-SNAPSHOT-dev.jar`; never ship the latter to a
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
- Candidate identity is adopted only on a valid player right-click. Single block
  containers use PDC v1. Double chests use shared PDC v2 on both halves with a
  canonical owner and explicit left/right source order. Storage minecarts use
  their entity UUID as the OpenLootr UUID.
- Personal contents are committed before first access. Later mutations use
  monotonic CAS revisions, 250 ms coalescing, and close/quit/shutdown flushes.
- A degraded transition captures the exact open top inventory on its entity
  scheduler before detaching the session. Transient failures may recover after
  a successful retry; CAS conflicts remain quarantined until reload/repair.
- Decode/size failures, partial/inconsistent PDC and CAS conflicts fail closed;
  OpenLootr never regenerates or overwrites those rows.
- Clean, closed instances remain hot for 60 seconds and are then eligible for
  eviction. Dirty, degraded, quarantined and corrupt instances are never evicted.
- Shutdown captures sessions, drains the latest revision per key asynchronously
  to a deadline, closes the scheduler gate and only then closes SQLite.

## Alpha scope

- Supported: normal single chests, strict generated double chests, barrels and
  storage minecarts; vanilla/datapack loot tables; mutable personal menus.
- Protected: break, explosions, dynamic chest merge/split, hopper transfer and
  vehicle destruction for candidate/managed/broken targets.
- Deliberately unsupported: trapped chests, chest boats/rafts, shulker boxes,
  hopper/dropper/dispenser/crafter menus, comparator-personal signals and timed
  refresh/network storage.
- Physical lid animation is reference-counted. Chest/barrel statistics,
  generated-loot criterion and nearby-piglin anger are restored after successful menu opening.
  The two NMS calls are isolated and pinned to Paper 1.21.11; linkage failure
  disables the plugin during startup.

## Commands

- `/openlootr info` — version, runtime and SQLite durability.
- `/openlootr inspect` — block/entity descriptor, sources, DB row and cache state;
  uses Paper Dialog API with a text fallback.
- `/openlootr debug` — read-only queues, cache/session/fault and scheduler/NMS data.
- `/openlootr repair` — creates a 30-second confirmation plan for the one safe
  repair case: one valid PDC v2 double-chest half and one clean missing half.

Repair never merges conflicting identities, edits personal rows, clears metadata
or regenerates loot.

## Compatibility and validation

Generation uses `LootTable.fillInventory` with an immutable origin and captured
luck. `LootGenerateEvent` modifiers and cancellation are observed once per
physical loot source; cancelling either half aborts the complete double-chest
first open before a row is inserted. Conditions requiring `THIS_ENTITY` are not
guaranteed because the compatibility-safe v1 context intentionally does not carry
a live player across scheduler boundaries. See
[`docs/compatibility.md`](docs/compatibility.md).

Storage-minecart open-statistic parity remains a Phase 10 live-evidence gate; the
plugin deliberately does not increment `CHEST_OPENED` for minecarts by assumption.

Automated tests do not replace live server evidence. Paper/Folia animation,
sound/game-event duplication, datapack and third-party event fixtures, process-kill
timing and entity unload/removal remain explicit release gates in
[`docs/spikes/README.md`](docs/spikes/README.md). This snapshot does not claim 1.0
or hard-crash dupe freedom.

The precise persistence promise is documented in
[`docs/failure-model.md`](docs/failure-model.md).
