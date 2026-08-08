# Phase 4–9 implementation record

This branch intentionally ships Phase 4 through Phase 9 as one reviewable PR,
with release version `0.7.0-SNAPSHOT`.

## Implemented

- 60-second lazy instance cache and safe eviction predicates;
- monotonic latest-snapshot writer, recovery/quarantine/corruption states and
  asynchronous shutdown drain;
- deterministic persistence fault fixtures and strict codec torture coverage;
- immutable block/entity locators and ordered multi-source descriptors;
- strict PDC v2 double-chest adoption and SplitMix64 per-source seeds;
- storage minecart resolution, interaction and physical protection;
- terminal entity-retirement paths and owner-time minecart origin capture;
- shared block/entity first-open service and explicit scheduler revalidation;
- reference-counted lids, statistics and version-isolated vanilla parity bridge;
- multi-source `LootGenerateEvent` cancellation semantics and documented context
  limitations;
- Dialog-backed inspect, read-only debug metrics, and nonce/fingerprint repair.

## Still release-gated by live evidence

- generated double-chest layout across rotations and chunk reload;
- storage minecart unload versus permanent removal on Paper and Folia;
- exact lid sound/game-event behavior (no manual duplicate emission is added);
- piglin and advancement parity on the pinned server build;
- exact vanilla storage-minecart statistic behavior;
- datapack and third-party `LootGenerateEvent` compatibility fixtures;
- forced process kill at each persistence boundary;
- Paper/Folia stress and profiler runs.

These gates belong to release hardening. Their absence must not be represented as
successful evidence in a changelog or release claim.
