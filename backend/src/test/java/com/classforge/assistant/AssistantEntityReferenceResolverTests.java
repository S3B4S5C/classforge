package com.classforge.assistant;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantEntityReferenceResolverTests {

    private final AssistantEntityReferenceResolver resolver =
            new AssistantEntityReferenceResolver();

    @Test
    void resolvesLeetAndTranspositionTyposToCanonicalClassNames() {
        ProjectDocument document =
                documentWith(
                        "Animal",
                        "Mascota",
                        "Veterinaria"
                );

        List<AssistantEntityReferenceResolver.ResolvedClassReference> resolved =
                resolver.resolveMentions(
                        "Crea una asociacion entre 4nimal y msacota",
                        document
                );

        assertEquals(
                List.of(
                        "Animal",
                        "Mascota"
                ),
                resolved.stream()
                        .map(
                                AssistantEntityReferenceResolver.ResolvedClassReference::canonicalName
                        )
                        .toList()
        );
    }

    @Test
    void ambiguousShortFragmentsAreNotSilentlyResolved() {
        ProjectDocument document =
                documentWith(
                        "Animal",
                        "AnimalDomestico",
                        "Mascota"
                );

        List<AssistantEntityReferenceResolver.ResolvedClassReference> resolved =
                resolver.resolveMentions(
                        "borra anim",
                        document
                );

        assertTrue(
                resolved.isEmpty()
        );
    }

    @Test
    void rawTypoFromLlmCanBeCanonicalized() {
        ProjectDocument document =
                documentWith(
                        "Animal",
                        "Mascota"
                );

        assertEquals(
                "Animal",
                resolver.resolveExistingClass(
                                "4nimal",
                                document
                        )
                        .orElseThrow()
                        .canonicalName()
        );
    }

    private ProjectDocument documentWith(
            String... names
    ) {
        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(
                        java.util.Arrays.stream(
                                        names
                                )
                                .map(
                                        name ->
                                                new UmlClass(
                                                        UUID.randomUUID(),
                                                        name,
                                                        List.of()
                                                )
                                )
                                .toList(),
                        List.of()
                ),
                DiagramLayout.empty()
        );
    }
}
