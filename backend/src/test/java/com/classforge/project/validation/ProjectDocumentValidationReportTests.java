package com.classforge.project.validation;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.domain.document.UmlVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDocumentValidationReportTests {

    private final ProjectDocumentValidator validator =
            new ProjectDocumentValidator();

    @Test
    void reportsWarningsWithoutMakingDocumentInvalid() {
        UUID classId = UUID.randomUUID();

        ProjectDocument document =
                new ProjectDocument(
                        "1.0",
                        new UmlModel(
                                List.of(
                                        new UmlClass(
                                                classId,
                                                "Animal",
                                                List.of()
                                        )
                                ),
                                List.of()
                        ),
                        DiagramLayout.empty()
                );

        ProjectDocumentValidationReport report =
                validator.analyze(document);

        assertTrue(report.valid());
        assertEquals(0, report.errors());
        assertTrue(report.warnings() >= 3);

        assertTrue(
                report.diagnostics()
                        .stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic.code()
                                                .equals(
                                                        "CLASS_WITHOUT_ATTRIBUTES"
                                                )
                                                && classId.equals(
                                                        diagnostic.elementId()
                                                )
                        )
        );

        assertTrue(
                report.diagnostics()
                        .stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic.code()
                                                .equals(
                                                        "CLASS_WITHOUT_IDENTIFIER"
                                                )
                        )
        );

        assertTrue(
                report.diagnostics()
                        .stream()
                        .anyMatch(
                                diagnostic ->
                                        diagnostic.code()
                                                .equals(
                                                        "ISOLATED_CLASS"
                                                )
                        )
        );
    }

    @Test
    void reportsErrorsAndKeepsElementIdForNavigation() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();

        ProjectDocument document =
                new ProjectDocument(
                        "1.0",
                        new UmlModel(
                                List.of(
                                        validClass(
                                                firstId,
                                                "Animal"
                                        ),
                                        validClass(
                                                secondId,
                                                "Animal"
                                        )
                                ),
                                List.of(
                                        new UmlRelationship(
                                                UUID.randomUUID(),
                                                firstId,
                                                secondId,
                                                UmlRelationshipType.ASSOCIATION,
                                                Multiplicity.one(),
                                                Multiplicity.many()
                                        )
                                )
                        ),
                        DiagramLayout.empty()
                );

        ProjectDocumentValidationReport report =
                validator.analyze(document);

        assertFalse(report.valid());
        assertTrue(report.errors() >= 1);

        ValidationDiagnostic duplicate =
                report.diagnostics()
                        .stream()
                        .filter(
                                diagnostic ->
                                        diagnostic.code()
                                                .equals(
                                                        "DUPLICATE_CLASS_NAME"
                                                )
                        )
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                ValidationSeverity.ERROR,
                duplicate.severity()
        );

        assertEquals(
                secondId,
                duplicate.elementId()
        );
    }

    private UmlClass validClass(
            UUID id,
            String name
    ) {
        return new UmlClass(
                id,
                name,
                List.of(
                        new UmlAttribute(
                                UUID.randomUUID(),
                                "id",
                                UmlDataType.LONG,
                                null,
                                UmlVisibility.PRIVATE,
                                false,
                                true
                        )
                )
        );
    }
}