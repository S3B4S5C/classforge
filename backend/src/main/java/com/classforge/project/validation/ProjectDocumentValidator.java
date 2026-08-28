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
        ValidationCollector collector = new ValidationCollector();

        if (document == null) {
            collector.add(
                    "document",
                    "DOCUMENT_REQUIRED",
                    "El documento del proyecto es obligatorio."
            );
            collector.throwIfInvalid();
            return;
        }

        if (!ProjectDocument.CURRENT_SCHEMA_VERSION.equals(document.schemaVersion())) {
            collector.add(
                    "document.schemaVersion",
                    "UNSUPPORTED_SCHEMA_VERSION",
                    "La version del documento no es compatible con esta version de ClassForge."
            );
        }

        validateClasses(document.umlModel().classes(), collector);
        validateRelationships(
                document.umlModel().relationships(),
                document.umlModel().classes(),
                collector
        );
        validateGeneralizationCycles(
                document.umlModel().relationships(),
                document.umlModel().classes(),
                collector
        );
        validateLayout(
                document.layout().nodes(),
                document.umlModel().classes(),
                collector
        );

        collector.throwIfInvalid();
    }

    private void validateClasses(
            List<UmlClass> classes,
            ValidationCollector collector
    ) {
        Set<UUID> classIds = new HashSet<>();
        Set<String> classNames = new HashSet<>();
        Set<UUID> attributeIds = new HashSet<>();

        for (int classIndex = 0; classIndex < classes.size(); classIndex++) {
            UmlClass umlClass = classes.get(classIndex);
            String classPath = "document.umlModel.classes[" + classIndex + "]";

            if (umlClass == null) {
                collector.add(
                        classPath,
                        "CLASS_REQUIRED",
                        "La clase no puede ser nula."
                );
                continue;
            }

            if (umlClass.id() == null) {
                collector.add(
                        classPath + ".id",
                        "CLASS_ID_REQUIRED",
                        "La clase necesita un identificador."
                );
            } else if (!classIds.add(umlClass.id())) {
                collector.add(
                        classPath + ".id",
                        "DUPLICATE_CLASS_ID",
                        "El identificador de la clase esta repetido."
                );
            }

            validateCodeName(
                    umlClass.name(),
                    classPath + ".name",
                    "CLASS_NAME",
                    "clase",
                    MAX_CLASS_NAME,
                    collector
            );

            if (hasText(umlClass.name())) {
                String normalized = umlClass.name().toLowerCase(Locale.ROOT);
                if (!classNames.add(normalized)) {
                    collector.add(
                            classPath + ".name",
                            "DUPLICATE_CLASS_NAME",
                            "Ya existe otra clase con el nombre '" + umlClass.name() + "'."
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
        Set<String> attributeNames = new HashSet<>();

        for (int attributeIndex = 0;
             attributeIndex < umlClass.attributes().size();
             attributeIndex++) {
            UmlAttribute attribute = umlClass.attributes().get(attributeIndex);
            String attributePath =
                    classPath + ".attributes[" + attributeIndex + "]";

            if (attribute == null) {
                collector.add(
                        attributePath,
                        "ATTRIBUTE_REQUIRED",
                        "El atributo no puede ser nulo."
                );
                continue;
            }

            if (attribute.id() == null) {
                collector.add(
                        attributePath + ".id",
                        "ATTRIBUTE_ID_REQUIRED",
                        "El atributo necesita un identificador."
                );
            } else if (!attributeIds.add(attribute.id())) {
                collector.add(
                        attributePath + ".id",
                        "DUPLICATE_ATTRIBUTE_ID",
                        "El identificador del atributo esta repetido."
                );
            }

            validateCodeName(
                    attribute.name(),
                    attributePath + ".name",
                    "ATTRIBUTE_NAME",
                    "atributo",
                    MAX_ATTRIBUTE_NAME,
                    collector
            );

            if (hasText(attribute.name())) {
                String normalized = attribute.name().toLowerCase(Locale.ROOT);
                if (!attributeNames.add(normalized)) {
                    collector.add(
                            attributePath + ".name",
                            "DUPLICATE_ATTRIBUTE_NAME",
                            "La clase '" + umlClass.name()
                                    + "' ya contiene un atributo llamado '"
                                    + attribute.name() + "'."
                    );
                }
            }

            if (attribute.dataType() == null) {
                collector.add(
                        attributePath + ".dataType",
                        "ATTRIBUTE_TYPE_REQUIRED",
                        "Selecciona un tipo para el atributo."
                );
            }

            if (attribute.visibility() == null) {
                collector.add(
                        attributePath + ".visibility",
                        "ATTRIBUTE_VISIBILITY_REQUIRED",
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
                        collector
                );
            }

            if (attribute.identifier() && attribute.nullable()) {
                collector.add(
                        attributePath + ".nullable",
                        "IDENTIFIER_CANNOT_BE_NULLABLE",
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
        Set<UUID> relationshipIds = new HashSet<>();

        for (UmlClass umlClass : classes) {
            if (umlClass != null && umlClass.id() != null) {
                classIds.add(umlClass.id());
            }
        }

        for (int relationshipIndex = 0;
             relationshipIndex < relationships.size();
             relationshipIndex++) {
            UmlRelationship relationship = relationships.get(relationshipIndex);
            String path =
                    "document.umlModel.relationships[" + relationshipIndex + "]";

            if (relationship == null) {
                collector.add(
                        path,
                        "RELATIONSHIP_REQUIRED",
                        "La relacion no puede ser nula."
                );
                continue;
            }

            if (relationship.id() == null) {
                collector.add(
                        path + ".id",
                        "RELATIONSHIP_ID_REQUIRED",
                        "La relacion necesita un identificador."
                );
            } else if (!relationshipIds.add(relationship.id())) {
                collector.add(
                        path + ".id",
                        "DUPLICATE_RELATIONSHIP_ID",
                        "El identificador de la relacion esta repetido."
                );
            }

            validateClassReference(
                    relationship.sourceClassId(),
                    path + ".sourceClassId",
                    classIds,
                    collector
            );
            validateClassReference(
                    relationship.targetClassId(),
                    path + ".targetClassId",
                    classIds,
                    collector
            );

            if (relationship.type() == null) {
                collector.add(
                        path + ".type",
                        "RELATIONSHIP_TYPE_REQUIRED",
                        "Selecciona un tipo de relacion UML."
                );
            }

            if (relationship.type() == UmlRelationshipType.GENERALIZATION
                    && relationship.sourceClassId() != null
                    && relationship.sourceClassId().equals(relationship.targetClassId())) {
                collector.add(
                        path + ".targetClassId",
                        "GENERALIZATION_SELF_REFERENCE",
                        "Una clase no puede generalizarse a si misma."
                );
            }

            if (relationship.type() != UmlRelationshipType.GENERALIZATION) {
                validateMultiplicity(
                        relationship.sourceMultiplicity(),
                        path + ".sourceMultiplicity",
                        collector
                );
                validateMultiplicity(
                        relationship.targetMultiplicity(),
                        path + ".targetMultiplicity",
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
        Set<UUID> knownClassIds = new HashSet<>();

        for (UmlClass umlClass : classes) {
            if (umlClass != null && umlClass.id() != null) {
                knownClassIds.add(umlClass.id());
            }
        }

        Map<UUID, List<UUID>> graph = new HashMap<>();

        for (UmlRelationship relationship : relationships) {
            if (
                    relationship == null
                            || relationship.type() != UmlRelationshipType.GENERALIZATION
                            || relationship.sourceClassId() == null
                            || relationship.targetClassId() == null
                            || relationship.sourceClassId().equals(relationship.targetClassId())
                            || !knownClassIds.contains(relationship.sourceClassId())
                            || !knownClassIds.contains(relationship.targetClassId())
            ) {
                continue;
            }

            graph.computeIfAbsent(
                    relationship.sourceClassId(),
                    ignored -> new ArrayList<>()
            ).add(relationship.targetClassId());
        }

        Map<UUID, Integer> state = new HashMap<>();

        for (UUID classId : knownClassIds) {
            if (hasGeneralizationCycle(classId, graph, state)) {
                collector.add(
                        "document.umlModel.relationships",
                        "GENERALIZATION_CYCLE",
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
        int currentState = state.getOrDefault(classId, 0);

        if (currentState == 1) {
            return true;
        }

        if (currentState == 2) {
            return false;
        }

        state.put(classId, 1);

        for (UUID parentId : graph.getOrDefault(classId, List.of())) {
            if (hasGeneralizationCycle(parentId, graph, state)) {
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
            ValidationCollector collector
    ) {
        if (classId == null) {
            collector.add(
                    field,
                    "RELATIONSHIP_CLASS_REQUIRED",
                    "La relacion debe apuntar a una clase."
            );
            return;
        }

        if (!knownClassIds.contains(classId)) {
            collector.add(
                    field,
                    "RELATIONSHIP_CLASS_NOT_FOUND",
                    "La relacion referencia una clase que no existe."
            );
        }
    }

    private void validateMultiplicity(
            Multiplicity multiplicity,
            String path,
            ValidationCollector collector
    ) {
        if (multiplicity == null) {
            collector.add(
                    path,
                    "MULTIPLICITY_REQUIRED",
                    "La multiplicidad es obligatoria."
            );
            return;
        }

        if (multiplicity.lower() < 0) {
            collector.add(
                    path + ".lower",
                    "MULTIPLICITY_LOWER_INVALID",
                    "El limite inferior no puede ser negativo."
            );
        }

        if (multiplicity.upper() != null
                && multiplicity.upper() < multiplicity.lower()) {
            collector.add(
                    path + ".upper",
                    "MULTIPLICITY_RANGE_INVALID",
                    "El limite superior no puede ser menor al inferior."
            );
        }
    }

    private void validateLayout(
            java.util.Map<UUID, DiagramNodeLayout> nodes,
            List<UmlClass> classes,
            ValidationCollector collector
    ) {
        Set<UUID> classIds = new HashSet<>();

        for (UmlClass umlClass : classes) {
            if (umlClass != null && umlClass.id() != null) {
                classIds.add(umlClass.id());
            }
        }

        for (var entry : nodes.entrySet()) {
            UUID classId = entry.getKey();
            DiagramNodeLayout layout = entry.getValue();
            String path = "document.layout.nodes[" + classId + "]";

            if (classId == null || !classIds.contains(classId)) {
                collector.add(
                        path,
                        "LAYOUT_CLASS_NOT_FOUND",
                        "El layout contiene una posicion para una clase inexistente."
                );
            }

            if (layout == null) {
                collector.add(
                        path,
                        "LAYOUT_REQUIRED",
                        "La informacion visual de la clase no puede ser nula."
                );
                continue;
            }

            if (!Double.isFinite(layout.x()) || !Double.isFinite(layout.y())) {
                collector.add(
                        path,
                        "LAYOUT_POSITION_INVALID",
                        "La posicion de la clase debe contener valores finitos."
                );
            }

            if (!Double.isFinite(layout.width())
                    || !Double.isFinite(layout.height())
                    || layout.width() <= 0
                    || layout.height() <= 0) {
                collector.add(
                        path,
                        "LAYOUT_SIZE_INVALID",
                        "El ancho y alto de la clase deben ser mayores que cero."
                );
            }
        }
    }

    private void validateCodeName(
            String value,
            String field,
            String codePrefix,
            String label,
            int maxLength,
            ValidationCollector collector
    ) {
        if (!hasText(value)) {
            collector.add(
                    field,
                    codePrefix + "_REQUIRED",
                    "El nombre del " + label + " es obligatorio."
            );
            return;
        }

        if (value.length() > maxLength) {
            collector.add(
                    field,
                    codePrefix + "_TOO_LONG",
                    "El nombre del " + label
                            + " no puede superar " + maxLength + " caracteres."
            );
        }

        if (!CODE_NAME.matcher(value).matches()) {
            collector.add(
                    field,
                    codePrefix + "_INVALID",
                    "Usa un nombre compatible con codigo: letras, numeros y '_', sin espacios, y no empieces con un numero."
            );
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class ValidationCollector {
        private final ArrayList<ValidationViolation> violations = new ArrayList<>();

        void add(String field, String code, String message) {
            violations.add(new ValidationViolation(field, code, message));
        }

        void throwIfInvalid() {
            if (!violations.isEmpty()) {
                throw new ProjectDocumentValidationException(violations);
            }
        }
    }
}