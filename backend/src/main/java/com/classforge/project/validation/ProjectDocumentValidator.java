package com.classforge.project.validation;

import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlRelationship;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Facade for canonical ProjectDocument validation. Detail rules are grouped by
 * UML concern so the validation path can be read without traversing one large class.
 */
@Component
public class ProjectDocumentValidator {
    private final UmlClassValidator classValidator = new UmlClassValidator();
    private final UmlRelationshipValidator relationshipValidator = new UmlRelationshipValidator();
    private final GeneralizationValidator generalizationValidator = new GeneralizationValidator();
    private final DiagramLayoutValidator layoutValidator = new DiagramLayoutValidator();
    private final UmlModelQualityValidator qualityValidator = new UmlModelQualityValidator();

    public void validate(ProjectDocument document) {
        ProjectDocumentValidationReport report = analyze(document);
        if (report.valid()) return;

        List<ValidationViolation> violations = report.diagnostics().stream()
                .filter(diagnostic -> diagnostic.severity() == ValidationSeverity.ERROR)
                .map(diagnostic -> new ValidationViolation(
                        diagnostic.field(), diagnostic.code(), diagnostic.message()))
                .toList();
        throw new ProjectDocumentValidationException(violations);
    }

    public ProjectDocumentValidationReport analyze(ProjectDocument document) {
        ProjectValidationCollector collector = new ProjectValidationCollector();
        if (document == null) {
            collector.error("document", "DOCUMENT_REQUIRED", null,
                    "El documento del proyecto es obligatorio.");
            return collector.report();
        }

        if (!ProjectDocument.CURRENT_SCHEMA_VERSION.equals(document.schemaVersion())) {
            collector.error("document.schemaVersion", "UNSUPPORTED_SCHEMA_VERSION", null,
                    "La version del documento no es compatible con esta version de ClassForge.");
        }

        List<UmlClass> classes = document.umlModel().classes();
        List<UmlRelationship> relationships = document.umlModel().relationships();
        if (classes.isEmpty()) {
            collector.warning("document.umlModel.classes", "MODEL_EMPTY", null,
                    "El modelo no contiene clases todavia.");
        }

        classValidator.validateClasses(classes, collector);
        relationshipValidator.validateRelationships(relationships, classes, collector);
        generalizationValidator.validateGeneralizationCycles(relationships, classes, collector);
        layoutValidator.validateLayout(document.layout().nodes(), classes, collector);
        qualityValidator.validateModelQuality(classes, relationships, collector);
        return collector.report();
    }
}
