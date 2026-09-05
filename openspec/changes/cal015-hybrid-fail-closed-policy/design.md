# Cal-015 - Hybrid fail-closed policy

## Context

Cal-014 promoted hybrid-CV while temporarily preserving semantic fallback. For
dense diagrams, returning the first semantic proposal after hybrid geometry or
annotation fails bypasses Cal-012 topology authority.

## Decision

Production fallback defaults to `false`. Once hybrid is selected, failures fail
closed through the existing `VISION` error contract: `OUTPUT_CONTRACT` or
`TRANSPORT`, HTTP 503, and frontend retry. No proposal, preview, Apply, or
mutation is returned.

`CLASSFORGE_ASSISTANT_VISION_HYBRID_FALLBACK=true` remains an operational
rollback/debug override. Diagrams below `minClasses` remain semantic-only by
routing; they are not fallback. This changes no public API, frontend, or vision
inference behavior.
