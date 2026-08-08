# ADR 0004: Folia ownership pipeline

Status: Ownership boundaries implemented; live Folia torture evidence pending

Validate region/entity → DB → owner → DB → player dispatch, retirement behavior,
and stale open-attempt rejection. No live Bukkit world object may enter DB work.

On disable, active sessions are captured before storage drain. Scheduler
callbacks are guarded by an accepting gate that is closed before SQLite begins
closing, because region/entity scheduler APIs do not expose one universal
plugin-wide cancellation primitive. Live delayed-callback torture tests remain
a release gate.
