# OpenLootr

OpenLootr is a Paper-first personal-loot plugin. The current `0.0.1-SNAPSHOT`
milestone contains the Phase-0 technical-spike harness and the production
lifecycle/SQLite skeleton; it does **not** intercept gameplay containers yet.

## Target

- Paper/Folia 1.21.11
- Java 21 bytecode
- SQLite supplied by the Paper runtime
- GPL-3.0

## Build

```bash
./gradlew build spikeJar
```

The production JAR is written to `build/libs/OpenLootr-0.0.1-SNAPSHOT.jar`.
The development-only probe plugin is
`build/libs/OpenLootrSpikes-0.0.1-SNAPSHOT-dev.jar`; never ship the latter to a
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
