package com.classforge.project.domain.document;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssociationClassSupportTests {

    @Test
    void v2MarkerPreservesRelationshipTypeEndpointsAndMultiplicities() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UmlRelationship original = new UmlRelationship(
                UUID.randomUUID(), source, target, UmlRelationshipType.COMPOSITION,
                new Multiplicity(1, null), new Multiplicity(1, null)
        );
        UmlClass associationClass = AssociationClassSupport.withMarker(
                new UmlClass(UUID.randomUUID(), "DetallePedido", List.of(
                        new UmlAttribute(
                                UUID.randomUUID(), "id", UmlDataType.UUID, null,
                                UmlVisibility.PRIVATE, false, true
                        ),
                        new UmlAttribute(
                                UUID.randomUUID(), "cantidad", UmlDataType.INTEGER, null,
                                UmlVisibility.PUBLIC, false, false
                        )
                )),
                original
        );

        AssociationClassSupport.Metadata metadata = AssociationClassSupport.metadata(associationClass).orElseThrow();

        assertEquals(original, metadata.relationship());
        assertTrue(AssociationClassSupport.isMarker(
                associationClass.attributes().getFirst().customTypeName()
        ));
        assertEquals(List.of("cantidad"), AssociationClassSupport.visibleAttributes(associationClass)
                .stream().map(UmlAttribute::name).toList());
    }

    @Test
    void legacyV1MarkerRemainsReadableAsAssociation() {
        UUID relationshipId = UUID.randomUUID();
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        String marker = String.join(
                "|",
                AssociationClassSupport.MARKER_V1,
                relationshipId.toString(),
                source.toString(),
                target.toString(),
                "1:*",
                "0:*"
        );
        UmlClass associationClass = new UmlClass(UUID.randomUUID(), "Inscripcion", List.of(
                new UmlAttribute(
                        UUID.randomUUID(), "id", UmlDataType.UUID, marker,
                        UmlVisibility.PRIVATE, false, true
                )
        ));

        UmlRelationship restored = AssociationClassSupport.metadata(associationClass)
                .orElseThrow().relationship();

        assertEquals(UmlRelationshipType.ASSOCIATION, restored.type());
        assertEquals(new Multiplicity(1, null), restored.sourceMultiplicity());
        assertEquals(new Multiplicity(0, null), restored.targetMultiplicity());
        assertFalse(AssociationClassSupport.visibleAttributes(associationClass).stream()
                .anyMatch(attribute -> AssociationClassSupport.isMarker(attribute.customTypeName())));
    }
    @Test
    void classWithoutIdentifierGetsTechnicalMarkerIdWithoutHidingDomainAttributes() {
        UUID source = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        UmlRelationship original = new UmlRelationship(
                UUID.randomUUID(), source, target, UmlRelationshipType.ASSOCIATION,
                Multiplicity.one(), Multiplicity.many()
        );
        UmlClass sourceClass = new UmlClass(UUID.randomUUID(), "DetallePedido", List.of(
                new UmlAttribute(
                        UUID.randomUUID(), "cantidad", UmlDataType.INTEGER, null,
                        UmlVisibility.PUBLIC, false, false
                ),
                new UmlAttribute(
                        UUID.randomUUID(), "precioUnitario", UmlDataType.DECIMAL, null,
                        UmlVisibility.PUBLIC, false, false
                )
        ));

        UmlClass marked = AssociationClassSupport.withMarker(sourceClass, original);

        assertEquals(List.of("cantidad", "precioUnitario"), AssociationClassSupport.visibleAttributes(marked)
                .stream().map(UmlAttribute::name).toList());
        assertTrue(marked.attributes().stream().anyMatch(attribute ->
                attribute.identifier()
                        && "id".equals(attribute.name())
                        && AssociationClassSupport.isMarker(attribute.customTypeName())
        ));
    }

}
