# ADR 0007: PDC lifecycle

Status: Metadata contract implemented; lifecycle evidence pending

Test break, Silk Touch, place, explosion, chunk reload and plugin block-state
copying for metadata version, identity, source loot-table key and source seed.
Identity-present malformed metadata always fails closed.

The v1 keys are `metadata_version`, 16-byte `container_id`, canonical
`loot_table`, and `source_loot_seed` in the `openlootr` namespace. Adoption is
only performed by a valid right-click candidate path and is immediately read
back for verification.

Adoption uses `BlockState.update(false, false)`: it does not force a stale tile
snapshot over a changed block type. Exact concurrent-state behavior remains in
the live PDC lifecycle matrix.
