# Cal-013: Per-edge relationship annotation

Relationship inference receives one independently rendered panel for one fixed geometry edge. Geometry owns physical edge existence: every `VisionGeometryEdgeCandidate` requires exactly one classification with that edge ID. The classifier owns only type, marker, evidence, and confidence; it cannot add or remove endpoints. Java preserves the existing assembler and aggregates singleton responses in geometry order; its relationship confidence is the minimum non-null singleton confidence.

The singleton contract bounds free-form output: `evidenceLabel` has a 120-character maximum and `warnings` permits at most one 160-character warning. The prompt further limits evidence to 12 words and requests no reasoning or process description.

Each edge stage includes its edge ID. If a response ends at `finish_reason=length`, the gateway emits an `OUTPUT_CONTRACT` diagnostic with bounded content and reasoning prefixes plus the reported completion token count. This is diagnostic only: no retry or token escalation occurs. The real per-edge default remains 512 tokens in application and benchmark entry points. `response_format` remains `json_object` with the schema; evaluation of `json_schema` is deferred until partial output is observed under this bounded contract.

## Fix-002: endpoint-isolated multiplicity transcription

Type and marker classification remain a single authoritative VLM call over the full edge panel. It no longer receives or returns multiplicities. Each endpoint then has a separate, stateless VLM authority which receives only its endpoint crop and class identity. It cannot receive the opposite endpoint image, class, label, or result.

Multiplicity observations return only a bounded raw visual label (`null` or at most 16 characters) and confidence. Java deterministically parses `N`, `*`, `N..M`, and `N..*`; malformed labels fail closed. Endpoint calls use a separate 128-token default, with no token escalation or retry. Diagnostic output retains the full sheet and adds each relationship panel, endpoint panel, and parsed observation.

## Fix-004: connector-scoped endpoint evidence

Geometry optionally records an inner point about 28 pixels from each contact, derived only from the nearest segment in that edge's component root. It is diagnostic metadata and does not affect DSU, pair selection, attachment, score, or topology. Endpoint evidence is a 512x280 dual view of the same clean crop: original and 90-degree counter-clockwise rotation. Each view overlays the contact crosshair and, when available, an arrow toward the inner point to identify the connector branch. This improves handwriting orientation and branch locality without changing physical topology or multiplicity parsing.

## Fix-005: clean transcription and guided connector ownership

Fix-004 improved ownership precision but reduced multiplicity recall when its overlays obscured handwritten labels. Endpoint evidence therefore separates two visual authorities without changing the endpoint-isolation boundary: CLEAN is an unannotated 90-degree counter-clockwise rotation of the existing crop for character transcription, while GUIDED is the original crop with contact/inner annotations for connector ownership. When an inner point exists, GUIDED attenuates only the exterior of a 90-pixel maximum, 36-pixel half-width connector corridor plus a 40-pixel contact zone; it preserves the highlighted area and surrounding context. UML multiplicity text may be adjacent to, rather than on, the connector. The corridor is renderer-only and changes neither geometry nor topology.

## Fix-006: transcribe first, verify ownership second

Fix-005 showed that asking one inference to transcribe and decide ownership over-rejected visible labels. Each endpoint now uses two stateless VLM authorities: recall-oriented transcription receives only a clean 256x280 rotated crop and returns a bounded raw label; precision-oriented ownership receives only a guided 256x280 original crop plus that candidate label and returns `BELONGS`, `NOT_BELONGS`, or `AMBIGUOUS`. Java holds the transient candidate state, skips ownership for a null transcription, parses only `BELONGS`, and deterministically maps `NOT_BELONGS` and `AMBIGUOUS` to null. Both stages retain the 128-token budget, no retry, and no history. This presentation and annotation flow remains independent of geometry and physical topology.

## Fix-007: competing connector context

An audit found code/spec drift: the documented competing-connector ownership context had not been implemented in production, so prior benchmark results did not validate fix-007. The production ownership panel now presents the current connector in red and visible physical connectors of the same endpoint class in blue. Blue competitors receive contact and direction overlays but no corridor; a 28-pixel contact zone preserves nearby evidence through the existing dimming. A single deterministic, edge-ID-ordered authority selects same-class competing endpoints, filters geometry that does not intersect the ownership crop, and applies the same crop and panel transform to both the visual overlays and Java ownership guides. This iteration corrects that drift and adds no new acceptance heuristic. Fix-007 remains pending real hybrid validation.

## Fix-008: localized deterministic ownership guard

Ownership now returns the approximate normalized center of the requested candidate label over the complete 256x280 ownership panel. Java maps that center without rounding to panel pixels and reuses the shared fix-007 crop transform and contact/inner geometry to construct 90-pixel centerlines for the current connector and visible same-endpoint-class competitors. When both guides exist, a competitor dominates only when its point-to-segment distance plus `max(6px, panelWidth * 0.03)` is strictly less than the current distance. The guard is one-way and fail-closed: it may change VLM `BELONGS` to `NOT_BELONGS` with `COMPETING_CONNECTOR_CLOSER`; it never promotes `NOT_BELONGS` or `AMBIGUOUS`. It uses neither oracle data nor UML semantics, and does not affect physical topology. The code/spec drift correction does not change the 90-pixel guide length, dominance margin, point-to-segment calculation, or one-way policy.

## Fix-009: class-context ownership

Real diagnostics invalidated localized label coordinates as an ownership authority: Qwen returned `(500,500)` for every non-null observation. A contact-centered ownership crop also can include another connector's label while excluding that connector's contact and guide. Transcription remains unchanged and endpoint-local. Ownership now resolves exactly one geometry region by endpoint `classRef`, then renders the full class box expanded by `max(70px, min(imageWidth, imageHeight) / 11)` into a 384x360 panel. The current connector remains red; every incident competing connector is blue in deterministic edge-ID order, using the correct A or B contact/inner endpoint. Coordinates and the localized guard no longer participate in runtime acceptance. Ownership accepts VLM `BELONGS`, rejects `NOT_BELONGS` and `AMBIGUOUS`, and retains visible competing edge IDs as diagnostics. Topology remains geometry-owned.

## Fix-010: source-grounded connector attribution

Fix-009 made all incident connectors visible, but E6-B still returned `BELONGS` while E4 was visible because class context did not identify the exact visual source of the transcribed label. Ownership now receives a 640x360 attribution panel: an unannotated original-orientation reuse of the same endpoint crop used by transcription on the left, and the unchanged class-context evidence on the right. Connector overlays include their physical edge IDs. The closed response is an owner enum containing current edge ID, visible competing edge IDs in sorted order, `AMBIGUOUS`, and `NONE`. Java parses only when `owner == currentEdgeId`; a competing owner rejects with `OWNED_BY_COMPETING_EDGE`. No VLM coordinates, topology mutation, retry, or token increase is used.

The attribution PNG, named `multiplicity-<edgeId>-<endpoint>-attribution.png`, is the exact 640x360 image passed to the attribution gateway and retained in diagnostics. The legacy 384x360 ownership renderer is class-context-only evidence and is not an attribution gateway input.

## Fix-011: competitor-suppressed clean transcription

Fix-010 exhausted explicit attribution: E6-B still selected E6 despite E4 and label source being visible. Conditioning therefore occurs before the unchanged single transcription call. On the same endpoint crop before its existing CCW rotation, Java builds current support from a 90px/36px corridor plus 40px contact radius and every visible incident competitor from a 90px/28px corridor plus 40px contact radius. Only `competitorMask AND NOT currentSupportMask` is replaced with opaque white. Thus exclusive competing evidence is removed while overlap and current evidence remain. Attribution remains a secondary fail-closed defense; no semantic, oracle, topology, parser, budget, or retry change is involved.
