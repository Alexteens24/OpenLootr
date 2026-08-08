# ADR 0010: Menu implementation

Status: Pending live evidence

Use Menu Type only if session identity and all required click/drag/shift/number
mutations are observable and reliable. Otherwise use a stable custom inventory
behind the same `MenuFactory` boundary.
