package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class VisionHybridProposalAssembler {

    public VisionUmlProposal assemble(
            VisionUmlProposal semantic,
            UmlDiagramGeometry geometry,
            VisionHybridRelationshipAnnotationProposal annotation
    ) {
        if (semantic == null || annotation == null) {
            throw new AssistantPlanningException("El merge hibrido requiere propuestas semantica y relacional.");
        }
        Map<String, VisionGeometryEdgeCandidate> candidates = new HashMap<>();
        for (VisionGeometryEdgeCandidate edge : geometry.safeEdgeCandidates()) {
            candidates.put(normalize(edge.edgeId()), edge);
        }
        Map<String, VisionClassProposal> classes = new HashMap<>();
        for (VisionClassProposal umlClass : semantic.safeClasses()) {
            classes.put(normalize(umlClass.ref()), umlClass);
        }

        List<VisionRelationshipProposal> relationships = new ArrayList<>();
        Set<String> seenEdgeIds = new LinkedHashSet<>();
        Set<String> seenRelationships = new LinkedHashSet<>();
        for (VisionHybridEdgeAnnotation edge : annotation.safeEdges()) {
            String edgeId = normalize(required(edge.edgeId(), "edgeId"));
            if (!seenEdgeIds.add(edgeId)) {
                throw new AssistantPlanningException("La anotacion hibrida repitio edgeId: " + edge.edgeId());
            }
            VisionGeometryEdgeCandidate candidate = candidates.get(edgeId);
            if (candidate == null) {
                throw new AssistantPlanningException("La anotacion hibrida invento edgeId: " + edge.edgeId());
            }
            String type = required(edge.type(), "type").toUpperCase(Locale.ROOT);
            String markerAt = required(edge.markerAt(), "markerAt").toUpperCase(Locale.ROOT);
            if (!Set.of("ASSOCIATION", "AGGREGATION", "COMPOSITION", "GENERALIZATION").contains(type)) {
                throw new AssistantPlanningException("Tipo hibrido no soportado: " + type);
            }
            if (!Set.of("NONE", "A", "B").contains(markerAt)) {
                throw new AssistantPlanningException("markerAt hibrido invalido: " + markerAt);
            }

            String a = candidate.aClassRef();
            String b = candidate.bClassRef();
            String source;
            String target;
            VisionMultiplicityProposal sourceMultiplicity;
            VisionMultiplicityProposal targetMultiplicity;

            if ("ASSOCIATION".equals(type)) {
                if (!"NONE".equals(markerAt)) {
                    throw new AssistantPlanningException("ASSOCIATION requiere markerAt=NONE.");
                }
                source = a;
                target = b;
                sourceMultiplicity = edge.multiplicityA();
                targetMultiplicity = edge.multiplicityB();
            } else if ("GENERALIZATION".equals(type)) {
                if ("NONE".equals(markerAt)) {
                    throw new AssistantPlanningException("GENERALIZATION requiere localizar el triangulo en A o B.");
                }
                boolean markerA = "A".equals(markerAt);
                source = markerA ? b : a;
                target = markerA ? a : b;
                sourceMultiplicity = markerA ? edge.multiplicityB() : edge.multiplicityA();
                targetMultiplicity = markerA ? edge.multiplicityA() : edge.multiplicityB();
            } else {
                if ("NONE".equals(markerAt)) {
                    throw new AssistantPlanningException(type + " requiere localizar el rombo en A o B.");
                }
                boolean markerA = "A".equals(markerAt);
                source = markerA ? a : b;
                target = markerA ? b : a;
                sourceMultiplicity = markerA ? edge.multiplicityA() : edge.multiplicityB();
                targetMultiplicity = markerA ? edge.multiplicityB() : edge.multiplicityA();
            }

            if (!classes.containsKey(normalize(source)) || !classes.containsKey(normalize(target))) {
                throw new AssistantPlanningException("El edge hibrido no apunta a refs de clases confirmadas.");
            }
            String duplicate = relationshipKey(type, source, target);
            if (!seenRelationships.add(duplicate)) {
                throw new AssistantPlanningException("La anotacion hibrida produjo una relacion duplicada: " + duplicate);
            }
            validateMultiplicity(sourceMultiplicity);
            validateMultiplicity(targetMultiplicity);
            relationships.add(new VisionRelationshipProposal(
                    source,
                    target,
                    type,
                    sourceMultiplicity,
                    targetMultiplicity,
                    new VisionEvidence(
                            required(edge.evidenceLabel(), "evidenceLabel"),
                            edge.confidence(),
                            null, null, null, null
                    )
            ));
        }

        List<String> warnings = new ArrayList<>(semantic.safeWarnings());
        warnings.addAll(annotation.safeWarnings());
        warnings.add("HYBRID_CV: endpoints candidatos derivados de localizacion cerrada + geometria OpenCV; tipo/multiplicidad anotados sobre crops locales.");

        return new VisionUmlProposal(
                semantic.summary(),
                semantic.safeClasses(),
                List.copyOf(relationships),
                List.copyOf(new LinkedHashSet<>(warnings)),
                minConfidence(semantic.confidence(), annotation.confidence())
        );
    }

    private void validateMultiplicity(VisionMultiplicityProposal multiplicity) {
        if (multiplicity == null) {
            return;
        }
        if (multiplicity.lower() == null || multiplicity.lower() < 0) {
            throw new AssistantPlanningException("Multiplicity lower invalido en merge hibrido.");
        }
        boolean unbounded = Boolean.TRUE.equals(multiplicity.unbounded());
        if (unbounded && multiplicity.upper() != null) {
            throw new AssistantPlanningException("Multiplicity unbounded no debe incluir upper.");
        }
        if (!unbounded && (multiplicity.upper() == null || multiplicity.upper() < multiplicity.lower())) {
            throw new AssistantPlanningException("Multiplicity upper invalido en merge hibrido.");
        }
    }

    private String relationshipKey(String type, String source, String target) {
        String a = normalize(source);
        String b = normalize(target);
        if ("ASSOCIATION".equals(type) && a.compareTo(b) > 0) {
            String swap = a;
            a = b;
            b = swap;
        }
        return type + "|" + a + "|" + b;
    }

    private Double minConfidence(Double a, Double b) {
        if (a == null) return b;
        if (b == null) return a;
        return Math.min(a, b);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AssistantPlanningException(field + " es obligatorio en merge hibrido.");
        }
        return value.trim();
    }
}
