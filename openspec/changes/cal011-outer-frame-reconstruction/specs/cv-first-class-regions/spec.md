# CV-first class regions - Cal-011 primitive hypotheses

## ADDED Requirements

### Requirement: Strict contours are primitives

The detector SHALL retain the existing strict rectangular contour thresholds and
preprocessing. A strict contour SHALL be a candidate primitive, not an early
class selection. It MAY be removed only when it is near-identical to another
strict primitive (`IoU >= 0.85`).

#### Scenario: More strict contours than semantic classes

- **WHEN** strict contours include frames and compartments
- **THEN** the detector SHALL retain them for hypothesis generation
- **AND** SHALL NOT stop after `expectedClassCount` strict contours

### Requirement: Hypotheses may form compartment chains

Each strict primitive SHALL produce direct, complete-frame, chain, and eligible
Hough alternatives. A compartment chain MAY consume strict or relaxed
primitives and SHALL be able to continue after an observed divider.

#### Scenario: Strict chain crosses a divider

- **WHEN** a strict compartment has an aligned successive companion after a divider
- **THEN** the detector SHALL retain the continued chain as a final-frame hypothesis

#### Scenario: Provisional chain merge

- **WHEN** a chain has only one consumed source or an accepted successive merge
  remains
- **THEN** the detector SHALL retain it for diagnostics but SHALL NOT treat it
  as evidence-complete for final selection

### Requirement: Expected count is final-only

The detector SHALL apply `expectedClassCount` only when selecting final,
pairwise non-ambiguous, evidence-complete hypotheses. A direct primitive without
an internal divider SHALL NOT be eligible for final selection. If exactly that
many valid final regions cannot be selected, it SHALL throw
`AssistantPlanningException`.

#### Scenario: Incomplete one-source primitive

- **WHEN** a strict primitive has neither an internal divider nor companion or
  Hough closing evidence
- **THEN** the detector SHALL reject it from final selection

### Requirement: Internal reconstruction evidence is diagnosable

The detector SHALL write best-effort internal JSON diagnostics below
`backend/build/reports/opencv-class-region-detector/` for each detection attempt.
The diagnostics SHALL contain every raw Hough line, detailed `H1..Hn` closing
hypotheses, companion evaluations, ranked candidates, and detailed final
`B1..Bn` selections, including source-count and maximal-merge provenance where
applicable.

### Requirement: Chain and projected-corner reconstruction complete their evidence

Compartment-chain expansion SHALL continue until no accepted companion merge
remains. A projected closing corner without an eligible Hough continuation MAY
use a threshold-image bridge only when the bridge covers the full path from the
seed border to the projected closing coordinate. This fallback SHALL NOT change
any existing threshold, parameter, or Hough acceptance gate.
Any valid Hough-projected corner SHALL provide continuation evidence of `1.0`.
A companion's maximum accepted added depth SHALL be its current short side plus
the existing adjacency tolerance.

#### Scenario: Chain exceeds the former depth cap

- **WHEN** successive accepted companions remain after three expansions
- **THEN** the detector SHALL continue expanding the chain
- **AND** SHALL mark a chain maximal only once no accepted companion remains

#### Scenario: Binary projected-corner bridge fallback

- **WHEN** no eligible Hough continuation supports a projected corner
- **AND** threshold pixels continuously bridge the seed border and projected closing border
- **THEN** the detector SHALL retain that corner as binary bridge evidence
- **AND** SHALL record its evidence source in internal diagnostics

### Requirement: Contracts and diagnostics remain stable

The detector SHALL preserve its public DTO shapes, null `classRef` output,
spatial `B1..Bn` ordering, threshold diagnostic, and final-frame overlay.
