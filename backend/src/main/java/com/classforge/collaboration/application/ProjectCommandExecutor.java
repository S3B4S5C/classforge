package com.classforge.collaboration.application;

import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ProjectCommandExecutor {

    public ProjectDocument execute(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        require(
                current != null,
                "DOCUMENT_REQUIRED",
                "The current project document is required"
        );

        require(
                command != null,
                "COMMAND_REQUIRED",
                "The UML command is required"
        );

        require(
                command.commandId() != null,
                "COMMAND_ID_REQUIRED",
                "commandId is required"
        );

        require(
                command.type() != null,
                "COMMAND_TYPE_REQUIRED",
                "command type is required"
        );

        return switch (command.type()) {
            case CREATE_CLASS ->
                    createClass(
                            current,
                            command
                    );

            case RENAME_CLASS ->
                    renameClass(
                            current,
                            command
                    );

            case DELETE_CLASS ->
                    deleteClass(
                            current,
                            command
                    );

            case RESTORE_CLASS ->
                    restoreClass(
                            current,
                            command
                    );

            case ADD_ATTRIBUTE ->
                    addAttribute(
                            current,
                            command
                    );

            case UPDATE_ATTRIBUTE ->
                    updateAttribute(
                            current,
                            command
                    );

            case DELETE_ATTRIBUTE ->
                    deleteAttribute(
                            current,
                            command
                    );

            case CREATE_RELATIONSHIP ->
                    createRelationship(
                            current,
                            command
                    );

            case UPDATE_RELATIONSHIP ->
                    updateRelationship(
                            current,
                            command
                    );

            case DELETE_RELATIONSHIP ->
                    deleteRelationship(
                            current,
                            command
                    );

            case MOVE_CLASS ->
                    moveClass(
                            current,
                            command
                    );
        };
    }

    private ProjectDocument createClass(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UmlClass umlClass =
                command.umlClass();

        DiagramNodeLayout layout =
                command.layout();

        require(
                umlClass != null,
                "CLASS_REQUIRED",
                "CREATE_CLASS requires umlClass"
        );

        require(
                umlClass.id() != null,
                "CLASS_ID_REQUIRED",
                "CREATE_CLASS requires umlClass.id"
        );

        require(
                layout != null,
                "LAYOUT_REQUIRED",
                "CREATE_CLASS requires layout"
        );

        require(
                findClass(
                        current,
                        umlClass.id()
                ) == null,
                "DUPLICATE_CLASS_ID",
                "A class with this id already exists"
        );

        ArrayList<UmlClass> classes =
                classes(current);

        classes.add(umlClass);

        HashMap<UUID, DiagramNodeLayout> nodes =
                nodes(current);

        nodes.put(
                umlClass.id(),
                layout
        );

        return document(
                current,
                classes,
                relationships(current),
                nodes
        );
    }

    private ProjectDocument restoreClass(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UmlClass umlClass =
                command.umlClass();

        require(
                umlClass != null,
                "CLASS_REQUIRED",
                "RESTORE_CLASS requires umlClass"
        );

        require(
                umlClass.id() != null,
                "CLASS_ID_REQUIRED",
                "RESTORE_CLASS requires umlClass.id"
        );

        require(
                findClass(
                        current,
                        umlClass.id()
                ) == null,
                "DUPLICATE_CLASS_ID",
                "A class with this id already exists"
        );

        ArrayList<UmlClass> classes =
                classes(current);

        classes.add(
                umlClass
        );

        HashMap<UUID, DiagramNodeLayout> nodes =
                nodes(current);

        if (command.layout() != null) {
            nodes.put(
                    umlClass.id(),
                    command.layout()
            );
        }

        ArrayList<UmlRelationship> restoredRelationships =
                relationships(current);

        for (
                UmlRelationship relationship
                : command.safeRelationships()
        ) {
            require(
                    relationship != null
                            && relationship.id() != null,
                    "RELATIONSHIP_REQUIRED",
                    "RESTORE_CLASS received an invalid relationship"
            );

            require(
                    relationship.sourceClassId()
                            .equals(umlClass.id())
                            || relationship.targetClassId()
                            .equals(umlClass.id()),
                    "RESTORE_RELATIONSHIP_NOT_CONNECTED",
                    "RESTORE_CLASS can only restore relationships connected to the class"
            );

            boolean duplicateRelationshipId =
                    restoredRelationships
                            .stream()
                            .anyMatch(
                                    candidate ->
                                            candidate.id()
                                                    .equals(
                                                            relationship.id()
                                                    )
                            );

            require(
                    !duplicateRelationshipId,
                    "DUPLICATE_RELATIONSHIP_ID",
                    "A restored relationship id already exists"
            );

            restoredRelationships.add(
                    normalizeRelationship(
                            relationship
                    )
            );
        }

        return document(
                current,
                classes,
                restoredRelationships,
                nodes
        );
    }

    private ProjectDocument renameClass(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UUID classId =
                requireId(
                        command.classId(),
                        "CLASS_ID_REQUIRED",
                        "RENAME_CLASS requires classId"
                );

        requireText(
                command.name(),
                "CLASS_NAME_REQUIRED",
                "RENAME_CLASS requires name"
        );

        ArrayList<UmlClass> classes =
                classes(current);

        int index =
                classIndex(
                        classes,
                        classId
                );

        UmlClass existing =
                classes.get(index);

        classes.set(
                index,
                new UmlClass(
                        existing.id(),
                        command.name(),
                        existing.attributes()
                )
        );

        return document(
                current,
                classes,
                relationships(current),
                nodes(current)
        );
    }

    private ProjectDocument deleteClass(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UUID classId =
                requireId(
                        command.classId(),
                        "CLASS_ID_REQUIRED",
                        "DELETE_CLASS requires classId"
                );

        ArrayList<UmlClass> classes =
                classes(current);

        int classIndex =
                classIndex(
                        classes,
                        classId
                );

        classes.remove(classIndex);

        ArrayList<UmlRelationship> relationships =
                relationships(current);

        relationships.removeIf(
                relationship ->
                        relationship.sourceClassId()
                                .equals(classId)
                                || relationship.targetClassId()
                                .equals(classId)
        );

        HashMap<UUID, DiagramNodeLayout> nodes =
                nodes(current);

        nodes.remove(classId);

        return document(
                current,
                classes,
                relationships,
                nodes
        );
    }

    private ProjectDocument addAttribute(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UUID classId =
                requireId(
                        command.classId(),
                        "CLASS_ID_REQUIRED",
                        "ADD_ATTRIBUTE requires classId"
                );

        UmlAttribute attribute =
                command.attribute();

        require(
                attribute != null,
                "ATTRIBUTE_REQUIRED",
                "ADD_ATTRIBUTE requires attribute"
        );

        require(
                attribute.id() != null,
                "ATTRIBUTE_ID_REQUIRED",
                "ADD_ATTRIBUTE requires attribute.id"
        );

        ArrayList<UmlClass> classes =
                classes(current);

        int index =
                classIndex(
                        classes,
                        classId
                );

        UmlClass existing =
                classes.get(index);

        ArrayList<UmlAttribute> attributes =
                new ArrayList<>(
                        existing.attributes()
                );

        boolean duplicateId =
                current.umlModel()
                        .classes()
                        .stream()
                        .flatMap(
                                umlClass ->
                                        umlClass.attributes()
                                                .stream()
                        )
                        .anyMatch(
                                candidate ->
                                        candidate.id()
                                                .equals(
                                                        attribute.id()
                                                )
                        );

        require(
                !duplicateId,
                "DUPLICATE_ATTRIBUTE_ID",
                "An attribute with this id already exists"
        );

        attributes.add(attribute);

        classes.set(
                index,
                new UmlClass(
                        existing.id(),
                        existing.name(),
                        attributes
                )
        );

        return document(
                current,
                classes,
                relationships(current),
                nodes(current)
        );
    }

    private ProjectDocument updateAttribute(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UUID classId =
                requireId(
                        command.classId(),
                        "CLASS_ID_REQUIRED",
                        "UPDATE_ATTRIBUTE requires classId"
                );

        UmlAttribute attribute =
                command.attribute();

        require(
                attribute != null,
                "ATTRIBUTE_REQUIRED",
                "UPDATE_ATTRIBUTE requires attribute"
        );

        require(
                attribute.id() != null,
                "ATTRIBUTE_ID_REQUIRED",
                "UPDATE_ATTRIBUTE requires attribute.id"
        );

        ArrayList<UmlClass> classes =
                classes(current);

        int classIndex =
                classIndex(
                        classes,
                        classId
                );

        UmlClass existing =
                classes.get(classIndex);

        ArrayList<UmlAttribute> attributes =
                new ArrayList<>(
                        existing.attributes()
                );

        int attributeIndex =
                attributeIndex(
                        attributes,
                        attribute.id()
                );

        attributes.set(
                attributeIndex,
                attribute
        );

        classes.set(
                classIndex,
                new UmlClass(
                        existing.id(),
                        existing.name(),
                        attributes
                )
        );

        return document(
                current,
                classes,
                relationships(current),
                nodes(current)
        );
    }

    private ProjectDocument deleteAttribute(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UUID classId =
                requireId(
                        command.classId(),
                        "CLASS_ID_REQUIRED",
                        "DELETE_ATTRIBUTE requires classId"
                );

        UUID attributeId =
                requireId(
                        command.attributeId(),
                        "ATTRIBUTE_ID_REQUIRED",
                        "DELETE_ATTRIBUTE requires attributeId"
                );

        ArrayList<UmlClass> classes =
                classes(current);

        int classIndex =
                classIndex(
                        classes,
                        classId
                );

        UmlClass existing =
                classes.get(classIndex);

        ArrayList<UmlAttribute> attributes =
                new ArrayList<>(
                        existing.attributes()
                );

        int attributeIndex =
                attributeIndex(
                        attributes,
                        attributeId
                );

        attributes.remove(
                attributeIndex
        );

        classes.set(
                classIndex,
                new UmlClass(
                        existing.id(),
                        existing.name(),
                        attributes
                )
        );

        return document(
                current,
                classes,
                relationships(current),
                nodes(current)
        );
    }

    private ProjectDocument createRelationship(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UmlRelationship relationship =
                normalizeRelationship(
                        command.relationship()
                );

        require(
                relationship != null,
                "RELATIONSHIP_REQUIRED",
                "CREATE_RELATIONSHIP requires relationship"
        );

        require(
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

        require(
                !exists,
                "DUPLICATE_RELATIONSHIP_ID",
                "A relationship with this id already exists"
        );

        ArrayList<UmlRelationship> relationships =
                relationships(current);

        relationships.add(
                relationship
        );

        return document(
                current,
                classes(current),
                relationships,
                nodes(current)
        );
    }

    private ProjectDocument updateRelationship(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UmlRelationship relationship =
                normalizeRelationship(
                        command.relationship()
                );

        require(
                relationship != null,
                "RELATIONSHIP_REQUIRED",
                "UPDATE_RELATIONSHIP requires relationship"
        );

        require(
                relationship.id() != null,
                "RELATIONSHIP_ID_REQUIRED",
                "UPDATE_RELATIONSHIP requires relationship.id"
        );

        ArrayList<UmlRelationship> relationships =
                relationships(current);

        int index =
                relationshipIndex(
                        relationships,
                        relationship.id()
                );

        relationships.set(
                index,
                relationship
        );

        return document(
                current,
                classes(current),
                relationships,
                nodes(current)
        );
    }

    private ProjectDocument deleteRelationship(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UUID relationshipId =
                requireId(
                        command.relationshipId(),
                        "RELATIONSHIP_ID_REQUIRED",
                        "DELETE_RELATIONSHIP requires relationshipId"
                );

        ArrayList<UmlRelationship> relationships =
                relationships(current);

        int index =
                relationshipIndex(
                        relationships,
                        relationshipId
                );

        relationships.remove(index);

        return document(
                current,
                classes(current),
                relationships,
                nodes(current)
        );
    }

    private ProjectDocument moveClass(
            ProjectDocument current,
            UmlCommandPayload command
    ) {
        UUID classId =
                requireId(
                        command.classId(),
                        "CLASS_ID_REQUIRED",
                        "MOVE_CLASS requires classId"
                );

        require(
                findClass(
                        current,
                        classId
                ) != null,
                "CLASS_NOT_FOUND",
                "The class no longer exists"
        );

        DiagramNodeLayout layout =
                command.layout();

        require(
                layout != null,
                "LAYOUT_REQUIRED",
                "MOVE_CLASS requires layout"
        );

        HashMap<UUID, DiagramNodeLayout> nodes =
                nodes(current);

        nodes.put(
                classId,
                layout
        );

        return document(
                current,
                classes(current),
                relationships(current),
                nodes
        );
    }

    private UmlRelationship normalizeRelationship(
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

    private ProjectDocument document(
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

    private ArrayList<UmlClass> classes(
            ProjectDocument current
    ) {
        return new ArrayList<>(
                current.umlModel()
                        .classes()
        );
    }

    private ArrayList<UmlRelationship> relationships(
            ProjectDocument current
    ) {
        return new ArrayList<>(
                current.umlModel()
                        .relationships()
        );
    }

    private HashMap<UUID, DiagramNodeLayout> nodes(
            ProjectDocument current
    ) {
        return new HashMap<>(
                current.layout()
                        .nodes()
        );
    }

    private UmlClass findClass(
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

    private int classIndex(
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

    private int attributeIndex(
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

    private int relationshipIndex(
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

    private UUID requireId(
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

    private void requireText(
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

    private void require(
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