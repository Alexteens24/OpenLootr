# Loot-generation compatibility

## Runtime and NMS linkage

The same production JAR supports exactly Paper `1.21.11`, `26.1.2`, and `26.2`.
OpenLootr compiles against the oldest API (`1.21.11`) with Java 21 bytecode; a
Paper 26.1+ server must itself run on Java 25.

No production class imports Minecraft or CraftBukkit internals. Startup first
checks the exact runtime allowlist, then `VanillaParityLinker` resolves the full
generated-loot and piglin-anger surface through `MethodHandle`s. `1.21.11` and
`26.1.2` share a layout. `26.2` has one separate layout for Mojang's advancement
trigger package move. A missing class, field or exact descriptor disables the
plugin before SQLite initialization and before any inventory can open.

CI builds one `OpenLootr.jar` and passes that same uploaded artifact to all three
Paper runtime jobs. These jobs are boot/linkage/SQLite/clean-shutdown smoke tests,
not substitutes for real-player gameplay fixtures.

OpenLootr 0.7 targets the public Paper loot pipeline:

- vanilla loot tables: supported;
- datapack loot tables: supported by key lookup;
- plugins modifying `LootGenerateEvent`: supported when they handle
  plugin-generated events;
- cancellation: aborts the entire first open before insertion;
- plugins deliberately skipping `event.isPlugin()`: known limitation;
- conditions requiring a player in `THIS_ENTITY`: not guaranteed in the v1
  player-less context;
- custom NMS generation pipelines: unsupported unless a concrete compatibility
  adapter is justified by real demand.

For a double chest OpenLootr invokes generation once per physical half in stable
left/right order. Each half receives a SplitMix64-derived stream from the persisted
personal seed, then occupies slots `0..26` or `27..53`. If either generation is
cancelled or fails, no personal row is established.

The generated-loot advancement criterion is a separate vanilla parity hook. It is
triggered only after a newly committed instance is successfully presented. Piglin
anger is triggered after every successful personal block-container open.
