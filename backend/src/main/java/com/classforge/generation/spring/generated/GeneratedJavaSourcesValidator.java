package com.classforge.generation.spring.generated;

import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class GeneratedJavaSourcesValidator {
    private static final Pattern PACKAGE = Pattern.compile(
            "(?m)^package\\s+([A-Za-z_$][A-Za-z0-9_$]*(?:\\.[A-Za-z_$][A-Za-z0-9_$]*)*)\\s*;"
    );
    private static final Pattern TOP_LEVEL_TYPE = Pattern.compile(
            "(?m)^(?:public\\s+)?(?:(?:abstract|final|sealed|non-sealed)\\s+)*(?:class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\b"
    );
    private static final Pattern IMPORT = Pattern.compile(
            "(?m)^import\\s+([A-Za-z_$][A-Za-z0-9_$.]*)(\\.\\*)?\\s*;"
    );
    private static final Pattern EXTENDS = Pattern.compile("\\bextends\\s+([A-Z][A-Za-z0-9_$]*)\\b");
    private static final Pattern ID_CLASS = Pattern.compile("@IdClass\\(\\s*([A-Z][A-Za-z0-9_$]*)\\.class\\s*\\)");
    private static final Pattern FIELD = Pattern.compile(
            "(?m)^\\s*private\\s+(?:final\\s+)?([A-Z][A-Za-z0-9_$]*)(?:<([A-Z][A-Za-z0-9_$]*)>)?\\s+[A-Za-z_$][A-Za-z0-9_$]*\\s*(?:=|;)"
    );
    private static final Set<String> NON_GENERATED_SIMPLE_TYPES = Set.of(
            "String", "Integer", "Long", "Boolean", "BigDecimal", "LocalDate", "LocalDateTime", "UUID",
            "Set", "LinkedHashSet", "Serializable", "Object");

    void validateJavaSources(
            Map<String, String> textByPath,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        Map<String, JavaSource> sourcesByFqcn = new HashMap<>();
        List<JavaSource> sources = new ArrayList<>();

        for (Map.Entry<String, String> entry : textByPath.entrySet()) {
            String path = entry.getKey();
            if (!path.endsWith(".java")) {
                continue;
            }
            if (!path.startsWith("src/main/java/") && !path.startsWith("src/test/java/")) {
                add(diagnostics, GeneratedProjectDiagnosticCode.PACKAGE_PATH_MISMATCH, path,
                        "Generated Java sources must live below src/main/java or src/test/java.");
                continue;
            }

            Matcher packageMatcher = PACKAGE.matcher(entry.getValue());
            Matcher typeMatcher = TOP_LEVEL_TYPE.matcher(entry.getValue());
            if (!packageMatcher.find() || !typeMatcher.find()) {
                add(diagnostics, GeneratedProjectDiagnosticCode.PACKAGE_PATH_MISMATCH, path,
                        "Generated Java source must declare one package and a top-level type.");
                continue;
            }

            String packageName = packageMatcher.group(1);
            String simpleName = typeMatcher.group(1);
            String sourceRoot = path.startsWith("src/main/java/") ? "src/main/java/" : "src/test/java/";
            String expectedDirectory = sourceRoot + packageName.replace('.', '/') + "/";
            String fileName = simpleName + ".java";
            if (!path.equals(expectedDirectory + fileName)) {
                add(diagnostics, GeneratedProjectDiagnosticCode.PACKAGE_PATH_MISMATCH, path,
                        "Java package/type declaration does not match the generated source path.");
            }

            JavaSource source = new JavaSource(path, packageName, simpleName, entry.getValue());
            sources.add(source);
            JavaSource prior = sourcesByFqcn.putIfAbsent(source.fqcn(), source);
            if (prior != null) {
                add(diagnostics, GeneratedProjectDiagnosticCode.GENERATED_TYPE_REFERENCE_MISSING, path,
                        "Generated Java type is declared more than once: " + source.fqcn());
            }
        }

        String basePackage = sources.stream()
                .filter(source -> source.path().startsWith("src/main/java/")
                        && source.text().contains("@SpringBootApplication"))
                .map(JavaSource::packageName)
                .findFirst()
                .orElse(null);
        if (basePackage == null) {
            return;
        }

        Set<String> generatedTypes = Set.copyOf(sourcesByFqcn.keySet());
        for (JavaSource source : sources) {
            Set<String> externalImportedSimpleNames = new HashSet<>();
            Matcher imports = IMPORT.matcher(source.text());
            while (imports.find()) {
                String imported = imports.group(1);
                boolean wildcard = imports.group(2) != null;
                if (imported.startsWith(basePackage + ".")) {
                    if (wildcard || !generatedTypes.contains(imported)) {
                        add(diagnostics, GeneratedProjectDiagnosticCode.GENERATED_TYPE_REFERENCE_MISSING,
                                source.path(), "Generated type import does not resolve: " + imported);
                    }
                } else if (!wildcard) {
                    externalImportedSimpleNames.add(imported.substring(imported.lastIndexOf('.') + 1));
                }
            }

            if (!source.path().startsWith("src/main/java/") || !source.packageName().endsWith(".entity")) {
                continue;
            }
            validateSamePackageReference(source, EXTENDS, generatedTypes, diagnostics);
            validateSamePackageReference(source, ID_CLASS, generatedTypes, diagnostics);

            Matcher fields = FIELD.matcher(source.text());
            while (fields.find()) {
                String rawType = fields.group(1);
                String genericType = fields.group(2);
                if (genericType != null) {
                    requireGeneratedTypeIfInternalCandidate(
                            source, genericType, externalImportedSimpleNames, generatedTypes, diagnostics
                    );
                } else {
                    requireGeneratedTypeIfInternalCandidate(
                            source, rawType, externalImportedSimpleNames, generatedTypes, diagnostics
                    );
                }
            }
        }
    }

    void validateSamePackageReference(
            JavaSource source,
            Pattern pattern,
            Set<String> generatedTypes,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        Matcher matcher = pattern.matcher(source.text());
        while (matcher.find()) {
            String simpleName = matcher.group(1);
            String fqcn = source.packageName() + "." + simpleName;
            if (!generatedTypes.contains(fqcn)) {
                add(diagnostics, GeneratedProjectDiagnosticCode.GENERATED_TYPE_REFERENCE_MISSING,
                        source.path(), "Generated type reference does not resolve: " + fqcn);
            }
        }
    }

    void requireGeneratedTypeIfInternalCandidate(
            JavaSource source,
            String simpleName,
            Set<String> externalImportedSimpleNames,
            Set<String> generatedTypes,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        if (NON_GENERATED_SIMPLE_TYPES.contains(simpleName)
                || externalImportedSimpleNames.contains(simpleName)) {
            return;
        }
        String fqcn = source.packageName() + "." + simpleName;
        if (!generatedTypes.contains(fqcn)) {
            add(diagnostics, GeneratedProjectDiagnosticCode.GENERATED_TYPE_REFERENCE_MISSING,
                    source.path(), "Generated field type does not resolve: " + fqcn);
        }
    }

    private void add(List<GeneratedProjectDiagnostic> diagnostics, GeneratedProjectDiagnosticCode code, String path, String message) {
        diagnostics.add(new GeneratedProjectDiagnostic(code, path, message));
    }

    private record JavaSource(String path, String packageName, String simpleName, String text) {
        String fqcn() { return packageName + "." + simpleName; }
    }
}
