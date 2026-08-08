# Persistence failure model

OpenLootr has two different durability problems and does not blur them together.

## Atomic first open

For a previously unseen `(container UUID, player UUID)` pair:

1. look up the row on the serialized DB executor;
2. generate on the owning block region or entity scheduler;
3. insert the complete versioned BLOB;
4. commit SQLite;
5. revalidate the physical identity;
6. open the menu on the player scheduler.

The application in-flight registry coalesces concurrent work and SQLite's primary
key is the final uniqueness barrier. An ambiguous insert acknowledgement is
resolved by reading the canonical row; loot is never exposed before a row exists.

## Later mutations

Minecraft player persistence and `openlootr.db` are separate transaction domains.
No plugin can make item transfer between them one ACID transaction. OpenLootr
therefore promises crash resistance with a bounded dirty window, not hard-crash
dupe freedom.

Mutations increment an in-memory revision. The writer takes only the latest
snapshot, updates with `WHERE revision = expected`, and marks only the written
revision committed. If revision 18 appears while revision 17 is writing, the
instance remains dirty. Older callbacks cannot clean or overwrite newer state.

Writes use SQLite WAL, `synchronous=FULL`, an explicit busy timeout and one
serialized DB executor. Changes are coalesced for 250 ms and flushed on close,
quit, kick and shutdown. Repeated transient failures close the menu and retry;
successful retry returns the instance to healthy. CAS conflict quarantines the
instance. Decode or logical-size failure marks it corrupt. Neither state is
evicted, regenerated or overwritten automatically.

On shutdown OpenLootr stops new opens, captures sessions on their owner, awaits an
asynchronous drain deadline, closes the scheduler gate, then closes SQLite. A
deadline failure is logged loudly because the remaining state exists only in
memory.
