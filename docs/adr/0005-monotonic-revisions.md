# ADR 0005: Monotonic revisions

Status: Accepted by automated tests

One latest snapshot is coalesced per key. Database writes use revision CAS; a
zero-row result forces a canonical read. Dirty clears only when committed and
current revisions match. Older revisions never overwrite newer revisions.
