# Vision production activation

## Requirements

### R1: Default activation
Dense hybrid SHALL be enabled by default.

### R2: Disabled routing
When explicitly disabled, the system SHALL return the semantic path without
executing hybrid localization or geometry.

### R3: Small diagrams
With hybrid enabled and semantic class count below `minClasses`, the system
SHALL return semantic-only.

### R4: Dense diagrams
With hybrid enabled and semantic class count at least `minClasses`, the system
SHALL enter hybrid-CV. The threshold is inclusive.

### R5: Existing fallback
Cal-014 SHALL preserve the existing fallback-to-semantic behavior.

### R6: Diagnostics and tooling
Production SHALL not collect heavy benchmark diagnostics. Benchmark tooling
SHALL retain explicit mode selection and diagnostic capability.
