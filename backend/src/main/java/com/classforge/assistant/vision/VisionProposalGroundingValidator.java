package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class VisionProposalGroundingValidator {

    public void validate(VisionUmlProposal proposal) {
        if (proposal == null) {
            throw new AssistantPlanningException("El VLM no devolvio una propuesta UML.");
        }

        validateNoActionableConsistency(proposal);
        validateConfidence(proposal.confidence(), "proposal.confidence");

        for (VisionClassProposal umlClass : proposal.safeClasses()) {
            required(umlClass.ref(), "class.ref");
            required(umlClass.name(), "class.name");
            String classEvidenceLabel = requireEvidence(
                    umlClass.evidence(),
                    umlClass.name(),
                    "class.evidence",
                    null
            );

            for (VisionAttributeProposal attribute : umlClass.safeAttributes()) {
                required(attribute.name(), "attribute.name");
                requireEvidence(
                        attribute.evidence(),
                        attribute.name(),
                        "attribute.evidence",
                        classEvidenceLabel
                );
            }
        }

        for (VisionRelationshipProposal relationship : proposal.safeRelationships()) {
            required(relationship.sourceRef(), "relationship.sourceRef");
            required(relationship.targetRef(), "relationship.targetRef");
            required(relationship.type(), "relationship.type");
            requireEvidence(relationship.evidence(), null, "relationship.evidence", null);
        }
    }

    private void validateNoActionableConsistency(VisionUmlProposal proposal) {
        boolean noActionableWarning = proposal.safeWarnings().stream()
                .filter(warning -> warning != null)
                .map(String::trim)
                .anyMatch(warning -> warning.regionMatches(
                        true,
                        0,
                        "NO_ACTIONABLE_UML:",
                        0,
                        "NO_ACTIONABLE_UML:".length()
                ));

        if (noActionableWarning
                && (!proposal.safeClasses().isEmpty() || !proposal.safeRelationships().isEmpty())) {
            throw new AssistantPlanningException(
                    "NO_ACTIONABLE_UML solo es valido cuando classes y relationships estan vacios."
            );
        }
    }

    private String requireEvidence(
            VisionEvidence evidence,
            String expectedLabel,
            String field,
            String enclosingClassEvidenceLabel
    ) {
        if (evidence == null) {
            throw new AssistantPlanningException(
                    "La propuesta visual no incluye evidencia para " + field + "."
            );
        }

        String label = required(evidence.label(), field + ".label");
        validateConfidence(evidence.confidence(), field + ".confidence");

        if (expectedLabel != null
                && !supports(label, expectedLabel)
                && !supports(enclosingClassEvidenceLabel, expectedLabel)) {
            throw new AssistantPlanningException(
                    "La evidencia visual no respalda el simbolo '" + expectedLabel + "'."
            );
        }

        int bboxParts = 0;
        bboxParts += evidence.x() == null ? 0 : 1;
        bboxParts += evidence.y() == null ? 0 : 1;
        bboxParts += evidence.width() == null ? 0 : 1;
        bboxParts += evidence.height() == null ? 0 : 1;

        if (bboxParts != 0 && bboxParts != 4) {
            throw new AssistantPlanningException(
                    "La evidencia visual debe incluir x/y/width/height completos o ninguno."
            );
        }

        if (evidence.x() != null && evidence.x() < 0
                || evidence.y() != null && evidence.y() < 0
                || evidence.width() != null && evidence.width() <= 0
                || evidence.height() != null && evidence.height() <= 0) {
            throw new AssistantPlanningException("La evidencia visual contiene un bounding box invalido.");
        }

        return label;
    }

    private boolean supports(String evidenceLabel, String expectedLabel) {
        if (evidenceLabel == null || evidenceLabel.isBlank()
                || expectedLabel == null || expectedLabel.isBlank()) {
            return false;
        }
        return normalize(evidenceLabel).contains(normalize(expectedLabel));
    }

    private void validateConfidence(Double confidence, String field) {
        if (confidence == null) {
            return;
        }
        if (confidence < 0.0 || confidence > 1.0) {
            throw new AssistantPlanningException(field + " debe estar entre 0 y 1.");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AssistantPlanningException(field + " es obligatorio.");
        }
        return value.trim();
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace("\\n", " ")
                .replace("\\r", " ")
                .replace("\\t", " ")
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
    }
}
