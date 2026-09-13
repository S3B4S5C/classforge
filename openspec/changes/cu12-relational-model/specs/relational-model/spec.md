# Relational model requirements

- R1: equal UML content maps to equal IR regardless of collection order.
- R2: mapping SHALL NOT mutate `UmlModel`.
- R3: every non-subclass SHALL have an identifier.
- R4: multiple identifiers SHALL produce a composite PK.
- R5: CUSTOM SHALL fail closed.
- R6: 1:N SHALL store its FK on the many side.
- R7: 1:1 SHALL use deterministic unique-FK ownership.
- R8: N:M SHALL create a join table.
- R9: composition FKs SHALL use CASCADE.
- R10: generalization SHALL use JOINED.
- R11: multiple inheritance SHALL be rejected.
- R12: physical names SHALL be deterministic snake_case.
- R13: collisions SHALL be rejected, never suffixed.
- R14: the IR SHALL not be persisted or exposed through an API.
