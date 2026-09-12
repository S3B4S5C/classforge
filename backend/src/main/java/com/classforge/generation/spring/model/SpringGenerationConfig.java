package com.classforge.generation.spring.model;

import com.classforge.generation.spring.validation.SpringGenerationDiagnostic;
import com.classforge.generation.spring.validation.SpringGenerationDiagnosticCode;
import com.classforge.generation.spring.validation.SpringGenerationException;
import java.util.List;
import java.util.Set;

public record SpringGenerationConfig(String artifactName, String basePackage) {
    private static final String ARTIFACT_PATTERN = "^[a-z][a-z0-9-]{0,62}$";
    private static final String PACKAGE_PATTERN = "^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*$";
    private static final Set<String> JAVA_KEYWORDS = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void", "volatile", "while", "_", "exports", "module", "non-sealed", "open", "opens", "permits", "provides", "record", "requires", "sealed", "to", "transitive", "uses", "var", "with", "yield", "true", "false", "null");

    public SpringGenerationConfig {
        if (artifactName == null || !artifactName.matches(ARTIFACT_PATTERN)) {
            throw failure(SpringGenerationDiagnosticCode.INVALID_ARTIFACT_NAME, "artifactName", "artifactName must match " + ARTIFACT_PATTERN + ".");
        }
        if (basePackage == null || !basePackage.matches(PACKAGE_PATTERN)
                || java.util.Arrays.stream(basePackage.split("\\.")).anyMatch(JAVA_KEYWORDS::contains)) {
            throw failure(SpringGenerationDiagnosticCode.INVALID_BASE_PACKAGE, "basePackage", "basePackage must be lowercase Java package segments that are not Java keywords.");
        }
    }

    private static SpringGenerationException failure(SpringGenerationDiagnosticCode code, String path, String message) {
        return new SpringGenerationException(List.of(new SpringGenerationDiagnostic(code, null, path, message)));
    }
}
