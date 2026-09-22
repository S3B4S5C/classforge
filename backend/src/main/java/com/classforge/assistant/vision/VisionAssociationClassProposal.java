package com.classforge.assistant.vision;

/**
 * Visual UML AssociationClass marker. classRef points to the class box joined
 * by a dashed connector to the underlying relationship sourceRef-targetRef.
 */
public record VisionAssociationClassProposal(
        String classRef,
        String sourceRef,
        String targetRef,
        VisionEvidence evidence
) {
}
