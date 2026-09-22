package com.classforge.project.domain.document;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Backward-compatible association-class metadata stored inside ProjectDocument.
 *
 * <p>ClassForge keeps its stable ProjectDocument schema unchanged: an UML
 * AssociationClass is materialized internally as a normal UmlClass plus two
 * generator-friendly associations. A compact marker attached to a non-CUSTOM
 * attribute preserves the original association for the visual model, AI and
 * XMI interchange layers.</p>
 */
public final class AssociationClassSupport {

    public static final String MARKER_V1 = "__classforge_association_class_v1__";
    public static final String MARKER_V2 = "__classforge_association_class_v2__";

    private AssociationClassSupport() {
    }

    public static String marker(UmlRelationship relationship) {
        if (relationship == null) {
            throw new IllegalArgumentException("relationship is required");
        }
        return String.join(
                "|",
                MARKER_V2,
                relationship.id().toString(),
                relationship.sourceClassId().toString(),
                relationship.targetClassId().toString(),
                relationship.type().name(),
                encodeMultiplicity(relationship.sourceMultiplicity()),
                encodeMultiplicity(relationship.targetMultiplicity())
        );
    }

    public static boolean isMarker(String value) {
        return value != null
                && (value.startsWith(MARKER_V1 + "|") || value.startsWith(MARKER_V2 + "|"));
    }

    public static Optional<Metadata> metadata(UmlClass umlClass) {
        if (umlClass == null || umlClass.attributes() == null) {
            return Optional.empty();
        }
        for (UmlAttribute attribute : umlClass.attributes()) {
            Optional<UmlRelationship> relationship = parseMarker(attribute.customTypeName());
            if (relationship.isPresent()) {
                return Optional.of(new Metadata(attribute.id(), relationship.get()));
            }
        }
        return Optional.empty();
    }

    public static List<UmlAttribute> visibleAttributes(UmlClass umlClass) {
        if (umlClass == null) {
            return List.of();
        }
        return umlClass.attributes().stream()
                .filter(attribute -> !isMarker(attribute.customTypeName()))
                .toList();
    }

    /**
     * Returns exactly the generator-only relationships corresponding to known
     * AssociationClasses. They must not leak into XMI or AI presentation.
     */
    public static Set<UUID> auxiliaryRelationshipIds(ProjectDocument document) {
        if (document == null || document.umlModel() == null) {
            return Set.of();
        }
        Set<UUID> result = new LinkedHashSet<>();
        List<UmlRelationship> relationships = document.umlModel().relationships();
        for (UmlClass umlClass : document.umlModel().classes()) {
            Optional<Metadata> metadata = metadata(umlClass);
            if (metadata.isEmpty()) {
                continue;
            }
            UmlRelationship original = metadata.get().relationship();
            UUID associationClassId = umlClass.id();
            findAuxiliary(relationships, original.sourceClassId(), associationClassId)
                    .ifPresent(item -> result.add(item.id()));
            findAuxiliary(relationships, original.targetClassId(), associationClassId)
                    .ifPresent(item -> result.add(item.id()));
        }
        return Set.copyOf(result);
    }

    public static List<Descriptor> descriptors(ProjectDocument document) {
        if (document == null || document.umlModel() == null) {
            return List.of();
        }
        List<Descriptor> result = new ArrayList<>();
        for (UmlClass umlClass : document.umlModel().classes()) {
            metadata(umlClass).ifPresent(metadata -> result.add(new Descriptor(
                    umlClass,
                    metadata,
                    auxiliaryRelationships(document, umlClass, metadata)
            )));
        }
        return List.copyOf(result);
    }

    public static List<UmlRelationship> auxiliaryRelationships(
            ProjectDocument document,
            UmlClass associationClass,
            Metadata metadata
    ) {
        if (document == null || associationClass == null || metadata == null) {
            return List.of();
        }
        List<UmlRelationship> result = new ArrayList<>();
        UmlRelationship original = metadata.relationship();
        findAuxiliary(document.umlModel().relationships(), original.sourceClassId(), associationClass.id())
                .ifPresent(result::add);
        findAuxiliary(document.umlModel().relationships(), original.targetClassId(), associationClass.id())
                .ifPresent(result::add);
        return List.copyOf(result);
    }

    public static UmlClass withMarker(UmlClass umlClass, UmlRelationship relationship) {
        List<UmlAttribute> attributes = new ArrayList<>(umlClass.attributes());
        String marker = marker(relationship);

        for (int index = 0; index < attributes.size(); index++) {
            UmlAttribute attribute = attributes.get(index);
            if (isMarker(attribute.customTypeName())) {
                attributes.set(index, copyWithCustomType(attribute, marker));
                return new UmlClass(umlClass.id(), umlClass.name(), attributes);
            }
        }

        for (int index = 0; index < attributes.size(); index++) {
            UmlAttribute attribute = attributes.get(index);
            if (attribute.dataType() != UmlDataType.CUSTOM && attribute.identifier()) {
                attributes.set(index, copyWithCustomType(attribute, marker));
                return new UmlClass(umlClass.id(), umlClass.name(), attributes);
            }
        }

        // Never hide or overload a domain attribute merely to persist presentation
        // metadata. If the UML class has no identifier (valid in imported UML),
        // inject a deterministic technical UUID identifier that can safely carry
        // the marker and also makes the association entity generable.
        UUID markerAttributeId = UUID.nameUUIDFromBytes((
                "classforge:association-class-marker:" + umlClass.id() + ":" + relationship.id()
        ).getBytes(StandardCharsets.UTF_8));
        attributes.add(0, new UmlAttribute(
                markerAttributeId,
                "id",
                UmlDataType.UUID,
                marker,
                UmlVisibility.PRIVATE,
                false,
                true
        ));
        return new UmlClass(umlClass.id(), umlClass.name(), attributes);
    }

    private static UmlAttribute copyWithCustomType(UmlAttribute attribute, String marker) {
        return new UmlAttribute(
                attribute.id(),
                attribute.name(),
                attribute.dataType(),
                marker,
                attribute.visibility(),
                attribute.nullable(),
                attribute.identifier()
        );
    }

    private static Optional<UmlRelationship> parseMarker(String value) {
        if (!isMarker(value)) {
            return Optional.empty();
        }
        String[] parts = value.split("\\|", -1);
        try {
            if (MARKER_V1.equals(parts[0]) && parts.length == 6) {
                return Optional.of(new UmlRelationship(
                        UUID.fromString(parts[1]),
                        UUID.fromString(parts[2]),
                        UUID.fromString(parts[3]),
                        UmlRelationshipType.ASSOCIATION,
                        decodeMultiplicity(parts[4]),
                        decodeMultiplicity(parts[5])
                ));
            }
            if (MARKER_V2.equals(parts[0]) && parts.length == 7) {
                UmlRelationshipType type = UmlRelationshipType.valueOf(parts[4]);
                if (type == UmlRelationshipType.GENERALIZATION) {
                    return Optional.empty();
                }
                return Optional.of(new UmlRelationship(
                        UUID.fromString(parts[1]),
                        UUID.fromString(parts[2]),
                        UUID.fromString(parts[3]),
                        type,
                        decodeMultiplicity(parts[5]),
                        decodeMultiplicity(parts[6])
                ));
            }
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<UmlRelationship> findAuxiliary(
            List<UmlRelationship> relationships,
            UUID endpoint,
            UUID associationClassId
    ) {
        return relationships.stream()
                .filter(item -> item.type() == UmlRelationshipType.ASSOCIATION)
                .filter(item -> item.sourceClassId().equals(endpoint)
                        && item.targetClassId().equals(associationClassId))
                .findFirst();
    }

    private static String encodeMultiplicity(Multiplicity multiplicity) {
        if (multiplicity == null) {
            return "-";
        }
        return multiplicity.lower() + ":" + (multiplicity.upper() == null ? "*" : multiplicity.upper());
    }

    private static Multiplicity decodeMultiplicity(String value) {
        if ("-".equals(value)) {
            return null;
        }
        String[] parts = value.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("invalid multiplicity marker");
        }
        int lower = Integer.parseInt(parts[0]);
        Integer upper = "*".equals(parts[1]) ? null : Integer.valueOf(parts[1]);
        return new Multiplicity(lower, upper);
    }

    public record Metadata(UUID markerAttributeId, UmlRelationship relationship) {
    }

    public record Descriptor(UmlClass umlClass, Metadata metadata, List<UmlRelationship> auxiliaryRelationships) {
        public UmlRelationship relationship() {
            return metadata.relationship();
        }
    }
}
