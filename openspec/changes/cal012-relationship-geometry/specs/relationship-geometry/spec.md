# Relationship Geometry

## Raster Endpoint Bridge

The analyzer SHALL merge distinct endpoint components only when the configured
bridge distance and raster tube coverage requirements both pass. Geometric
proximity alone SHALL NOT merge components.

## Local Raster Attachment

The analyzer SHALL use raster attachment only from an endpoint-limited local
foreground region. It SHALL attach a class only when exactly one class is within
the existing contact distance; ambiguous local contact SHALL NOT attach a class.

Direct Hough class contacts SHALL take precedence over local raster contacts.
Local raster MAY fill a missing class attachment, but SHALL NOT add a third
class to a component with two direct class contacts. Local propagation SHALL
NOT make effective component cardinality exceed two.

## Physical Pairs

The geometry stage SHALL identify an unordered physical class pair. Edge endpoint
ordering SHALL NOT imply semantic relationship direction.

## Crossings

Geometric crossings at different angles SHALL NOT become graph junctions.

## Fixture Evidence

The `library-whiteboard-realistic` oracle SHALL contain only physically drawn
class pairs. Usuario-Libro SHALL NOT be expected because no connector supports
that pair in the fixture.
