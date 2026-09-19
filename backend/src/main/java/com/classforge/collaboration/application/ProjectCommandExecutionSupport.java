package com.classforge.collaboration.application;

import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.domain.document.*;
import java.util.*;

final class ProjectCommandExecutionSupport {

    UmlRelationship normalizeRelationship(
            UmlRelationship relationship
    ) {
        if (
                relationship == null
                        || relationship.type()
                        != UmlRelationshipType.GENERALIZATION
        ) {
            return relationship;
        }

        return new UmlRelationship(
                relationship.id(),
                relationship.sourceClassId(),
                relationship.targetClassId(),
                relationship.type(),
                null,
                null
        );
    }

    ProjectDocument document(
            ProjectDocument current,
            List<UmlClass> classes,
            List<UmlRelationship> relationships,
            Map<UUID, DiagramNodeLayout> nodes
    ) {
        return new ProjectDocument(
                current.schemaVersion(),
                new UmlModel(
                        classes,
                        relationships
                ),
                new DiagramLayout(nodes)
        );
    }

    ArrayList<UmlClass> classes(
            ProjectDocument current
    ) {
        return new ArrayList<>(
                current.umlModel()
                        .classes()
        );
    }

    ArrayList<UmlRelationship> relationships(
            ProjectDocument current
    ) {
        return new ArrayList<>(
                current.umlModel()
                        .relationships()
        );
    }

    HashMap<UUID, DiagramNodeLayout> nodes(
            ProjectDocument current
    ) {
        return new HashMap<>(
                current.layout()
                        .nodes()
        );
    }

    UmlClass findClass(
            ProjectDocument current,
            UUID classId
    ) {
        if (classId == null) {
            return null;
        }

        return current.umlModel()
                .classes()
                .stream()
                .filter(
                        candidate ->
                                candidate.id()
                                        .equals(classId)
                )
                .findFirst()
                .orElse(null);
    }

    int classIndex(
            List<UmlClass> classes,
            UUID classId
    ) {
        for (
                int index = 0;
                index < classes.size();
                index++
        ) {
            if (
                    classes.get(index)
                            .id()
                            .equals(classId)
            ) {
                return index;
            }
        }

        throw new ProjectCommandRejectedException(
                "CLASS_NOT_FOUND",
                "The class no longer exists"
        );
    }

    int attributeIndex(
            List<UmlAttribute> attributes,
            UUID attributeId
    ) {
        for (
                int index = 0;
                index < attributes.size();
                index++
        ) {
            if (
                    attributes.get(index)
                            .id()
                            .equals(attributeId)
            ) {
                return index;
            }
        }

        throw new ProjectCommandRejectedException(
                "ATTRIBUTE_NOT_FOUND",
                "The attribute no longer exists"
        );
    }

    int relationshipIndex(
            List<UmlRelationship> relationships,
            UUID relationshipId
    ) {
        for (
                int index = 0;
                index < relationships.size();
                index++
        ) {
            if (
                    relationships.get(index)
                            .id()
                            .equals(relationshipId)
            ) {
                return index;
            }
        }

        throw new ProjectCommandRejectedException(
                "RELATIONSHIP_NOT_FOUND",
                "The relationship no longer exists"
        );
    }

    UUID requireId(
            UUID value,
            String code,
            String message
    ) {
        require(
                value != null,
                code,
                message
        );

        return value;
    }

    void requireText(
            String value,
            String code,
            String message
    ) {
        require(
                value != null
                        && !value.isBlank(),
                code,
                message
        );
    }

    void require(
            boolean condition,
            String code,
            String message
    ) {
        if (!condition) {
            throw new ProjectCommandRejectedException(
                    code,
                    message
            );
        }
    }
}
