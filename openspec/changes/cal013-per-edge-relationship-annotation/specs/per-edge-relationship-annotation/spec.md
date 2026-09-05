# Per-edge Relationship Annotation

## Requirements

### Requirement: Independent relationship inference
The hybrid analyzer SHALL render and annotate every geometry edge candidate independently in deterministic geometry order. It SHALL retain the full evidence sheet for diagnostics.

### Requirement: Geometry-owned topology contract
Geometry SHALL own physical edge existence. Every `VisionGeometryEdgeCandidate` SHALL produce exactly one classification restricted to its requested edge ID. The classifier SHALL decide only relationship type, marker, evidence, and confidence; it SHALL not add or remove endpoints. Zero classifications, multiple classifications, or another edge ID SHALL fail with `OUTPUT_CONTRACT`.

The schema SHALL bound `evidenceLabel` to 120 characters and `warnings` to at most one 160-character string.

### Requirement: Bounded truncation diagnostics
The per-edge stage SHALL include the requested edge ID. If inference ends with `finish_reason=length`, the gateway SHALL return `OUTPUT_CONTRACT` with the stage, reported completion tokens, and content and reasoning lengths and prefixes capped at 400 characters. It SHALL not retry or increase the 512-token per-edge default.

`response_format` SHALL remain `json_object` with the supplied schema until partial-output diagnostics have been evaluated.

### Requirement: Aggregation
Java SHALL aggregate singleton annotations without changing assembler semantics. Aggregate relationship confidence SHALL be the minimum non-null singleton confidence.

### Requirement: Endpoint-isolated multiplicity transcription and ownership
The classifier SHALL decide only relationship type, marker, evidence, and confidence from the full edge panel. For every geometry edge candidate, the analyzer SHALL make independent, stateless transcription and ownership calls per endpoint. Neither call SHALL receive opposite-endpoint context or prior VLM chat history.

The 256x280 transcription panel SHALL use the existing crop radius and contain only an unannotated 90-degree counter-clockwise crop. It SHALL return a bounded candidate raw label without deciding connector ownership. If the label is null, Java SHALL not call ownership. The 384x360 ownership panel SHALL use class-context evidence with a red current contact crosshair and, when geometry supplies an optional inner point, a red branch arrow and presentation-only local corridor. The corridor SHALL have a 90-pixel maximum length, 36-pixel half-width, and 40-pixel contact zone. Visible physical connectors of the same endpoint class SHALL be shown in blue with a contact crosshair and, when available, a direction arrow but no blue corridor. Dimming SHALL be no stronger than alpha 0.40 and preserve a 28-pixel zone around each visible competing contact. A legend SHALL identify `RED = CURRENT` and `BLUE = OTHER`. Multiplicity text MAY be adjacent to its connector branch rather than directly on its line. Ownership SHALL be `BELONGS`, `NOT_BELONGS`, or `AMBIGUOUS`; Java SHALL parse only `BELONGS` and SHALL map the other two values to null. The ownership prompt SHALL classify only labels associated with red as `BELONGS`, clearly blue-associated labels as `NOT_BELONGS`, and indistinguishable red/blue ownership as `AMBIGUOUS`. This evidence SHALL not affect physical topology.

The visual competitor list and deterministic ownership-guide list SHALL share one authority. It SHALL exclude the current edge, resolve the endpoint matching the current class reference, order competitors by edge ID, reject connector geometry that does not intersect the current ownership crop, and use the same endpoint crop and panel transform for blue overlays and guard guides.

### Requirement: Deterministic multiplicity parsing
Transcription SHALL return only `edgeId`, `endpoint`, `rawLabel`, and `confidence`. `rawLabel` SHALL be null or a string of at most 16 characters. Ownership SHALL return only `edgeId`, `endpoint`, `ownership`, and `confidence`, where ownership is the closed enum `BELONGS`, `NOT_BELONGS`, or `AMBIGUOUS`. Java SHALL parse only `N`, `*`, `N..M`, and `N..*`, with non-negative integers and `M >= N`; all other accepted non-empty labels SHALL fail with an output contract error.

### Requirement: Localized ownership veto
Localized label coordinates and the deterministic localized ownership guard SHALL not participate in runtime acceptance because observed VLM coordinates are not reliable localization evidence.

### Requirement: Class-context ownership evidence
For ownership, Java SHALL resolve exactly one `VisionGeometryClassRegion` by endpoint `classRef`; zero or multiple regions SHALL fail closed with `OUTPUT_CONTRACT`. It SHALL crop that class region expanded on every side by `max(70px, min(imageWidth, imageHeight) / 11)`, clamped to source bounds, and render it at 384x360. The panel SHALL show the current contact/inner branch in red and every incident competitor endpoint of the same class in blue, ordered by edge ID. A competitor SHALL use contact/inner A when the class is A and contact/inner B when the class is B. Ownership acceptance SHALL use VLM ownership directly: only `BELONGS` is parsed; `NOT_BELONGS` and `AMBIGUOUS` map to null.

### Requirement: Endpoint diagnostics and budget
Each multiplicity stage SHALL use an independent 128-token default with no retry or token escalation. Diagnostics SHALL persist transcription panels, ownership panels when invoked, raw and effective ownership, visible competing edge IDs, parsed multiplicity, and final acceptance for every endpoint.

### Requirement: Source-grounded connector attribution
When transcription returns a raw label, attribution SHALL receive a 640x360 panel containing the same endpoint crop used by transcription in original orientation as `LABEL SOURCE`, alongside unchanged class-context evidence as `CLASS CONTEXT`. Current and visible competing overlays SHALL show their edge IDs. Attribution SHALL return only `edgeId`, `endpoint`, `owner`, and `confidence`; `owner` SHALL be a closed enum ordered as current edge ID, sorted distinct visible competing IDs, `AMBIGUOUS`, and `NONE`. Java SHALL parse the raw label only when owner equals the current edge ID. A competing owner SHALL reject with `OWNED_BY_COMPETING_EDGE`; `AMBIGUOUS` and `NONE` SHALL reject. Diagnostics SHALL record raw/effective owner, attribution confidence, visible competitors, and rejection reason.
