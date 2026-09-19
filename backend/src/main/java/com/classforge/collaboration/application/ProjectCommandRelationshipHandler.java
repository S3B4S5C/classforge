package com.classforge.collaboration.application;

import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.domain.document.*;
import java.util.*;

final class ProjectCommandRelationshipHandler {
    private final ProjectCommandExecutionSupport support;

    ProjectCommandRelationshipHandler(ProjectCommandExecutionSupport support) {
        this.support = support;
    }

    ProjectDocument createRelationship(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UmlRelationship relationship =
                support.normalizeRelationship(
                        command.relationship()
                );

        support.require(
                relationship != null,
                "RELATIONSHIP_REQUIRED",
                "CREATE_RELATIONSHIP requires relationship"
        );

        support.require(
                relationship.id() != null,
                "RELATIONSHIP_ID_REQUIRED",
                "CREATE_RELATIONSHIP requires relationship.id"
        );

        boolean exists =
                current.umlModel()
                        .relationships()
                        .stream()
                        .anyMatch(
                                candidate ->
                                        candidate.id()
                                                .equals(
                                                        relationship.id()
                                                )
                        );

        support.require(
                !exists,
                "DUPLICATE_RELATIONSHIP_ID",
                "A relationship with this id already exists"
        );

        ArrayList<UmlRelationship> relationships =
                support.relationships(current);

        relationships.add(
                relationship
        );

        return support.document(
                current,
                support.classes(current),
                relationships,
                support.nodes(current)
        );
    }

    ProjectDocument updateRelationship(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UmlRelationship relationship =
                support.normalizeRelationship(
                        command.relationship()
                );

        support.require(
                relationship != null,
                "RELATIONSHIP_REQUIRED",
                "UPDATE_RELATIONSHIP requires relationship"
        );

        support.require(
                relationship.id() != null,
                "RELATIONSHIP_ID_REQUIRED",
                "UPDATE_RELATIONSHIP requires relationship.id"
        );

        ArrayList<UmlRelationship> relationships =
                support.relationships(current);

        int index =
                support.relationshipIndex(
                        relationships,
                        relationship.id()
                );

        relationships.set(
                index,
                relationship
        );

        return support.document(
                current,
                support.classes(current),
                relationships,
                support.nodes(current)
        );
    }

    ProjectDocument deleteRelationship(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UUID relationshipId =
                support.requireId(
                        command.relationshipId(),
                        "RELATIONSHIP_ID_REQUIRED",
                        "DELETE_RELATIONSHIP requires relationshipId"
                );

        ArrayList<UmlRelationship> relationships =
                support.relationships(current);

        int index =
                support.relationshipIndex(
                        relationships,
                        relationshipId
                );

        relationships.remove(index);

        return support.document(
                current,
                support.classes(current),
                relationships,
                support.nodes(current)
        );
    }
}
