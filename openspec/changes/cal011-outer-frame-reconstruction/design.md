# Cal-011 - Primitive and hypothesis class-region detection

## Decision

`OpenCvUmlClassRegionDetector` treats contours as physical primitives, not class
regions. It keeps the current grayscale, 3x3 blur, adaptive threshold `(31, 9)`
and 3x3 close unchanged.

Strict rectangular contours retain their existing gates and are the only roots
of a class hypothesis. Strict contours are deduplicated only when they are
near-identical (`IoU >= 0.85`); overlapping, nested, and nearby strict
primitives remain available. Relaxed contours retain the existing relaxed gates
and are companion-only primitives.

## Hypotheses

Each strict primitive produces independent alternatives:

1. Direct primitive; an internal divider marks it as a complete-frame
   alternative, but never removes other alternatives.
2. Iterative compartment chains. A chain can consume strict or relaxed
   primitives, including an enclosing contour, and can continue past a divider.
    Each step must satisfy the existing orientation, overlap, adjacency/depth,
    growth, area, and score gates. Expansion continues to a fixed point, not a
    depth limit. A chain is evidence-complete only when it has consumed more
    than one source and no further accepted merge remains;
    provisional chain states remain diagnosable but ineligible.
3. Local Hough closing-border alternatives. Hough is evidence for an optional
   hypothesis, not a reason to discard a direct or chain alternative.

Hypotheses retain their source seed and reconstruction provenance internally.
There is no public diagnostic DTO change. Confidence remains diagnostic-only.

## Final Selection

Only after all hypotheses are generated does the detector apply
`expectedClassCount`. It deterministically ranks hypotheses and selects exactly
that many pairwise non-ambiguous, evidence-complete final regions. A direct
one-source primitive without an observed internal divider is incomplete and is
ineligible; complete-frame, companion-chain, and Hough-closing hypotheses carry
the required independent evidence. Final regions must contain their strict
source, remain in bounds, have no pairwise IoU `>= 0.18`, and not contain
another final center. Failure to select exactly the requested number throws
`AssistantPlanningException`; no proximity synthesis or semantic data is used.

Final boxes are ordered by `y`, then `x`, and emitted as `B1..Bn` with null
`classRef`. Threshold and overlay diagnostics show those final hypotheses.

## Scope

Public detector DTOs, thresholds, preprocessing, CV-first semantic mapping, and
relationship geometry remain unchanged. Hough/complete-frame observations are
internal hypothesis evidence only. Hough corner support projects each
    continuation to the closing border and projects the closing coordinate at the
    reconstructed-union midpoint without changing the existing Hough gates. If
     no eligible Hough continuation is found, the threshold image may provide a
     full projected-corner bridge from the seed border to that coordinate.
     Any valid Hough-projected corner provides full continuation evidence. A
     companion may add depth through its current short side plus the existing
     adjacency tolerance.

## Internal Diagnostics

Each detection attempt writes a best-effort JSON evidence trace under
`backend/build/reports/opencv-class-region-detector/`. It records every raw
Hough segment from divider and closing-border searches, detailed `H1..Hn`
closing hypotheses, companion evaluations, ranked candidates, and detailed
    final `B1..Bn` selections. Chain source-count/fixed-point maximal-merge
    provenance and Hough-versus-binary projected-corner evidence are retained.
    The trace is internal-only and
cannot change detection thresholds or public DTOs.
