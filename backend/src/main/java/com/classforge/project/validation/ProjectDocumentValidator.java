package com.classforge.project.validation;

import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class ProjectDocumentValidator {

    private static final Pattern CODE_NAME =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private static final int MAX_CLASS_NAME = 80;
    private static final int MAX_ATTRIBUTE_NAME = 80;
    private static final int MAX_CUSTOM_TYPE_NAME = 120;

    public void validate(ProjectDocument document) {
        ProjectDocumentValidationReport report = analyze(document);

        if (report.valid()) {
            return;
        }

        List<ValidationViolation> violations =
                report.diagnostics()
                        .stream()
                        .filter(
                                diagnostic ->
                                        diagnostic.severity()
                                                == ValidationSeverity.ERROR
                        )
                        .map(
                                diagnostic ->
                                        new ValidationViolation(
                                                diagnostic.field(),
                                                diagnostic.code(),
                                                diagnostic.message()
                                        )
                        )
                        .toList();

        throw new ProjectDocumentValidationException(violations);
    }

    public ProjectDocumentValidationReport analyze(
            ProjectDocument document
    ) {
        ValidationCollector collector =
                new ValidationCollector();

        if (document == null) {
            collector.error(
                    "document",
                    "DOCUMENT_REQUIRED",
                    null,
                    "El documento del proyecto es obligatorio."
            );

            return collector.report();
        }

        if (!ProjectDocument.CURRENT_SCHEMA_VERSION.equals(
                document.schemaVersion()
        )) {
            collector.error(
                    "document.schemaVersion",
                    "UNSUPPORTED_SCHEMA_VERSION",
                    null,
                    "La version del documento no es compatible con esta version de ClassForge."
            );
        }

        List<UmlClass> classes =
                document.umlModel().classes();

        List<UmlRelationship> relationships =
                document.umlModel().relationships();

        if (classes.isEmpty()) {
            collector.warning(
                    "document.umlModel.classes",
                    "MODEL_EMPTY",
                    null,
                    "El modelo no contiene clases todavia."
            );
        }

        validateClasses(
                classes,
                collector
        );

        validateRelationships(
                relationships,
                classes,
                collector
        );

        validateGeneralizationCycles(
                relationships,
                classes,
                collector
        );

        validateLayout(
                document.layout().nodes(),
                classes,
                collector
        );

        validateModelQuality(
                classes,
                relationships,
                collector
        );

        return collector.report();
    }

    private void validateClasses(
            List<UmlClass> classes,
            ValidationCollector collector
    ) {
        Set<UUID> classIds = new HashSet<>();
        Set<String> classNames = new HashSet<>();
        Set<UUID> attributeIds = new HashSet<>();

        for (
                int classIndex = 0;
                classIndex < classes.size();
                classIndex++
        ) {
            UmlClass umlClass =
                    classes.get(classIndex);

            String classPath =
                    "document.umlModel.classes["
                            + classIndex
                            + "]";

            if (umlClass == null) {
                collector.error(
                        classPath,
                        "CLASS_REQUIRED",
                        null,
                        "La clase no puede ser nula."
                );
                continue;
            }

            UUID classId = umlClass.id();

            if (classId == null) {
                collector.error(
                        classPath + ".id",
                        "CLASS_ID_REQUIRED",
                        null,
                        "La clase necesita un identificador."
                );
            } else if (!classIds.add(classId)) {
                collector.error(
                        classPath + ".id",
                        "DUPLICATE_CLASS_ID",
                        classId,
                        "El identificador de la clase esta repetido."
                );
            }

            validateCodeName(
                    umlClass.name(),
                    classPath + ".name",
                    "CLASS_NAME",
                    "clase",
                    MAX_CLASS_NAME,
                    classId,
                    collector
            );

            if (hasText(umlClass.name())) {
                String normalized =
                        umlClass.name()
                                .toLowerCase(Locale.ROOT);

                if (!classNames.add(normalized)) {
                    collector.error(
                            classPath + ".name",
                            "DUPLICATE_CLASS_NAME",
                            classId,
                            "Ya existe otra clase con el nombre '"
                                    + umlClass.name()
                                    + "'."
                    );
                }
            }

            validateAttributes(
                    umlClass,
                    classPath,
                    attributeIds,
                    collector
            );
        }
    }

    private void validateAttributes(
            UmlClass umlClass,
            String classPath,
            Set<UUID> attributeIds,
            ValidationCollector collector
    ) {
        Set<String> attributeNames =
                new HashSet<>();

        for (
                int attributeIndex = 0;
                attributeIndex
                        < umlClass.attributes().size();
                attributeIndex++
        ) {
            UmlAttribute attribute =
                    umlClass.attributes()
                            .get(attributeIndex);

            String attributePath =
                    classPath
                            + ".attributes["
                            + attributeIndex
                            + "]";

            if (attribute == null) {
                collector.error(
                        attributePath,
                        "ATTRIBUTE_REQUIRED",
                        umlClass.id(),
                        "El atributo no puede ser nulo."
                );
                continue;
            }

            if (attribute.id() == null) {
                collector.error(
                        attributePath + ".id",
                        "ATTRIBUTE_ID_REQUIRED",
                        umlClass.id(),
                        "El atributo necesita un identificador."
                );
            } else if (!attributeIds.add(attribute.id())) {
                collector.error(
                        attributePath + ".id",
                        "DUPLICATE_ATTRIBUTE_ID",
                        umlClass.id(),
                        "El identificador del atributo esta repetido."
                );
            }

            validateCodeName(
                    attribute.name(),
                    attributePath + ".name",
                    "ATTRIBUTE_NAME",
                    "atributo",
                    MAX_ATTRIBUTE_NAME,
                    umlClass.id(),
                    collector
            );

            if (hasText(attribute.name())) {
                String normalized =
                        attribute.name()
                                .toLowerCase(Locale.ROOT);

                if (!attributeNames.add(normalized)) {
                    collector.error(
                            attributePath + ".name",
                            "DUPLICATE_ATTRIBUTE_NAME",
                            umlClass.id(),
                            "La clase '"
                                    + umlClass.name()
                                    + "' ya contiene un atributo llamado '"
                                    + attribute.name()
                                    + "'."
                    );
                }
            }

            if (attribute.dataType() == null) {
                collector.error(
                        attributePath + ".dataType",
                        "ATTRIBUTE_TYPE_REQUIRED",
                        umlClass.id(),
                        "Selecciona un tipo para el atributo."
                );
            }

            if (attribute.visibility() == null) {
                collector.error(
                        attributePath + ".visibility",
                        "ATTRIBUTE_VISIBILITY_REQUIRED",
                        umlClass.id(),
                        "Selecciona una visibilidad UML."
                );
            }

            if (attribute.dataType() == UmlDataType.CUSTOM) {
                validateCodeName(
                        attribute.customTypeName(),
                        attributePath + ".customTypeName",
                        "CUSTOM_TYPE_NAME",
                        "tipo personalizado",
                        MAX_CUSTOM_TYPE_NAME,
                        umlClass.id(),
                        collector
                );
            }

            if (
                    attribute.identifier()
                            && attribute.nullable()
            ) {
                collector.error(
                        attributePath + ".nullable",
                        "IDENTIFIER_CANNOT_BE_NULLABLE",
                        umlClass.id(),
                        "Un atributo identificador no puede ser nullable."
                );
            }
        }
    }

    private void validateRelationships(
            List<UmlRelationship> relationships,
            List<UmlClass> classes,
            ValidationCollector collector
    ) {
        Set<UUID> classIds = new HashSet<>();
        Set<UUID> relationshipIds =
                new HashSet<>();

        for (UmlClass umlClass : classes) {
            if (
                    umlClass != null
                            && umlClass.id() != null
            ) {
                classIds.add(umlClass.id());
            }
        }

        for (
                int relationshipIndex = 0;
                relationshipIndex
                        < relationships.size();
                relationshipIndex++
        ) {
            UmlRelationship relationship =
                    relationships.get(
                            relationshipIndex
                    );

            String path =
                    "document.umlModel.relationships["
                            + relationshipIndex
                            + "]";

            if (relationship == null) {
                collector.error(
                        path,
                        "RELATIONSHIP_REQUIRED",
                        null,
                        "La relacion no puede ser nula."
                );
                continue;
            }

            UUID relationshipId =
                    relationship.id();

            if (relationshipId == null) {
                collector.error(
                        path + ".id",
                        "RELATIONSHIP_ID_REQUIRED",
                        null,
                        "La relacion necesita un identificador."
                );
            } else if (
                    !relationshipIds.add(
                            relationshipId
                    )
            ) {
                collector.error(
                        path + ".id",
                        "DUPLICATE_RELATIONSHIP_ID",
                        relationshipId,
                        "El identificador de la relacion esta repetido."
                );
            }

            validateClassReference(
                    relationship.sourceClassId(),
                    path + ".sourceClassId",
                    classIds,
                    relationshipId,
                    collector
            );

            validateClassReference(
                    relationship.targetClassId(),
                    path + ".targetClassId",
                    classIds,
                    relationshipId,
                    collector
            );

            if (relationship.type() == null) {
                collector.error(
                        path + ".type",
                        "RELATIONSHIP_TYPE_REQUIRED",
                        relationshipId,
                        "Selecciona un tipo de relacion UML."
                );
            }

            if (
                    relationship.type()
                            == UmlRelationshipType.GENERALIZATION
                            && relationship.sourceClassId()
                            != null
                            && relationship.sourceClassId()
                            .equals(
                                    relationship.targetClassId()
                            )
            ) {
                collector.error(
                        path + ".targetClassId",
                        "GENERALIZATION_SELF_REFERENCE",
                        relationshipId,
                        "Una clase no puede generalizarse a si misma."
                );
            }

            if (
                    relationship.type()
                            != UmlRelationshipType.GENERALIZATION
            ) {
                validateMultiplicity(
                        relationship.sourceMultiplicity(),
                        path + ".sourceMultiplicity",
                        relationshipId,
                        collector
                );

                validateMultiplicity(
                        relationship.targetMultiplicity(),
                        path + ".targetMultiplicity",
                        relationshipId,
                        collector
                );
            }
        }
    }

    private void validateGeneralizationCycles(
            List<UmlRelationship> relationships,
            List<UmlClass> classes,
            ValidationCollector collector
    ) {
        Set<UUID> knownClassIds =
                new HashSet<>();

        for (UmlClass umlClass : classes) {
            if (
                    umlClass != null
                            && umlClass.id() != null
            ) {
                knownClassIds.add(
                        umlClass.id()
                );
            }
        }

        Map<UUID, List<UUID>> graph =
                new HashMap<>();

        for (
                UmlRelationship relationship
                : relationships
        ) {
            if (
                    relationship == null
                            || relationship.type()
                            != UmlRelationshipType.GENERALIZATION
                            || relationship.sourceClassId()
                            == null
                            || relationship.targetClassId()
                            == null
                            || relationship.sourceClassId()
                            .equals(
                                    relationship.targetClassId()
                            )
                            || !knownClassIds.contains(
                                    relationship.sourceClassId()
                            )
                            || !knownClassIds.contains(
                                    relationship.targetClassId()
                            )
            ) {
                continue;
            }

            graph.computeIfAbsent(
                    relationship.sourceClassId(),
                    ignored -> new ArrayList<>()
            ).add(
                    relationship.targetClassId()
            );
        }

        Map<UUID, Integer> state =
                new HashMap<>();

        for (UUID classId : knownClassIds) {
            if (
                    hasGeneralizationCycle(
                            classId,
                            graph,
                            state
                    )
            ) {
                collector.error(
                        "document.umlModel.relationships",
                        "GENERALIZATION_CYCLE",
                        classId,
                        "La jerarquia de generalizacion contiene un ciclo. Una clase no puede terminar heredando de si misma."
                );
                return;
            }
        }
    }

    private boolean hasGeneralizationCycle(
            UUID classId,
            Map<UUID, List<UUID>> graph,
            Map<UUID, Integer> state
    ) {
        int currentState =
                state.getOrDefault(
                        classId,
                        0
                );

        if (currentState == 1) {
            return true;
        }

        if (currentState == 2) {
            return false;
        }

        state.put(classId, 1);

        for (
                UUID parentId
                : graph.getOrDefault(
                        classId,
                        List.of()
                )
        ) {
            if (
                    hasGeneralizationCycle(
                            parentId,
                            graph,
                            state
                    )
            ) {
                return true;
            }
        }

        state.put(classId, 2);

        return false;
    }

    private void validateClassReference(
            UUID classId,
            String field,
            Set<UUID> knownClassIds,
            UUID relationshipId,
            ValidationCollector collector
    ) {
        if (classId == null) {
            collector.error(
                    field,
                    "RELATIONSHIP_CLASS_REQUIRED",
                    relationshipId,
                    "La relacion debe apuntar a una clase."
            );
            return;
        }

        if (!knownClassIds.contains(classId)) {
            collector.error(
                    field,
                    "RELATIONSHIP_CLASS_NOT_FOUND",
                    relationshipId,
                    "La relacion referencia una clase que no existe."
            );
        }
    }

    private void validateMultiplicity(
            Multiplicity multiplicity,
            String path,
            UUID relationshipId,
            ValidationCollector collector
    ) {
        if (multiplicity == null) {
            collector.error(
                    path,
                    "MULTIPLICITY_REQUIRED",
                    relationshipId,
                    "La multiplicidad es obligatoria."
            );
            return;
        }

        if (multiplicity.lower() < 0) {
            collector.error(
                    path + ".lower",
                    "MULTIPLICITY_LOWER_INVALID",
                    relationshipId,
                    "El limite inferior no puede ser negativo."
            );
        }

        if (
                multiplicity.upper() != null
                        && multiplicity.upper()
                        < multiplicity.lower()
        ) {
            collector.error(
                    path + ".upper",
                    "MULTIPLICITY_RANGE_INVALID",
                    relationshipId,
                    "El limite superior no puede ser menor al inferior."
            );
        }
    }

    private void validateLayout(
            Map<UUID, DiagramNodeLayout> nodes,
            List<UmlClass> classes,
            ValidationCollector collector
    ) {
        Set<UUID> classIds =
                new HashSet<>();

        for (UmlClass umlClass : classes) {
            if (
                    umlClass != null
                            && umlClass.id() != null
            ) {
                classIds.add(
                        umlClass.id()
                );
            }
        }

        for (
                Map.Entry<
                        UUID,
                        DiagramNodeLayout
                        > entry
                : nodes.entrySet()
        ) {
            UUID classId =
                    entry.getKey();

            DiagramNodeLayout layout =
                    entry.getValue();

            String path =
                    "document.layout.nodes["
                            + classId
                            + "]";

            if (
                    classId == null
                            || !classIds.contains(
                                    classId
                            )
            ) {
                collector.error(
                        path,
                        "LAYOUT_CLASS_NOT_FOUND",
                        classId,
                        "El layout contiene una posicion para una clase inexistente."
                );
            }

            if (layout == null) {
                collector.error(
                        path,
                        "LAYOUT_REQUIRED",
                        classId,
                        "La informacion visual de la clase no puede ser nula."
                );
                continue;
            }

            if (
                    !Double.isFinite(layout.x())
                            || !Double.isFinite(
                                    layout.y()
                            )
            ) {
                collector.error(
                        path,
                        "LAYOUT_POSITION_INVALID",
                        classId,
                        "La posicion de la clase debe contener valores finitos."
                );
            }

            if (
                    !Double.isFinite(
                            layout.width()
                    )
                            || !Double.isFinite(
                                    layout.height()
                            )
                            || layout.width() <= 0
                            || layout.height() <= 0
            ) {
                collector.error(
                        path,
                        "LAYOUT_SIZE_INVALID",
                        classId,
                        "El ancho y alto de la clase deben ser mayores que cero."
                );
            }
        }
    }

    private void validateModelQuality(
            List<UmlClass> classes,
            List<UmlRelationship> relationships,
            ValidationCollector collector
    ) {
        if (classes.isEmpty()) {
            return;
        }

        Set<UUID> connectedClassIds =
                new HashSet<>();

        for (
                UmlRelationship relationship
                : relationships
        ) {
            if (relationship == null) {
                continue;
            }

            if (
                    relationship.sourceClassId()
                            != null
            ) {
                connectedClassIds.add(
                        relationship.sourceClassId()
                );
            }

            if (
                    relationship.targetClassId()
                            != null
            ) {
                connectedClassIds.add(
                        relationship.targetClassId()
                );
            }

            if (
                    relationship.type()
                            == UmlRelationshipType.ASSOCIATION
                            && relationship.sourceClassId()
                            != null
                            && relationship.sourceClassId()
                            .equals(
                                    relationship.targetClassId()
                            )
            ) {
                collector.warning(
                        relationshipPath(
                                relationships,
                                relationship
                        ),
                        "REFLEXIVE_ASSOCIATION",
                        relationship.id(),
                        "La asociacion es reflexiva. Es valida, pero conviene confirmar que la clase realmente debe relacionarse consigo misma."
                );
            }
        }

        for (
                int classIndex = 0;
                classIndex < classes.size();
                classIndex++
        ) {
            UmlClass umlClass =
                    classes.get(classIndex);

            if (umlClass == null) {
                continue;
            }

            String classPath =
                    "document.umlModel.classes["
                            + classIndex
                            + "]";

            if (umlClass.attributes().isEmpty()) {
                collector.warning(
                        classPath + ".attributes",
                        "CLASS_WITHOUT_ATTRIBUTES",
                        umlClass.id(),
                        "La clase '"
                                + safeName(umlClass.name())
                                + "' no tiene atributos."
                );
            }

            boolean hasIdentifier =
                    umlClass.attributes()
                            .stream()
                            .filter(
                                    attribute ->
                                            attribute != null
                            )
                            .anyMatch(
                                    UmlAttribute::identifier
                            );

            if (!hasIdentifier) {
                collector.warning(
                        classPath + ".attributes",
                        "CLASS_WITHOUT_IDENTIFIER",
                        umlClass.id(),
                        "La clase '"
                                + safeName(umlClass.name())
                                + "' no tiene un atributo marcado como identificador. Esto puede dificultar la futura generacion relacional/JPA."
                );
            }

            if (
                    umlClass.id() != null
                            && !connectedClassIds.contains(
                                    umlClass.id()
                            )
            ) {
                collector.warning(
                        classPath,
                        "ISOLATED_CLASS",
                        umlClass.id(),
                        "La clase '"
                                + safeName(umlClass.name())
                                + "' esta aislada y no participa en ninguna relacion."
                );
            }
        }
    }

    private String relationshipPath(
            List<UmlRelationship> relationships,
            UmlRelationship relationship
    ) {
        int index =
                relationships.indexOf(
                        relationship
                );

        return "document.umlModel.relationships["
                + Math.max(index, 0)
                + "]";
    }

    private void validateCodeName(
            String value,
            String field,
            String codePrefix,
            String label,
            int maxLength,
            UUID elementId,
            ValidationCollector collector
    ) {
        if (!hasText(value)) {
            collector.error(
                    field,
                    codePrefix + "_REQUIRED",
                    elementId,
                    "El nombre del "
                            + label
                            + " es obligatorio."
            );
            return;
        }

        if (value.length() > maxLength) {
            collector.error(
                    field,
                    codePrefix + "_TOO_LONG",
                    elementId,
                    "El nombre del "
                            + label
                            + " no puede superar "
                            + maxLength
                            + " caracteres."
            );
        }

        if (!CODE_NAME.matcher(value).matches()) {
            collector.error(
                    field,
                    codePrefix + "_INVALID",
                    elementId,
                    "Usa un nombre compatible con codigo: letras, numeros y '_', sin espacios, y no empieces con un numero."
            );
        }
    }

    private boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }

    private String safeName(
            String value
    ) {
        return hasText(value)
                ? value
                : "(sin nombre)";
    }

    private static final class ValidationCollector {

        private final ArrayList<
                ValidationDiagnostic
                > diagnostics =
                new ArrayList<>();

        void error(
                String field,
                String code,
                UUID elementId,
                String message
        ) {
            add(
                    ValidationSeverity.ERROR,
                    field,
                    code,
                    elementId,
                    message
            );
        }

        void warning(
                String field,
                String code,
                UUID elementId,
                String message
        ) {
            add(
                    ValidationSeverity.WARNING,
                    field,
                    code,
                    elementId,
                    message
            );
        }

        void info(
                String field,
                String code,
                UUID elementId,
                String message
        ) {
            add(
                    ValidationSeverity.INFO,
                    field,
                    code,
                    elementId,
                    message
            );
        }

        private void add(
                ValidationSeverity severity,
                String field,
                String code,
                UUID elementId,
                String message
        ) {
            diagnostics.add(
                    new ValidationDiagnostic(
                            severity,
                            code,
                            field,
                            elementId,
                            message
                    )
            );
        }

        ProjectDocumentValidationReport report() {
            return ProjectDocumentValidationReport
                    .from(diagnostics);
        }
    }
}