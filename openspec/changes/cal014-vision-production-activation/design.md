# Cal-014 - Vision production activation

## Context

Cal-011, Cal-012 and Cal-013 closed the focal hybrid-CV calibration. The
`library-whiteboard-realistic` fixture achieved 3/3 Exact.

## Decision

Hybrid-CV is the normal path for dense diagrams. The semantic pass always runs
first. When hybrid is disabled or the semantic class count is below four, the
semantic proposal is returned. At four or more classes, the hybrid-CV pipeline
runs. Fallback to semantic remains enabled.

Production does not retain heavy diagnostics; benchmark and geometry-only
paths retain them. This change adds no public API, UI, model, prompt, mask, or
geometry change. `CLASSFORGE_ASSISTANT_VISION_HYBRID=false` remains the
semantic-only kill switch.
