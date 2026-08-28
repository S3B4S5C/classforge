package com.classforge.project.validation;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralizationMultiplicityValidatorTests {

    private final ProjectDocumentValidator validator =
            new ProjectDocumentValidator();

    @Test
    void generalizationDoesNotRequireMultiplicities() {
        UUID childId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(
                        List.of(
                                new UmlClass(
                                        childId,
                                        "Perro",
                                        List.of()
                                ),
                                new UmlClass(
                                        parentId,
                                        "Animal",
                                        List.of()
                                )
                        ),
                        List.of(
                                new UmlRelationship(
                                        UUID.randomUUID(),
                                        childId,
                                        parentId,
                                        UmlRelationshipType.GENERALIZATION,
                                        null,
                                        null
                                )
                        )
                ),
                DiagramLayout.empty()
        );

        assertDoesNotThrow(
                () -> validator.validate(document)
        );
    }

    @Test
    void associationStillRequiresBothMultiplicities() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(
                        List.of(
                                new UmlClass(
                                        sourceId,
                                        "Animal",
                                        List.of()
                                ),
                                new UmlClass(
                                        targetId,
                                        "Cita",
                                        List.of()
                                )
                        ),
                        List.of(
                                new UmlRelationship(
                                        UUID.randomUUID(),
                                        sourceId,
                                        targetId,
                                        UmlRelationshipType.ASSOCIATION,
                                        null,
                                        null
                                )
                        )
                ),
                DiagramLayout.empty()
        );

        ProjectDocumentValidationException exception =
                assertThrows(
                        ProjectDocumentValidationException.class,
                        () -> validator.validate(document)
                );

        long multiplicityErrors =
                exception.getViolations()
                        .stream()
                        .filter(
                                violation ->
                                        violation.code().equals(
                                                "MULTIPLICITY_REQUIRED"
                                        )
                        )
                        .count();

        assertTrue(
                multiplicityErrors >= 2,
                "Association must require both multiplicities"
        );
    }
}