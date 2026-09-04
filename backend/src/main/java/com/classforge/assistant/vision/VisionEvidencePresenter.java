package com.classforge.assistant.vision;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class VisionEvidencePresenter {

    public List<AssistantImageEvidenceItem> items(VisionUmlProposal proposal) {
        if (proposal == null) {
            return List.of();
        }

        Map<String, String> displayNames = new LinkedHashMap<>();
        for (VisionClassProposal umlClass : proposal.safeClasses()) {
            if (umlClass.ref() != null && umlClass.name() != null) {
                displayNames.put(normalize(umlClass.ref()), umlClass.name());
                displayNames.putIfAbsent(normalize(umlClass.name()), umlClass.name());
            }
        }

        List<AssistantImageEvidenceItem> result = new ArrayList<>();
        for (VisionClassProposal umlClass : proposal.safeClasses()) {
            add(result, "CLASS", umlClass.name(), umlClass.evidence());
            for (VisionAttributeProposal attribute : umlClass.safeAttributes()) {
                add(
                        result,
                        "ATTRIBUTE",
                        umlClass.name() + "." + attribute.name(),
                        attribute.evidence()
                );
            }
        }

        for (VisionRelationshipProposal relationship : proposal.safeRelationships()) {
            String source = displayName(relationship.sourceRef(), displayNames);
            String target = displayName(relationship.targetRef(), displayNames);
            add(
                    result,
                    "RELATIONSHIP",
                    source + " -> " + target,
                    relationship.evidence()
            );
        }

        return List.copyOf(result);
    }

    private String displayName(String ref, Map<String, String> displayNames) {
        if (ref == null) {
            return "?";
        }
        return displayNames.getOrDefault(normalize(ref), ref);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void add(
            List<AssistantImageEvidenceItem> target,
            String kind,
            String symbol,
            VisionEvidence evidence
    ) {
        if (evidence == null) {
            return;
        }
        target.add(new AssistantImageEvidenceItem(
                kind,
                symbol,
                evidence.label(),
                evidence.confidence(),
                evidence.x(),
                evidence.y(),
                evidence.width(),
                evidence.height()
        ));
    }
}
