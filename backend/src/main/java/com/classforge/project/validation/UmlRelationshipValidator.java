package com.classforge.project.validation;

import com.classforge.project.domain.document.*;
import java.util.*;
import java.util.regex.Pattern;

final class UmlRelationshipValidator {

    void validateRelationships(
            List<UmlRelationship> relationships,
            List<UmlClass> classes,
            ProjectValidationCollector collector
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

    void validateClassReference(
            UUID classId,
            String field,
            Set<UUID> knownClassIds,
            UUID relationshipId,
            ProjectValidationCollector collector
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

    void validateMultiplicity(
            Multiplicity multiplicity,
            String path,
            UUID relationshipId,
            ProjectValidationCollector collector
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
}
