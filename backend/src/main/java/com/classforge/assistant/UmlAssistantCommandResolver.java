package com.classforge.assistant;

import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.domain.document.AssociationClassSupport;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class UmlAssistantCommandResolver {

    private final ProjectCommandExecutor executor;
    private final AssistantPlanNormalizer normalizer;

    public UmlAssistantCommandResolver(
            ProjectCommandExecutor executor,
            AssistantPlanNormalizer normalizer
    ) {
        this.executor =
                executor;

        this.normalizer =
                normalizer;
    }

    public UmlCommandPayload resolve(
            AssistantSemanticPlan plan,
            ProjectDocument document
    ) {
        AssistantSemanticPlan normalized =
                normalizer.normalize(
                        plan
                );

        ProjectDocument evolving =
                document;

        List<UmlCommandPayload> commands =
                new ArrayList<>();

        for (
                AssistantPlanAction action
                : normalized.actions()
        ) {
            List<UmlCommandPayload> resolved =
                    resolveAction(
                            action,
                            evolving
                    );

            for (
                    UmlCommandPayload command
                    : resolved
            ) {
                evolving =
                        executor.execute(
                                evolving,
                                command
                        );

                commands.add(
                        command
                );
            }
        }

        if (commands.isEmpty()) {
            throw new AssistantPlanningException(
                    "El plan no produjo comandos UML"
            );
        }

        return UmlCommandPayload.batch(
                "IA: "
                        + normalized.summary(),
                commands
        );
    }

    public ProjectDocument preview(
            ProjectDocument document,
            UmlCommandPayload batch
    ) {
        return executor.execute(
                document,
                batch
        );
    }

    private List<UmlCommandPayload> resolveAction(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        return switch (action.type()) {
            case CREATE_CLASS ->
                    createClass(
                            action,
                            document
                    );

            case CREATE_ASSOCIATION_CLASS ->
                    createAssociationClass(
                            action,
                            document
                    );

            case RENAME_CLASS ->
                    List.of(
                            renameClass(
                                    action,
                                    document
                            )
                    );

            case DELETE_CLASS ->
                    deleteClass(
                            action,
                            document
                    );

            case ADD_ATTRIBUTES ->
                    addAttributes(
                            action,
                            document
                    );

            case UPDATE_ATTRIBUTE ->
                    List.of(
                            updateAttribute(
                                    action,
                                    document
                            )
                    );

            case DELETE_ATTRIBUTE ->
                    List.of(
                            deleteAttribute(
                                    action,
                                    document
                            )
                    );

            case CREATE_RELATIONSHIP ->
                    List.of(
                            createRelationship(
                                    action,
                                    document
                            )
                    );

            case UPDATE_RELATIONSHIP ->
                    List.of(
                            updateRelationship(
                                    action,
                                    document
                            )
                    );

            case DELETE_RELATIONSHIP ->
                    List.of(
                            deleteRelationship(
                                    action,
                                    document
                            )
                    );
        };
    }

    private List<UmlCommandPayload> createClass(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        String name =
                required(
                        action.className(),
                        "CREATE_CLASS.className"
                );

        UUID classId =
                UUID.randomUUID();

        int index =
                document.umlModel()
                        .classes()
                        .size();

        int column =
                index % 3;

        int row =
                index / 3;

        List<UmlCommandPayload> commands =
                new ArrayList<>();

        commands.add(
                command(
                        UmlCommandType.CREATE_CLASS,
                        null,
                        null,
                        new UmlClass(
                                classId,
                                name,
                                List.of()
                        ),
                        new DiagramNodeLayout(
                                80 + column * 300,
                                80 + row * 220,
                                260,
                                160
                        ),
                        null,
                        null,
                        null,
                        null
                )
        );

        for (
                AssistantAttributePlan attribute
                : action.safeAttributes()
        ) {
            commands.add(
                    addAttributeCommand(
                            classId,
                            attribute
                    )
            );
        }

        return List.copyOf(
                commands
        );
    }

    private List<UmlCommandPayload> createAssociationClass(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        String name = required(action.className(), "CREATE_ASSOCIATION_CLASS.className");
        UmlClass source = findClass(document, action.sourceClassName());
        UmlClass target = findClass(document, action.targetClassName());
        if (source.id().equals(target.id())) {
            throw new AssistantPlanningException("CREATE_ASSOCIATION_CLASS requiere dos clases distintas.");
        }

        UmlRelationship original;
        if (action.relationshipId() != null) {
            original = document.umlModel().relationships().stream()
                    .filter(item -> item.id().equals(action.relationshipId()))
                    .findFirst()
                    .orElseThrow(() -> new AssistantPlanningException(
                            "La asociacion seleccionada para la clase de asociacion ya no existe."
                    ));
            boolean sameEndpoints = (original.sourceClassId().equals(source.id())
                    && original.targetClassId().equals(target.id()))
                    || (original.sourceClassId().equals(target.id())
                    && original.targetClassId().equals(source.id()));
            if (!sameEndpoints || original.type() == UmlRelationshipType.GENERALIZATION) {
                throw new AssistantPlanningException(
                        "La relacion seleccionada no puede convertirse en clase de asociacion."
                );
            }
            if (original.sourceClassId().equals(target.id())) {
                original = new UmlRelationship(
                        original.id(),
                        source.id(),
                        target.id(),
                        original.type(),
                        original.targetMultiplicity(),
                        original.sourceMultiplicity()
                );
            }
        } else {
            UmlRelationshipType type = action.relationshipType() == null
                    ? UmlRelationshipType.ASSOCIATION
                    : action.relationshipType();
            if (type == UmlRelationshipType.GENERALIZATION) {
                throw new AssistantPlanningException("Una generalizacion no puede ser AssociationClass.");
            }
            original = relationship(
                    UUID.randomUUID(),
                    source.id(),
                    target.id(),
                    type,
                    action
            );
        }

        UUID classId = UUID.randomUUID();
        int index = document.umlModel().classes().size();
        int column = index % 3;
        int row = index / 3;

        UmlAttribute markerAttribute = new UmlAttribute(
                UUID.randomUUID(),
                "id",
                com.classforge.project.domain.document.UmlDataType.UUID,
                AssociationClassSupport.marker(original),
                com.classforge.project.domain.document.UmlVisibility.PRIVATE,
                false,
                true
        );

        List<UmlCommandPayload> commands = new ArrayList<>();
        if (action.relationshipId() != null) {
            commands.add(command(
                    UmlCommandType.DELETE_RELATIONSHIP,
                    null, null, null, null, null, null, null, original.id()
            ));
        }
        commands.add(command(
                UmlCommandType.CREATE_CLASS,
                null,
                null,
                new UmlClass(classId, name, List.of(markerAttribute)),
                new DiagramNodeLayout(
                        80 + column * 300,
                        80 + row * 220,
                        260,
                        160
                ),
                null, null, null, null
        ));

        for (AssistantAttributePlan attribute : action.safeAttributes()) {
            if ("id".equalsIgnoreCase(attribute.name())) {
                continue;
            }
            commands.add(addAttributeCommand(classId, attribute));
        }

        commands.add(command(
                UmlCommandType.CREATE_RELATIONSHIP,
                null, null, null, null, null, null,
                new UmlRelationship(
                        UUID.randomUUID(),
                        source.id(),
                        classId,
                        UmlRelationshipType.ASSOCIATION,
                        Multiplicity.one(),
                        original.targetMultiplicity() == null
                                ? new Multiplicity(0, null)
                                : original.targetMultiplicity()
                ),
                null
        ));
        commands.add(command(
                UmlCommandType.CREATE_RELATIONSHIP,
                null, null, null, null, null, null,
                new UmlRelationship(
                        UUID.randomUUID(),
                        target.id(),
                        classId,
                        UmlRelationshipType.ASSOCIATION,
                        Multiplicity.one(),
                        original.sourceMultiplicity() == null
                                ? new Multiplicity(0, null)
                                : original.sourceMultiplicity()
                ),
                null
        ));
        return List.copyOf(commands);
    }

    private UmlCommandPayload renameClass(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        UmlClass umlClass =
                findClass(
                        document,
                        action.className()
                );

        return command(
                UmlCommandType.RENAME_CLASS,
                umlClass.id(),
                required(
                        action.newName(),
                        "RENAME_CLASS.newName"
                ),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private List<UmlCommandPayload> deleteClass(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        UmlClass umlClass =
                findClass(
                        document,
                        action.className()
                );

        List<UmlCommandPayload> commands = new ArrayList<>();
        commands.add(command(
                UmlCommandType.DELETE_CLASS,
                umlClass.id(),
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));

        AssociationClassSupport.metadata(umlClass).ifPresent(metadata -> {
            UmlRelationship original = metadata.relationship();
            boolean sourceExists = document.umlModel().classes().stream()
                    .anyMatch(item -> item.id().equals(original.sourceClassId()));
            boolean targetExists = document.umlModel().classes().stream()
                    .anyMatch(item -> item.id().equals(original.targetClassId()));
            if (sourceExists && targetExists) {
                commands.add(command(
                        UmlCommandType.CREATE_RELATIONSHIP,
                        null, null, null, null, null, null, original, null
                ));
            }
        });

        return List.copyOf(commands);
    }

    private List<UmlCommandPayload> addAttributes(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        UmlClass umlClass =
                findClass(
                        document,
                        action.className()
                );

        if (action.safeAttributes().isEmpty()) {
            throw new AssistantPlanningException(
                    "ADD_ATTRIBUTES no contiene atributos"
            );
        }

        return action.safeAttributes()
                .stream()
                .map(
                        attribute ->
                                addAttributeCommand(
                                        umlClass.id(),
                                        attribute
                                )
                )
                .toList();
    }

    private UmlCommandPayload addAttributeCommand(
            UUID classId,
            AssistantAttributePlan attribute
    ) {
        UmlAttribute umlAttribute =
                new UmlAttribute(
                        UUID.randomUUID(),
                        required(
                                attribute.name(),
                                "attribute.name"
                        ),
                        attribute.dataType(),
                        attribute.customTypeName(),
                        attribute.visibility(),
                        attribute.nullable(),
                        attribute.identifier()
                );

        return command(
                UmlCommandType.ADD_ATTRIBUTE,
                classId,
                null,
                null,
                null,
                umlAttribute,
                null,
                null,
                null
        );
    }

    private UmlCommandPayload updateAttribute(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        UmlClass umlClass =
                findClass(
                        document,
                        action.className()
                );

        UmlAttribute current =
                findAttribute(
                        umlClass,
                        action.attributeName()
                );

        UmlAttribute updated =
                new UmlAttribute(
                        current.id(),
                        optional(
                                action.newAttributeName(),
                                current.name()
                        ),
                        action.dataType() == null
                                ? current.dataType()
                                : action.dataType(),
                        action.customTypeName() == null
                                ? current.customTypeName()
                                : action.customTypeName(),
                        action.visibility() == null
                                ? current.visibility()
                                : action.visibility(),
                        action.nullable() == null
                                ? current.nullable()
                                : action.nullable(),
                        action.identifier() == null
                                ? current.identifier()
                                : action.identifier()
                );

        return command(
                UmlCommandType.UPDATE_ATTRIBUTE,
                umlClass.id(),
                null,
                null,
                null,
                updated,
                null,
                null,
                null
        );
    }

    private UmlCommandPayload deleteAttribute(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        UmlClass umlClass =
                findClass(
                        document,
                        action.className()
                );

        UmlAttribute attribute =
                findAttribute(
                        umlClass,
                        action.attributeName()
                );

        return command(
                UmlCommandType.DELETE_ATTRIBUTE,
                umlClass.id(),
                null,
                null,
                null,
                null,
                attribute.id(),
                null,
                null
        );
    }

    private UmlCommandPayload createRelationship(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        UmlClass source =
                findClass(
                        document,
                        action.sourceClassName()
                );

        UmlClass target =
                findClass(
                        document,
                        action.targetClassName()
                );

        UmlRelationshipType type =
                action.relationshipType() == null
                        ? UmlRelationshipType.ASSOCIATION
                        : action.relationshipType();

        return command(
                UmlCommandType.CREATE_RELATIONSHIP,
                null,
                null,
                null,
                null,
                null,
                null,
                relationship(
                        UUID.randomUUID(),
                        source.id(),
                        target.id(),
                        type,
                        action
                ),
                null
        );
    }

    private UmlCommandPayload updateRelationship(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        UmlClass source =
                findClass(
                        document,
                        action.sourceClassName()
                );

        UmlClass target =
                findClass(
                        document,
                        action.targetClassName()
                );

        UmlRelationship current =
                uniqueRelationship(
                        document,
                        source.id(),
                        target.id()
                );

        UmlRelationshipType type =
                action.relationshipType() == null
                        ? current.type()
                        : action.relationshipType();

        return command(
                UmlCommandType.UPDATE_RELATIONSHIP,
                null,
                null,
                null,
                null,
                null,
                null,
                updatedRelationship(
                        current,
                        source.id(),
                        target.id(),
                        type,
                        action
                ),
                null
        );
    }

    private UmlCommandPayload deleteRelationship(
            AssistantPlanAction action,
            ProjectDocument document
    ) {
        UmlClass source =
                findClass(
                        document,
                        action.sourceClassName()
                );

        UmlClass target =
                findClass(
                        document,
                        action.targetClassName()
                );

        UmlRelationship relationship =
                uniqueRelationship(
                        document,
                        source.id(),
                        target.id()
                );

        return command(
                UmlCommandType.DELETE_RELATIONSHIP,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                relationship.id()
        );
    }

    private UmlRelationship updatedRelationship(
            UmlRelationship current,
            UUID sourceId,
            UUID targetId,
            UmlRelationshipType type,
            AssistantPlanAction action
    ) {
        if (type == UmlRelationshipType.GENERALIZATION) {
            return new UmlRelationship(
                    current.id(),
                    sourceId,
                    targetId,
                    type,
                    null,
                    null
            );
        }

        return new UmlRelationship(
                current.id(),
                sourceId,
                targetId,
                type,
                updatedMultiplicity(
                        current.sourceMultiplicity(),
                        action.sourceLower(),
                        action.sourceUpper()
                ),
                updatedMultiplicity(
                        current.targetMultiplicity(),
                        action.targetLower(),
                        action.targetUpper()
                )
        );
    }

    private Multiplicity updatedMultiplicity(
            Multiplicity current,
            Integer lower,
            Integer upper
    ) {
        if (lower == null && upper == null) {
            return current == null
                    ? Multiplicity.one()
                    : current;
        }

        int normalizedLower;

        if (lower != null) {
            normalizedLower = lower;
        } else if (upper != null && upper < 0) {
            normalizedLower = 0;
        } else if (current != null) {
            normalizedLower = current.lower();
        } else {
            normalizedLower = 1;
        }

        Integer normalizedUpper;

        if (upper == null) {
            normalizedUpper = current == null
                    ? normalizedLower
                    : current.upper();
        } else if (upper < 0) {
            normalizedUpper = null;
        } else {
            normalizedUpper = upper;
        }

        return new Multiplicity(
                normalizedLower,
                normalizedUpper
        );
    }

    private UmlRelationship relationship(
            UUID id,
            UUID sourceId,
            UUID targetId,
            UmlRelationshipType type,
            AssistantPlanAction action
    ) {
        if (
                type
                        == UmlRelationshipType.GENERALIZATION
        ) {
            return new UmlRelationship(
                    id,
                    sourceId,
                    targetId,
                    type,
                    null,
                    null
            );
        }

        return new UmlRelationship(
                id,
                sourceId,
                targetId,
                type,
                multiplicity(
                        action.sourceLower(),
                        action.sourceUpper()
                ),
                multiplicity(
                        action.targetLower(),
                        action.targetUpper()
                )
        );
    }

    private Multiplicity multiplicity(
            Integer lower,
            Integer upper
    ) {
        int normalizedLower =
                lower == null
                        ? (upper != null && upper < 0 ? 0 : 1)
                        : lower;

        Integer normalizedUpper;

        if (upper == null) {
            normalizedUpper =
                    1;
        } else if (upper < 0) {
            normalizedUpper =
                    null;
        } else {
            normalizedUpper =
                    upper;
        }

        return new Multiplicity(
                normalizedLower,
                normalizedUpper
        );
    }

    private UmlClass findClass(
            ProjectDocument document,
            String name
    ) {
        String wanted =
                required(
                        name,
                        "className"
                );

        List<UmlClass> matches =
                document.umlModel()
                        .classes()
                        .stream()
                        .filter(
                                umlClass ->
                                        umlClass.name()
                                                .equalsIgnoreCase(
                                                        wanted
                                                )
                        )
                        .toList();

        if (matches.size() != 1) {
            throw new AssistantPlanningException(
                    matches.isEmpty()
                            ? "No existe la clase '"
                                    + wanted
                                    + "'"
                            : "La clase '"
                                    + wanted
                                    + "' es ambigua"
            );
        }

        return matches.getFirst();
    }

    private UmlAttribute findAttribute(
            UmlClass umlClass,
            String name
    ) {
        String wanted =
                required(
                        name,
                        "attributeName"
                );

        List<UmlAttribute> matches =
                umlClass.attributes()
                        .stream()
                        .filter(
                                attribute ->
                                        attribute.name()
                                                .equalsIgnoreCase(
                                                        wanted
                                                )
                        )
                        .toList();

        if (matches.size() != 1) {
            throw new AssistantPlanningException(
                    matches.isEmpty()
                            ? "No existe el atributo '"
                                    + wanted
                                    + "' en "
                                    + umlClass.name()
                            : "El atributo '"
                                    + wanted
                                    + "' es ambiguo"
            );
        }

        return matches.getFirst();
    }

    private UmlRelationship uniqueRelationship(
            ProjectDocument document,
            UUID sourceId,
            UUID targetId
    ) {
        List<UmlRelationship> directMatches =
                document.umlModel()
                        .relationships()
                        .stream()
                        .filter(
                                relationship ->
                                        relationship.sourceClassId()
                                                .equals(sourceId)
                                                && relationship.targetClassId()
                                                .equals(targetId)
                        )
                        .toList();

        if (directMatches.size() > 1) {
            throw new AssistantPlanningException(
                    "Hay varias relaciones entre esas clases; la orden es ambigua"
            );
        }

        if (directMatches.size() == 1) {
            return directMatches.getFirst();
        }

        List<UmlRelationship> reverseMatches =
                document.umlModel()
                        .relationships()
                        .stream()
                        .filter(
                                relationship ->
                                        relationship.sourceClassId()
                                                .equals(targetId)
                                                && relationship.targetClassId()
                                                .equals(sourceId)
                        )
                        .toList();

        if (reverseMatches.isEmpty()) {
            throw new AssistantPlanningException(
                    "No existe una relacion entre esas clases"
            );
        }

        if (reverseMatches.size() > 1) {
            throw new AssistantPlanningException(
                    "Hay varias relaciones entre esas clases; la orden es ambigua"
            );
        }

        UmlRelationship reverse =
                reverseMatches.getFirst();

        if (
                reverse.type()
                        != UmlRelationshipType.ASSOCIATION
        ) {
            throw new AssistantPlanningException(
                    "La relacion existe en el sentido contrario y su tipo "
                            + reverse.type().name()
                            + " tiene direccion UML. Indica la direccion real de la relacion."
            );
        }

        return reverse;
    }

    private String required(
            String value,
            String field
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            throw new AssistantPlanningException(
                    "Falta "
                            + field
            );
        }

        return value.trim();
    }

    private String optional(
            String value,
            String fallback
    ) {
        return value == null
                || value.isBlank()
                ? fallback
                : value.trim();
    }

    private UmlCommandPayload command(
            UmlCommandType type,
            UUID classId,
            String name,
            UmlClass umlClass,
            DiagramNodeLayout layout,
            UmlAttribute attribute,
            UUID attributeId,
            UmlRelationship relationship,
            UUID relationshipId
    ) {
        return new UmlCommandPayload(
                UUID.randomUUID(),
                Instant.now(),
                type,
                classId,
                name,
                umlClass,
                layout,
                attribute,
                attributeId,
                relationship,
                relationshipId
        );
    }
}