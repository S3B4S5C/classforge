# Hybrid fail-closed policy

## Requirements

### R1: Default
Hybrid semantic fallback SHALL be disabled by default.

### R2: Hybrid success
When hybrid is selected and succeeds, the system SHALL return the hybrid proposal.

### R3: Transport failure
When hybrid is selected and fails with `TRANSPORT`, the failure SHALL propagate as `VISION` / `TRANSPORT`.

### R4: Contract failure
When hybrid is selected and fails contractually or internally, the failure SHALL propagate fail-closed as `VISION` / `OUTPUT_CONTRACT`.

### R5: No semantic result
A hybrid failure SHALL NOT return the semantic proposal when fallback is false.

### R6: No mutation
A failed hybrid request SHALL NOT produce preview, command, or mutation.

### R7: Retry
The UI SHALL retain retry through its existing image-request error flow.

### R8: Rollback
Explicit fallback `true` SHALL remain supported for operational rollback.

### R9: Routing exceptions
Disabled hybrid and class count below `minClasses` SHALL remain semantic-only and SHALL NOT be described as fallback.
