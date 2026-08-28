package com.classforge.project.domain.document;

import java.util.Objects;

public record ProjectDocument(
        String schemaVersion,
        UmlModel umlModel,
        DiagramLayout layout
) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public ProjectDocument {
        Objects.requireNonNull(schemaVersion, "schemaVersion is required");
        umlModel = umlModel == null ? UmlModel.empty() : umlModel;
        layout = layout == null ? DiagramLayout.empty() : layout;
    }

    public static ProjectDocument empty() {
        return new ProjectDocument(
                CURRENT_SCHEMA_VERSION,
                UmlModel.empty(),
                DiagramLayout.empty()
        );
    }
}