package com.classforge.generation.spring.model;

import java.util.List;

public record SpringEntityIdModel(SpringIdKind kind, SpringJavaType javaType, String idClassName,
                                  List<SpringIdFieldModel> fields, boolean declaredByEntity) {
    public SpringEntityIdModel { fields = List.copyOf(fields == null ? List.of() : fields); }
    public String typeSimpleName() { return kind == SpringIdKind.SIMPLE ? javaType.simpleName() : idClassName; }
    public String typeQualifiedName() { return kind == SpringIdKind.SIMPLE ? javaType.qualifiedName() : null; }
}
