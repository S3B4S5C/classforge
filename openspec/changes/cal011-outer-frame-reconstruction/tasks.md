# Cal-011 - Primitive hypothesis implementation tasks

- [x] Preserve preprocessing and strict/relaxed contour thresholds.
- [x] Model strict contours as rectangular primitives; deduplicate strict only when near-identical.
- [x] Remove early `expectedClassCount` strict-seed selection.
- [x] Generate direct/complete, iterative compartment-chain, and Hough hypotheses.
- [x] Permit chains to consume strict or relaxed primitives and continue after dividers.
- [x] Apply expected count only to deterministic final-hypothesis selection.
- [x] Preserve DTOs, null semantic refs, final ordering, and internal diagnostics.
- [x] Add focused synthetic IoU coverage for a strict chain and a continuation after a divider.
- [x] Require complete internal evidence at final selection; add focal incomplete one-source rejection coverage.
- [x] Emit internal companion and raw-Hough evidence diagnostics without changing thresholds.
- [x] Project Hough corner continuations at the closing coordinate and calculate
  that coordinate at the reconstructed-union midpoint; emit detailed `H1..Hn`
  and `B1..Bn` diagnostic provenance.
- [x] Require a companion chain to have multiple sources and be a maximal merge
  before treating it as evidence-complete.
- [x] Expand companion chains to a fixed point; retain only truly maximal chains
  as evidence-complete and emit fixed-point provenance.
- [x] Add binary projected-corner bridge fallback after Hough continuation
  rejection, with source-specific diagnostics and no threshold/parameter changes.
- [x] Add focused coverage for a chain beyond three expansions and binary bridge
   support/rejection.
- [x] Treat any valid Hough-projected corner as `1.0` continuation evidence.
- [x] Permit companion depth through the current short side plus adjacency tolerance.
- [x] Add synthetic Hough and companion-depth IoU `>= 0.90` oracle coverage.
- [x] Run `OpenCvUmlClassRegionDetectorTests`: 11 passed, 0 failed. The realistic
    whiteboard fixture selects six evidence-complete candidates, including B2 by
    Hough reconstruction.
- [x] Run `VisionGeometryClassMappingValidatorTests` and
    `OpenCvUmlDiagramGeometryAnalyzerTests`: all passed.
- [ ] Do not commit.
