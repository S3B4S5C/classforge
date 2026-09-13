# CU-12 design

`RelationalModel` is invisible, non-persisted internal IR. `UmlModel` remains source of truth. Mapping is deterministic, has no AI or surrogate IDs, supports composite keys, maps associations independent of drawing direction, uses source=whole/CASCADE for composition and JOINED inheritance. CUSTOM fails closed; CU-13 consumes this IR only.
