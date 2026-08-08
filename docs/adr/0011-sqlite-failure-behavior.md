# ADR 0011: SQLite failure behavior

Status: Unit foundation verified; process/live failures pending

Verify duplicate insert, external lock/busy timeout, write failure, malformed
row, repeated initialization, disable with queued work and process kill around
commit. Ambiguous commit performs a canonical read before regeneration. Live
WAL databases must never be backed up by copying only the main `.db` file.

Persistence health is explicit: `HEALTHY → DEGRADING → DEGRADED → HEALTHY` for
recoverable write failures, while a CAS conflict transitions through
`DEGRADING` to `QUARANTINED` and never auto-merges or auto-recovers. The
degrading transition captures an active top inventory on the owning entity
scheduler before the session is removed and its menu is closed.
