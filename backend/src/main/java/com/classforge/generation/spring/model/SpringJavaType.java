package com.classforge.generation.spring.model;

import com.classforge.generation.relational.model.RelationalDataType;

public enum SpringJavaType {
    STRING("String", "java.lang.String"), INTEGER("Integer", "java.lang.Integer"),
    LONG("Long", "java.lang.Long"), BIG_DECIMAL("BigDecimal", "java.math.BigDecimal"),
    BOOLEAN("Boolean", "java.lang.Boolean"), LOCAL_DATE("LocalDate", "java.time.LocalDate"),
    LOCAL_DATE_TIME("LocalDateTime", "java.time.LocalDateTime"), UUID("UUID", "java.util.UUID");

    private final String simpleName;
    private final String qualifiedName;

    SpringJavaType(String simpleName, String qualifiedName) { this.simpleName = simpleName; this.qualifiedName = qualifiedName; }
    public String simpleName() { return simpleName; }
    public String qualifiedName() { return qualifiedName; }
    public static SpringJavaType from(RelationalDataType type) {
        return switch (type) {
            case VARCHAR -> STRING; case INTEGER -> INTEGER; case BIGINT -> LONG; case DECIMAL -> BIG_DECIMAL;
            case BOOLEAN -> BOOLEAN; case DATE -> LOCAL_DATE; case TIMESTAMP -> LOCAL_DATE_TIME; case UUID -> UUID;
        };
    }
}
