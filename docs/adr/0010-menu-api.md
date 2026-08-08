# ADR 0010: Menu implementation

Status: Provisional adapter selected; live mutation matrix pending

The alpha tries Paper Menu Type first and catches runtime failure to use a stable
custom inventory behind `MenuFactory`. Sessions compare the exact top inventory
object, never its title. Click and drag mutations are sampled on the player's
next owned tick; close, quit and kick request immediate persistence.
