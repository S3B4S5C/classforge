package com.classforge.project.domain.document;

import java.util.UUID;

public record UmlAttribute(
        UUID id,
        String name,
        UmlDataType dataType,
        String customTypeName,
        UmlVisibility visibility,
        boolean nullable,
        boolean identifier
) {
}