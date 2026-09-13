package com.classforge.generation.spring.application;

import com.classforge.generation.relational.RelationalMappingDiagnostic;
import com.classforge.generation.relational.RelationalMappingDiagnosticCode;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationshipType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class SpringBootGenerationUmlPreparation {
    private SpringBootGenerationUmlPreparation() { }

    static List<SpringBootGenerationPrimaryKeyFallback> fallbackCandidates(
            UmlModel model,
            List<RelationalMappingDiagnostic> diagnostics
    ) {
        if (diagnostics.isEmpty()
                || diagnostics.stream().anyMatch(diagnostic ->
                diagnostic.code() != RelationalMappingDiagnosticCode.CLASS_IDENTIFIER_REQUIRED)) {
            return List.of();
        }

        Map<UUID, UmlClass> classesById = new HashMap<>();
        for (UmlClass umlClass : model.classes()) {
            classesById.put(umlClass.id(), umlClass);
        }

        List<SpringBootGenerationPrimaryKeyFallback> result = new ArrayList<>();
        for (RelationalMappingDiagnostic diagnostic : diagnostics) {
            UmlClass umlClass = classesById.get(diagnostic.elementId());
            if (umlClass == null || umlClass.attributes().isEmpty()) {
                return List.of();
            }

            UmlAttribute first = umlClass.attributes().get(0);
            result.add(new SpringBootGenerationPrimaryKeyFallback(
                    umlClass.id(),
                    umlClass.name(),
                    first.id(),
                    first.name()
            ));
        }
        return List.copyOf(result);
    }

    static UmlModel applyFirstAttributeIdentifiers(UmlModel model) {
        Set<UUID> subclassIds = new HashSet<>();
        model.relationships().stream()
                .filter(relationship -> relationship.type() == UmlRelationshipType.GENERALIZATION)
                .forEach(relationship -> subclassIds.add(relationship.sourceClassId()));

        List<UmlClass> classes = model.classes().stream()
                .map(umlClass -> prepareClass(umlClass, subclassIds))
                .toList();
        return new UmlModel(classes, model.relationships());
    }

    private static UmlClass prepareClass(UmlClass umlClass, Set<UUID> subclassIds) {
        if (subclassIds.contains(umlClass.id())
                || umlClass.attributes().isEmpty()
                || umlClass.attributes().stream().anyMatch(UmlAttribute::identifier)) {
            return umlClass;
        }

        List<UmlAttribute> attributes = new ArrayList<>(umlClass.attributes());
        UmlAttribute first = attributes.get(0);
        attributes.set(0, new UmlAttribute(
                first.id(),
                first.name(),
                first.dataType(),
                first.customTypeName(),
                first.visibility(),
                false,
                true
        ));
        return new UmlClass(umlClass.id(), umlClass.name(), attributes);
    }
}
