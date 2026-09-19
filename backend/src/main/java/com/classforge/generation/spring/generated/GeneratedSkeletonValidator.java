package com.classforge.generation.spring.generated;

import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class GeneratedSkeletonValidator {
    private static final List<String> REQUIRED_PATHS = List.of(
            "build.gradle", "settings.gradle", "gradlew", "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
            ".gitignore", "README.md", "src/main/resources/application.yml",
            "src/main/resources/application-postgres.yml", "src/test/resources/application.yml");

    void validateRequiredSkeleton(
            Map<String, GeneratedFile> filesByPath,
            Map<String, String> textByPath,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        for (String required : REQUIRED_PATHS) {
            if (!filesByPath.containsKey(required)) {
                add(diagnostics, GeneratedProjectDiagnosticCode.REQUIRED_SKELETON_MISSING, required,
                        "Required generated-project file is missing.");
            }
        }

        List<Map.Entry<String, String>> applications = textByPath.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("src/main/java/") && entry.getKey().endsWith(".java"))
                .filter(entry -> entry.getValue().contains("@SpringBootApplication"))
                .toList();
        if (applications.size() != 1) {
            add(diagnostics, GeneratedProjectDiagnosticCode.APPLICATION_BOOTSTRAP_INVALID, null,
                    "Generated project must contain exactly one @SpringBootApplication bootstrap class.");
        }

        List<Map.Entry<String, String>> contextTests = textByPath.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("src/test/java/") && entry.getKey().endsWith(".java"))
                .filter(entry -> entry.getValue().contains("@SpringBootTest")
                        && entry.getValue().matches("(?s).*\\bvoid\\s+contextLoads\\s*\\(\\s*\\).*"))
                .toList();
        if (contextTests.size() != 1) {
            add(diagnostics, GeneratedProjectDiagnosticCode.REQUIRED_SKELETON_MISSING, "src/test/java",
                    "Generated project must contain exactly one Spring context-load test.");
        }
    }

    private void add(List<GeneratedProjectDiagnostic> diagnostics, GeneratedProjectDiagnosticCode code, String path, String message) {
        diagnostics.add(new GeneratedProjectDiagnostic(code, path, message));
    }
}
