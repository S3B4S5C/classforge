package com.classforge.assistant.vision;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class VisionProposalOutcomeNormalizer {

    static final String NO_ACTIONABLE_PREFIX = "NO_ACTIONABLE_UML:";
    private static final String NO_ACTIONABLE_WARNING =
            NO_ACTIONABLE_PREFIX + " No se detecto UML de clases accionable.";

    private VisionProposalOutcomeNormalizer() {
    }

    static VisionUmlProposal normalize(VisionUmlProposal proposal) {
        if (proposal == null) {
            return null;
        }

        List<String> warnings = new ArrayList<>();
        for (String warning : proposal.safeWarnings()) {
            if (warning != null
                    && warning.regionMatches(
                    true, 0, NO_ACTIONABLE_PREFIX, 0, NO_ACTIONABLE_PREFIX.length()
            )) {
                continue;
            }
            warnings.add(warning);
        }

        if (proposal.safeClasses().isEmpty() && proposal.safeRelationships().isEmpty()) {
            warnings.add(NO_ACTIONABLE_WARNING);
        }

        return new VisionUmlProposal(
                proposal.summary(),
                proposal.safeClasses(),
                proposal.safeRelationships(),
                List.copyOf(new LinkedHashSet<>(warnings)),
                proposal.confidence()
        );
    }
}
