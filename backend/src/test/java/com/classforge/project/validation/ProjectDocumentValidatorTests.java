package com.classforge.project.validation;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlVisibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDocumentValidatorTests {

    private final ProjectDocumentValidator validator =
            new ProjectDocumentValidator();

    @Test
    void acceptsTypedClassAndAttribute() {
        UUID classId = UUID.randomUUID();

        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(
                        List.of(
                                new UmlClass(
                                        classId,
                                        "Animal",
                                        List.of(
                                                new UmlAttribute(
                                                        UUID.randomUUID(),
                                                        "id",
                                                        UmlDataType.LONG,
                                                        null,
                                                        UmlVisibility.PRIVATE,
                                                        false,
                                                        true
                                                ),
                                                new UmlAttribute(
                                                        UUID.randomUUID(),
                                                        "nombre",
                                                        UmlDataType.STRING,
                                                        null,
                                                        UmlVisibility.PRIVATE,
                                                        true,
                                                        false
                                                )
                                        )
                                )
                        ),
                        List.of()
                ),
                new DiagramLayout(
                        Map.of(
                                classId,
                                new DiagramNodeLayout(80, 80, 240, 160)
                        )
                )
        );

        assertDoesNotThrow(() -> validator.validate(document));
    }

    @Test
    void reportsDuplicateClassNameWithFriendlyViolation() {
        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(
                        List.of(
                                new UmlClass(UUID.randomUUID(), "Animal", List.of()),
                                new UmlClass(UUID.randomUUID(), "Animal", List.of())
                        ),
                        List.of()
                ),
                DiagramLayout.empty()
        );

        ProjectDocumentValidationException exception =
                assertThrows(
                        ProjectDocumentValidationException.class,
                        () -> validator.validate(document)
                );

        assertTrue(
                exception.getViolations().stream()
                        .anyMatch(
                                violation -> violation.code().equals("DUPLICATE_CLASS_NAME")
                        )
        );
    }

    @Test
    void rejectsDuplicateAttributeAndNullableIdentifier() {
        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(
                        List.of(
                                new UmlClass(
                                        UUID.randomUUID(),
                                        "Animal",
                                        List.of(
                                                new UmlAttribute(
                                                        UUID.randomUUID(),
                                                        "id",
                                                        UmlDataType.LONG,
                                                        null,
                                                        UmlVisibility.PRIVATE,
                                                        true,
                                                        true
                                                ),
                                                new UmlAttribute(
                                                        UUID.randomUUID(),
                                                        "id",
                                                        UmlDataType.STRING,
                                                        null,
                                                        UmlVisibility.PRIVATE,
                                                        true,
                                                        false
                                                )
                                        )
                                )
                        ),
                        List.of()
                ),
                DiagramLayout.empty()
        );

        ProjectDocumentValidationException exception =
                assertThrows(
                        ProjectDocumentValidationException.class,
                        () -> validator.validate(document)
                );

        assertEquals(2, exception.getViolations().size());
        assertTrue(
                exception.getViolations().stream()
                        .anyMatch(
                                violation -> violation.code().equals(
                                        "IDENTIFIER_CANNOT_BE_NULLABLE"
                                )
                        )
        );
        assertTrue(
                exception.getViolations().stream()
                        .anyMatch(
                                violation -> violation.code().equals(
                                        "DUPLICATE_ATTRIBUTE_NAME"
                                )
                        )
        );
    }

    @Test
    void requiresCustomTypeName() {
        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(
                        List.of(
                                new UmlClass(
                                        UUID.randomUUID(),
                                        "Invoice",
                                        List.of(
                                                new UmlAttribute(
                                                        UUID.randomUUID(),
                                                        "total",
                                                        UmlDataType.CUSTOM,
                                                        "",
                                                        UmlVisibility.PRIVATE,
                                                        false,
                                                        false
                                                )
                                        )
                                )
                        ),
                        List.of()
                ),
                DiagramLayout.empty()
        );

        ProjectDocumentValidationException exception =
                assertThrows(
                        ProjectDocumentValidationException.class,
                        () -> validator.validate(document)
                );

        assertTrue(
                exception.getViolations().stream()
                        .anyMatch(
                                violation -> violation.code().equals(
                                        "CUSTOM_TYPE_NAME_REQUIRED"
                                )
                        )
        );
    }
}