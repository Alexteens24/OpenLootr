# Loot-generation compatibility

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
