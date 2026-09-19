package com.classforge.project.validation;

import com.classforge.project.domain.document.*;
import java.util.*;
import java.util.regex.Pattern;

final class GeneralizationValidator {

    void validateGeneralizationCycles(
            List<UmlRelationship> relationships,
            List<UmlClass> classes,
            ProjectValidationCollector collector
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

    boolean hasGeneralizationCycle(
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
}
