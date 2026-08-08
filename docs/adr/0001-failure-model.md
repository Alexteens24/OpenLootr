# ADR 0001: Failure model

Status: Accepted

First-open access is forbidden until the complete SQLite row commits. Later
mutations have a normally bounded crash-dupe window only while storage is
healthy; OpenLootr cannot transact atomically with Minecraft player persistence.
Persistent write failure must eventually freeze/close the affected session,
retain dirty memory, and prohibit eviction.
