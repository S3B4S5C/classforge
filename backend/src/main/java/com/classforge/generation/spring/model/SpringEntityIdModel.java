package com.classforge.generation.spring.model;

import java.util.List;

public record SpringEntityIdModel(SpringIdKind kind, SpringJavaType javaType, String idClassName,
                                  List<SpringIdFieldModel> fields, boolean declaredByEntity) {
    public SpringEntityIdModel { fields = List.copyOf(fields == null ? List.of() : fields); }
    public String typeSimpleName() { return kind == SpringIdKind.SIMPLE ? javaType.simpleName() : idClassName; }
    public String typeQualifiedName() { return kind == SpringIdKind.SIMPLE ? javaType.qualifiedName() : null; }

    /**
     * Returns true only for simple identifiers that have a safe, generic generation strategy.
     * Composite IDs and semantic scalar IDs (decimal/boolean/date/datetime) remain explicit.
     */
    public boolean automaticallyGenerated() {
        if (kind != SpringIdKind.SIMPLE || javaType == null) return false;
        return switch (javaType) {
            case UUID, INTEGER, LONG, STRING -> true;
            default -> false;
        };
    }

    public boolean generatedByJpa() {
        return automaticallyGenerated() && javaType != SpringJavaType.STRING;
    }

    public String jpaGenerationType() {
        if (!generatedByJpa()) return null;
        return javaType == SpringJavaType.UUID ? "UUID" : "IDENTITY";
    }

    public boolean generatedAsRandomUuidString() {
        return automaticallyGenerated() && javaType == SpringJavaType.STRING;
    }
}
