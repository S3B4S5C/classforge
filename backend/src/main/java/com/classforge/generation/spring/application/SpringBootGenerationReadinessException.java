package com.classforge.generation.spring.application;

import com.classforge.generation.relational.RelationalMappingDiagnostic;
import java.util.List;

public class SpringBootGenerationReadinessException extends RuntimeException {
    private final List<RelationalMappingDiagnostic> diagnostics;
    private final List<SpringBootGenerationPrimaryKeyFallback> primaryKeyFallbacks;

    public SpringBootGenerationReadinessException(
            List<RelationalMappingDiagnostic> diagnostics,
            List<SpringBootGenerationPrimaryKeyFallback> primaryKeyFallbacks
    ) {
        super("Spring Boot generation needs explicit primary-key confirmation");
        this.diagnostics = List.copyOf(diagnostics);
        this.primaryKeyFallbacks = List.copyOf(primaryKeyFallbacks);
    }

    public List<RelationalMappingDiagnostic> diagnostics() {
        return diagnostics;
    }

    public List<SpringBootGenerationPrimaryKeyFallback> primaryKeyFallbacks() {
        return primaryKeyFallbacks;
    }
}
