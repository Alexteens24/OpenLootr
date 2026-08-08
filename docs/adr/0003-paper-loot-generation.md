# ADR 0003: Paper loot-generation semantics

Status: Provisional implementation; pending live gameplay evidence

The alpha uses a scratch inventory and `LootTable.fillInventory` with a random
per-player seed persisted in the first-open row. A MONITOR listener scoped to
the synchronous generation observes cancellation; cancellation aborts and no
row is inserted. The context carries captured luck but deliberately no live
player object across ownership boundaries.

Live probes must still verify `isPlugin`, cancellation ordering, holder/entity,
datapack behavior and seeded repeatability before release.
