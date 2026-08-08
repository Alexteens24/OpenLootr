# ADR 0011: SQLite failure behavior

Status: Unit foundation verified; process/live failures pending

Verify duplicate insert, external lock/busy timeout, write failure, malformed
row, repeated initialization, disable with queued work and process kill around
commit. Ambiguous commit performs a canonical read before regeneration. Live
WAL databases must never be backed up by copying only the main `.db` file.
