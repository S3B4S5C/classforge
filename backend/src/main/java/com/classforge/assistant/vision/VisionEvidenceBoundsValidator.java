package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.springframework.stereotype.Component;

@Component
public class VisionEvidenceBoundsValidator {

    public void validate(VisionUmlProposal proposal, int imageWidth, int imageHeight) {
        if (proposal == null) {
            return;
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            throw new AssistantPlanningException("Las dimensiones normalizadas de la imagen son invalidas.");
        }

        for (VisionClassProposal umlClass : proposal.safeClasses()) {
            validate(umlClass.evidence(), imageWidth, imageHeight, "class.evidence");
            for (VisionAttributeProposal attribute : umlClass.safeAttributes()) {
                validate(attribute.evidence(), imageWidth, imageHeight, "attribute.evidence");
            }
        }

        for (VisionRelationshipProposal relationship : proposal.safeRelationships()) {
            validate(relationship.evidence(), imageWidth, imageHeight, "relationship.evidence");
        }

        for (VisionAssociationClassProposal associationClass : proposal.safeAssociationClasses()) {
            validate(associationClass.evidence(), imageWidth, imageHeight, "associationClass.evidence");
        }
    }

    private void validate(
            VisionEvidence evidence,
            int imageWidth,
            int imageHeight,
            String field
    ) {
        if (evidence == null || evidence.x() == null) {
            return;
        }

        long right = (long) evidence.x() + evidence.width();
        long bottom = (long) evidence.y() + evidence.height();
        if (right > imageWidth || bottom > imageHeight) {
            throw new AssistantPlanningException(
                    "El bounding box de " + field + " queda fuera de la imagen normalizada."
            );
        }
    }
}
