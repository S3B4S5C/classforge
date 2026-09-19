package com.classforge.project.validation;

import com.classforge.project.domain.document.*;
import java.util.*;
import java.util.regex.Pattern;

final class UmlModelQualityValidator {

    void validateModelQuality(
            List<UmlClass> classes,
            List<UmlRelationship> relationships,
            ProjectValidationCollector collector
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

    String relationshipPath(
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

    String safeName(
            String value
    ) {
        return hasText(value)
                ? value
                : "(sin nombre)";
    }

    boolean hasText(
            String value
    ) {
        return value != null
                && !value.isBlank();
    }
}
