# Image product E2E

## Requirements

### R1: Non-mutating planning
Generating an image plan SHALL NOT mutate ProjectDocument persistence.

### R2: READY command
READY SHALL contain a canonical BATCH and its baseRevision.

### R3: Canonical Apply
The exact BATCH returned by image planning SHALL be executable by the canonical
collaboration apply authority.

### R4: Preview equivalence
The persisted document after Apply SHALL equal the deterministic preview of that
same BATCH.

### R5: One revision
One assistant BATCH SHALL increment project revision exactly once regardless of
child command count.

### R6: Reopen equivalence
Reopening the project SHALL reconstruct the same ProjectDocument.

### R7: Stale plan rejection
A project revision change between image plan and Apply SHALL reject the stale
assistant operation.

### R8: No stale partial mutation
A stale assistant operation SHALL NOT partially persist any assistant child
command.

### R9: Inference revision guard
A revision change while vision inference is running SHALL prevent READY from
being returned.

### R10: Real-VLM canonical authority
The real-VLM acceptance E2E SHALL use the same canonical collaboration apply
authority when enabled.
