# Cal-012 - Raster-supported relationship geometry

## Decision

Relationship graph construction retains the current Hough parameters, endpoint
join distance, class contact distance, and crossing policy. Raster evidence is
used only as a fail-closed fallback.

The geometry stage identifies an unordered physical class pair. Edge endpoint
ordering is not semantic relationship direction.

Nearby endpoints from different graph components may join only when their
distance is at most `max(joinDistance, minDimension / 60.0)` and a two-pixel
tube along the endpoint segment has foreground coverage of at least `0.75` in
the existing closed binary image.

Class attachment remains endpoint-first. When an endpoint has no direct class
contact, an 8-connected flood-fill is limited to its local ROI, with radius
`max(24, minDimension / 12)`. Exactly one class must be reached within the
existing contact distance; zero or multiple candidates reject propagation.

Direct Hough contact has higher authority than local raster fallback. Local
raster may fill a missing class attachment, but may not add a third class to a
component already supported by two direct classes. Effective component contacts
therefore cannot exceed two classes because of local propagation.

The B3-B4 realistic regression is resolved by rejecting B5 local fallback on
the already complete direct B3-B4 component.

An audit of `library-whiteboard-realistic.png` confirmed that Usuario-Libro is
not physically drawn. The prior benchmark oracle contained that extra expected
relationship; HybridGeometryOnly now produces the six visible relationships
with no unexpected geometry pairs. Cal-012 must not add a segment graph to
manufacture a relationship without physical evidence.

Global raster connected components are not valid marker-attachment units:
markers physically connected to a relationship line correctly share a large
component with that line. Local, endpoint-scoped raster evidence prevents that
component from becoming global class authority.

## Diagnostics

`graph-debug.json` records endpoint bridge decisions and local raster attachment
attempts, including endpoint ROI, seed state, local pixel count, class distances,
and fail-closed result.
