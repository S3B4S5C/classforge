package com.classforge.assistant.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keeps the UML AssociationClass connector out of normal class-to-class
 * relationships while preserving the solid underlying association.
 */
final class VisionAssociationClassTopology {

    private VisionAssociationClassTopology() {
    }

    static List<VisionRelationshipProposal> reconcile(
            VisionUmlProposal semantic,
            List<VisionRelationshipProposal> candidates
    ) {
        List<VisionRelationshipProposal> result = new ArrayList<>(
                candidates == null ? List.of() : candidates
        );
        if (semantic == null || semantic.safeAssociationClasses().isEmpty()) {
            return List.copyOf(result);
        }

        for (VisionAssociationClassProposal associationClass : semantic.safeAssociationClasses()) {
            String classRef = normalize(associationClass.classRef());
            String sourceRef = normalize(associationClass.sourceRef());
            String targetRef = normalize(associationClass.targetRef());
            if (classRef.isBlank() || sourceRef.isBlank() || targetRef.isBlank()) {
                continue;
            }

            // The dashed class-to-association connector is presentation only.
            result.removeIf(relationship -> {
                String source = normalize(relationship.sourceRef());
                String target = normalize(relationship.targetRef());
                return pairMatches(source, target, classRef, sourceRef)
                        || pairMatches(source, target, classRef, targetRef);
            });

            boolean underlyingPresent = result.stream().anyMatch(relationship ->
                    pairMatches(
                            normalize(relationship.sourceRef()),
                            normalize(relationship.targetRef()),
                            sourceRef,
                            targetRef
                    )
            );
            if (underlyingPresent) {
                continue;
            }

            semantic.safeRelationships().stream()
                    .filter(relationship -> pairMatches(
                            normalize(relationship.sourceRef()),
                            normalize(relationship.targetRef()),
                            sourceRef,
                            targetRef
                    ))
                    .findFirst()
                    .ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    private static boolean pairMatches(String a, String b, String x, String y) {
        return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
