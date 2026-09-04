package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantActionType;
import com.classforge.assistant.AssistantAttributePlan;
import com.classforge.assistant.AssistantPlanAction;
import com.classforge.assistant.AssistantPlanningException;
import com.classforge.assistant.AssistantSemanticPlan;
import com.classforge.assistant.AssistantTypeSource;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.domain.document.UmlVisibility;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class VisionProposalCompiler {

    public VisionCompilationResult compile(
            VisionUmlProposal proposal,
            ProjectDocument document
    ) {
        if (proposal == null) {
            throw new AssistantPlanningException("No existe propuesta visual para compilar.");
        }

        List<String> warnings = new ArrayList<>(proposal.safeWarnings());
        List<AssistantPlanAction> actions = new ArrayList<>();
        Map<String, String> referenceToClassName = new LinkedHashMap<>();
        Set<String> seenRefs = new HashSet<>();
        Set<String> seenClassNames = new HashSet<>();

        for (VisionClassProposal proposedClass : proposal.safeClasses()) {
            String ref = required(proposedClass.ref(), "class.ref");
            String name = required(proposedClass.name(), "class.name");
            if (!seenRefs.add(normalize(ref))) {
                throw new AssistantPlanningException("La propuesta visual repite el ref '" + ref + "'.");
            }
            if (!seenClassNames.add(normalize(name))) {
                throw new AssistantPlanningException("La propuesta visual repite la clase '" + name + "'.");
            }

            UmlClass existingClass = exactClass(document, name);
            String canonicalName = existingClass == null ? name : existingClass.name();
            referenceToClassName.put(normalize(ref), canonicalName);
            referenceToClassName.putIfAbsent(normalize(name), canonicalName);

            List<AssistantAttributePlan> attributes = compileAttributes(
                    proposedClass.safeAttributes(),
                    existingClass,
                    warnings
            );

            if (existingClass == null) {
                actions.add(action(
                        AssistantActionType.CREATE_CLASS,
                        name,
                        null,
                        attributes,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            } else if (!attributes.isEmpty()) {
                actions.add(action(
                        AssistantActionType.ADD_ATTRIBUTES,
                        existingClass.name(),
                        null,
                        attributes,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
            } else {
                warnings.add("La clase '" + existingClass.name() + "' ya existe y no aporta cambios automaticos.");
            }
        }

        for (VisionRelationshipProposal relationship : proposal.safeRelationships()) {
            String source = resolveClassReference(
                    relationship.sourceRef(), referenceToClassName, document
            );
            String target = resolveClassReference(
                    relationship.targetRef(), referenceToClassName, document
            );
            UmlRelationshipType type = parseRelationshipType(relationship.type());
            VisionMultiplicityProposal sourceMultiplicity = relationship.sourceMultiplicity();
            VisionMultiplicityProposal targetMultiplicity = relationship.targetMultiplicity();

            UmlRelationship existing = existingRelationship(document, source, target, type);
            if (existing != null) {
                if (multiplicityConflicts(sourceMultiplicity, existing.sourceMultiplicity())
                        || multiplicityConflicts(targetMultiplicity, existing.targetMultiplicity())) {
                    warnings.add(
                            "La relacion '" + source + " -> " + target + "' ya existe con multiplicidades distintas; "
                                    + "se omitio el cambio visual por seguridad."
                    );
                } else {
                    warnings.add(
                            "La relacion '" + source + " -> " + target + "' ya existe y se omitio."
                    );
                }
                continue;
            }

            actions.add(action(
                    AssistantActionType.CREATE_RELATIONSHIP,
                    null,
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    source,
                    target,
                    type,
                    lower(sourceMultiplicity),
                    upper(sourceMultiplicity),
                    lower(targetMultiplicity),
                    upper(targetMultiplicity)
            ));
        }

        String summary = proposal.summary() == null || proposal.summary().isBlank()
                ? "Propuesta UML detectada desde imagen"
                : proposal.summary().trim();
        double confidence = proposal.confidence() == null ? 0.0 : proposal.confidence();

        return new VisionCompilationResult(
                new AssistantSemanticPlan(summary, List.copyOf(actions)),
                List.copyOf(warnings),
                confidence
        );
    }

    private List<AssistantAttributePlan> compileAttributes(
            List<VisionAttributeProposal> proposals,
            UmlClass existingClass,
            List<String> warnings
    ) {
        List<AssistantAttributePlan> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, UmlAttribute> existing = new LinkedHashMap<>();

        if (existingClass != null) {
            for (UmlAttribute attribute : existingClass.attributes()) {
                existing.put(normalize(attribute.name()), attribute);
            }
        }

        for (VisionAttributeProposal proposal : proposals) {
            String name = required(proposal.name(), "attribute.name");
            String key = normalize(name);
            if (!seen.add(key)) {
                throw new AssistantPlanningException("La propuesta visual repite el atributo '" + name + "'.");
            }

            UmlAttribute existingAttribute = existing.get(key);
            if (existingAttribute != null) {
                List<String> conflicts = attributeConflicts(proposal, existingAttribute);
                if (conflicts.isEmpty()) {
                    warnings.add("El atributo '" + name + "' ya existe y se omitio.");
                } else {
                    warnings.add(
                            "El atributo '" + existingClass.name() + "." + existingAttribute.name()
                                    + "' contradice la imagen en " + String.join(", ", conflicts)
                                    + "; no se modifico automaticamente."
                    );
                }
                continue;
            }

            UmlDataType dataType = parseDataType(proposal.dataType(), proposal.customTypeName());
            String customTypeName = dataType == UmlDataType.CUSTOM
                    ? required(proposal.customTypeName(), "attribute.customTypeName")
                    : null;
            UmlVisibility visibility = parseVisibility(proposal.visibility());
            boolean nullable = proposal.nullable() == null || proposal.nullable();
            boolean identifier = proposal.identifier() != null && proposal.identifier();

            result.add(new AssistantAttributePlan(
                    name,
                    dataType,
                    customTypeName,
                    visibility,
                    nullable,
                    identifier,
                    proposal.dataType() == null || proposal.dataType().isBlank()
                            ? AssistantTypeSource.DEFAULT
                            : AssistantTypeSource.EXPLICIT
            ));
        }

        return List.copyOf(result);
    }

    private List<String> attributeConflicts(
            VisionAttributeProposal proposal,
            UmlAttribute existing
    ) {
        List<String> conflicts = new ArrayList<>();

        if (proposal.dataType() != null && !proposal.dataType().isBlank()) {
            UmlDataType proposedType = parseDataType(
                    proposal.dataType(),
                    proposal.customTypeName()
            );
            if (proposedType != existing.dataType()) {
                conflicts.add("tipo");
            } else if (proposedType == UmlDataType.CUSTOM
                    && proposal.customTypeName() != null
                    && !proposal.customTypeName().isBlank()
                    && !normalize(proposal.customTypeName()).equals(normalize(existing.customTypeName()))) {
                conflicts.add("tipo custom");
            }
        }

        if (proposal.visibility() != null && !proposal.visibility().isBlank()
                && parseVisibility(proposal.visibility()) != existing.visibility()) {
            conflicts.add("visibilidad");
        }
        if (proposal.nullable() != null && proposal.nullable() != existing.nullable()) {
            conflicts.add("nullable");
        }
        if (proposal.identifier() != null && proposal.identifier() != existing.identifier()) {
            conflicts.add("identifier");
        }

        return List.copyOf(conflicts);
    }

    private UmlRelationship existingRelationship(
            ProjectDocument document,
            String sourceName,
            String targetName,
            UmlRelationshipType type
    ) {
        UmlClass source = exactClass(document, sourceName);
        UmlClass target = exactClass(document, targetName);
        if (source == null || target == null) {
            return null;
        }

        for (UmlRelationship relationship : document.umlModel().relationships()) {
            if (relationship.sourceClassId().equals(source.id())
                    && relationship.targetClassId().equals(target.id())
                    && relationship.type() == type) {
                return relationship;
            }
        }
        return null;
    }

    private boolean multiplicityConflicts(
            VisionMultiplicityProposal proposed,
            Multiplicity existing
    ) {
        if (proposed == null) {
            return false;
        }
        if (existing == null) {
            return true;
        }

        Integer proposedLower = lower(proposed);
        Integer proposedUpper = upper(proposed);
        if (proposedLower != null && proposedLower != existing.lower()) {
            return true;
        }
        if (proposedUpper != null) {
            Integer existingUpper = existing.upper() == null ? -1 : existing.upper();
            return !proposedUpper.equals(existingUpper);
        }
        return false;
    }

    private UmlClass exactClass(ProjectDocument document, String name) {
        String key = normalize(name);
        for (UmlClass umlClass : document.umlModel().classes()) {
            if (normalize(umlClass.name()).equals(key)) {
                return umlClass;
            }
        }
        return null;
    }

    private String resolveClassReference(
            String raw,
            Map<String, String> references,
            ProjectDocument document
    ) {
        String value = required(raw, "relationship.classRef");
        String resolved = references.get(normalize(value));
        if (resolved != null) {
            return resolved;
        }

        UmlClass existing = exactClass(document, value);
        if (existing != null) {
            return existing.name();
        }

        throw new AssistantPlanningException(
                "La relacion visual referencia una clase inexistente: '" + value + "'."
        );
    }

    private UmlDataType parseDataType(String raw, String customTypeName) {
        if (raw == null || raw.isBlank()) {
            return UmlDataType.STRING;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        if ("INT".equals(normalized)) {
            normalized = "INTEGER";
        }
        if ("BOOL".equals(normalized)) {
            normalized = "BOOLEAN";
        }
        if ("DOUBLE".equals(normalized) || "FLOAT".equals(normalized)) {
            normalized = "DECIMAL";
        }
        if ("LOCALDATE".equals(normalized)) {
            normalized = "DATE";
        }
        if ("LOCALDATETIME".equals(normalized)) {
            normalized = "DATETIME";
        }

        try {
            return UmlDataType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            if (customTypeName != null && !customTypeName.isBlank()) {
                return UmlDataType.CUSTOM;
            }
            throw new AssistantPlanningException(
                    "Tipo de atributo visual no soportado: '" + raw + "'."
            );
        }
    }

    private UmlVisibility parseVisibility(String raw) {
        if (raw == null || raw.isBlank()) {
            return UmlVisibility.PRIVATE;
        }

        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        normalized = switch (normalized) {
            case "+", "PUBLICO", "PUBLIC" -> "PUBLIC";
            case "-", "PRIVADO", "PRIVATE" -> "PRIVATE";
            case "#", "PROTEGIDO", "PROTECTED" -> "PROTECTED";
            case "~", "PACKAGE" -> "PACKAGE";
            default -> normalized;
        };

        try {
            return UmlVisibility.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new AssistantPlanningException(
                    "Visibilidad visual no soportada: '" + raw + "'."
            );
        }
    }

    private UmlRelationshipType parseRelationshipType(String raw) {
        String normalized = required(raw, "relationship.type")
                .toUpperCase(Locale.ROOT);
        try {
            return UmlRelationshipType.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new AssistantPlanningException(
                    "Tipo de relacion visual no soportado: '" + raw + "'."
            );
        }
    }

    private Integer lower(VisionMultiplicityProposal multiplicity) {
        if (multiplicity == null) {
            return null;
        }
        if (multiplicity.lower() != null && multiplicity.lower() < 0) {
            throw new AssistantPlanningException("La multiplicidad inferior visual no puede ser negativa.");
        }
        return multiplicity.lower();
    }

    private Integer upper(VisionMultiplicityProposal multiplicity) {
        if (multiplicity == null) {
            return null;
        }
        if (Boolean.TRUE.equals(multiplicity.unbounded())) {
            return -1;
        }
        if (multiplicity.upper() != null && multiplicity.upper() < 0) {
            throw new AssistantPlanningException("La multiplicidad superior visual no puede ser negativa.");
        }
        if (multiplicity.lower() != null
                && multiplicity.upper() != null
                && multiplicity.upper() < multiplicity.lower()) {
            throw new AssistantPlanningException("La multiplicidad visual tiene upper < lower.");
        }
        return multiplicity.upper();
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AssistantPlanningException(field + " es obligatorio.");
        }
        return value.trim();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }

    private AssistantPlanAction action(
            AssistantActionType type,
            String className,
            String newName,
            List<AssistantAttributePlan> attributes,
            String attributeName,
            String newAttributeName,
            UmlDataType dataType,
            String customTypeName,
            UmlVisibility visibility,
            Boolean nullable,
            Boolean identifier,
            String sourceClassName,
            String targetClassName,
            UmlRelationshipType relationshipType,
            Integer sourceLower,
            Integer sourceUpper,
            Integer targetLower,
            Integer targetUpper
    ) {
        return new AssistantPlanAction(
                type,
                className,
                newName,
                attributes,
                attributeName,
                newAttributeName,
                dataType,
                customTypeName,
                visibility,
                nullable,
                identifier,
                sourceClassName,
                targetClassName,
                relationshipType,
                sourceLower,
                sourceUpper,
                targetLower,
                targetUpper
        );
    }
}
