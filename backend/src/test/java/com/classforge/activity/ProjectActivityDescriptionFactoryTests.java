package com.classforge.activity;

import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.collaboration.protocol.*;
import com.classforge.project.domain.document.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectActivityDescriptionFactoryTests {
    final ProjectCommandExecutor executor = new ProjectCommandExecutor();
    final ProjectActivityDescriptionFactory factory = new ProjectActivityDescriptionFactory(executor);
    final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), attrId = UUID.randomUUID(), relId = UUID.randomUUID();
    final UmlAttribute attribute = new UmlAttribute(attrId, "nombre", UmlDataType.STRING, null, UmlVisibility.PRIVATE, false, false);
    final UmlRelationship relation = new UmlRelationship(relId, a, b, UmlRelationshipType.ASSOCIATION, Multiplicity.one(), Multiplicity.many());
    final DiagramNodeLayout layout = new DiagramNodeLayout(10, 10, 260, 160);

    ProjectDocument document() {
        return new ProjectDocument("1.0", new UmlModel(List.of(new UmlClass(a, "Animal", List.of(attribute)),
                new UmlClass(b, "Dueño", List.of())), List.of(relation)), new DiagramLayout(Map.of(a, layout, b, layout)));
    }
    UmlCommandPayload command(UmlCommandType type, UUID classId, String name, UmlClass umlClass, UmlAttribute attr,
            UUID attributeId, UmlRelationship relationship, UUID relationshipId) {
        return new UmlCommandPayload(UUID.randomUUID(), Instant.now(), type, classId, name, umlClass, layout,
                attr, attributeId, relationship, relationshipId);
    }
    ProjectOperationPersistedEvent event(ProjectDocument before, ProjectDocument after, UmlCommandPayload command) {
        return new ProjectOperationPersistedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Autor", 0, 1,
                ActivitySource.MANUAL, command, before, after, Instant.now());
    }
    ActivityChange describe(UmlCommandPayload command) {
        var before = document();
        return factory.changes(event(before, executor.execute(before, command), command)).getFirst();
    }

    @Test void renameDeleteMoveAndRestoreRetainReadableClassNames() {
        assertEquals("renombró la clase Animal a Mascota", describe(command(UmlCommandType.RENAME_CLASS, a, "Mascota", null, null, null, null, null)).description());
        assertEquals("eliminó la clase Animal", describe(command(UmlCommandType.DELETE_CLASS, a, null, null, null, null, null, null)).description());
        assertEquals("movió la clase Animal", describe(command(UmlCommandType.MOVE_CLASS, a, null, null, null, null, null, null)).description());
        var restored = command(UmlCommandType.RESTORE_CLASS, null, null, new UmlClass(UUID.randomUUID(), "Factura", List.of()), null, null, null, null);
        assertEquals("restauró la clase Factura", describe(restored).description());
    }

    @Test void attributeDescriptionsUseNamesTypesAndTheOwningClass() {
        var custom = new UmlAttribute(UUID.randomUUID(), "tipo", UmlDataType.CUSTOM, "Especie", UmlVisibility.PRIVATE, true, false);
        assertEquals("añadió tipo: Especie en Animal", describe(command(UmlCommandType.ADD_ATTRIBUTE, a, null, null, custom, null, null, null)).description());
        var changed = new UmlAttribute(attrId, "alias", UmlDataType.STRING, null, UmlVisibility.PUBLIC, true, false);
        assertEquals("actualizó alias: STRING en Animal", describe(command(UmlCommandType.UPDATE_ATTRIBUTE, a, null, null, changed, null, null, null)).description());
        assertEquals("eliminó nombre: STRING de Animal", describe(command(UmlCommandType.DELETE_ATTRIBUTE, a, null, null, null, attrId, null, null)).description());
    }

    @Test void relationshipDescriptionsResolveBothEndpointsIncludingDeletedRelationships() {
        var created = new UmlRelationship(UUID.randomUUID(), b, a, UmlRelationshipType.AGGREGATION, Multiplicity.one(), Multiplicity.many());
        assertEquals("creó la relación Dueño → Animal (AGGREGATION)", describe(command(UmlCommandType.CREATE_RELATIONSHIP, null, null, null, null, null, created, null)).description());
        var changed = new UmlRelationship(relId, a, b, UmlRelationshipType.AGGREGATION, Multiplicity.one(), Multiplicity.many());
        assertEquals("actualizó la relación Animal → Dueño (AGGREGATION)", describe(command(UmlCommandType.UPDATE_RELATIONSHIP, null, null, null, null, null, changed, null)).description());
        assertEquals("eliminó la relación Animal → Dueño (ASSOCIATION)", describe(command(UmlCommandType.DELETE_RELATIONSHIP, null, null, null, null, null, null, relId)).description());
    }

    @Test void fullSavesDetectRenamesAttributesRelationsAndLayoutRatherThanGuessingCommands() {
        var before = document();
        var changed = new UmlAttribute(attrId, "alias", UmlDataType.STRING, null, UmlVisibility.PRIVATE, false, false);
        var newAttr = new UmlAttribute(UUID.randomUUID(), "edad", UmlDataType.INTEGER, null, UmlVisibility.PRIVATE, true, false);
        var after = new ProjectDocument("1.0", new UmlModel(List.of(new UmlClass(a, "Mascota", List.of(changed, newAttr))), List.of()),
                new DiagramLayout(Map.of(a, new DiagramNodeLayout(80, 20, 260, 160))));
        var changes = factory.changes(event(before, after, null));
        assertEquals(Set.of(ActivityOperationType.DELETE_CLASS, ActivityOperationType.RENAME_CLASS, ActivityOperationType.UPDATE_ATTRIBUTE,
                        ActivityOperationType.ADD_ATTRIBUTE, ActivityOperationType.DELETE_RELATIONSHIP, ActivityOperationType.MOVE_CLASS),
                changes.stream().map(ActivityChange::operationType).collect(java.util.stream.Collectors.toSet()));
        assertTrue(changes.stream().anyMatch(c -> c.description().contains("Dueño")));
    }

    @Test void fullSavesDetectAttributeRemovalAndRelationshipCreationOrUpdate() {
        var before = document();
        var after = new ProjectDocument("1.0", new UmlModel(List.of(new UmlClass(a, "Animal", List.of()), new UmlClass(b, "Dueño", List.of())),
                List.of(new UmlRelationship(relId, a, b, UmlRelationshipType.AGGREGATION, Multiplicity.one(), Multiplicity.many()),
                        new UmlRelationship(UUID.randomUUID(), b, a, UmlRelationshipType.ASSOCIATION, Multiplicity.one(), Multiplicity.many()))), before.layout());
        assertEquals(List.of(ActivityOperationType.DELETE_ATTRIBUTE, ActivityOperationType.UPDATE_RELATIONSHIP, ActivityOperationType.CREATE_RELATIONSHIP),
                factory.changes(event(before, after, null)).stream().map(ActivityChange::operationType).toList());
    }
}
