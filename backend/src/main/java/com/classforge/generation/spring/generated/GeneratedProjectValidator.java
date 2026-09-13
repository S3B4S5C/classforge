package com.classforge.generation.spring.generated;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class GeneratedProjectValidator {
    private static final List<String> REQUIRED_PATHS = List.of(
            "build.gradle",
            "settings.gradle",
            "gradlew",
            "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties",
            ".gitignore",
            "README.md",
            "src/main/resources/application.yml",
            "src/main/resources/application-postgres.yml",
            "src/test/resources/application.yml"
    );
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
    private static final Pattern FREEMARKER_EXPRESSION = Pattern.compile("\\$\\{([^}\\r\\n]+)}");
    private static final Pattern OPENAPI_OPERATION_ID = Pattern.compile("(?m)^\\s+operationId:\\s*([A-Za-z0-9_]+)\\s*$");
    private static final Set<String> NON_GENERATED_SIMPLE_TYPES = Set.of(
            "String", "Integer", "Long", "Boolean", "BigDecimal", "LocalDate", "LocalDateTime", "UUID",
            "Set", "LinkedHashSet", "Serializable", "Object"
    );
    private static final Set<String> ALLOWED_RUNTIME_PLACEHOLDERS = Set.of(
            "DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET", "app.security.jwt-secret", "app.security.jwt-expiration-seconds"
    );

    public void validate(GeneratedProject project) {
        List<GeneratedProjectDiagnostic> diagnostics = new ArrayList<>();
        if (project == null || project.artifactName() == null || project.artifactName().isBlank()
                || project.files().isEmpty()) {
            add(diagnostics, GeneratedProjectDiagnosticCode.INVALID_PROJECT, null,
                    "Project artifact and files are required.");
        }
        if (project == null) {
            throw new GeneratedProjectException(diagnostics);
        }

        Set<String> paths = new HashSet<>();
        Set<String> insensitive = new HashSet<>();
        Map<String, GeneratedFile> filesByPath = new HashMap<>();
        Map<String, String> textByPath = new HashMap<>();

        for (GeneratedFile file : project.files()) {
            String path = file.path();
            if (!validPath(path)) {
                add(diagnostics, GeneratedProjectDiagnosticCode.INVALID_PATH, path,
                        "Generated path must be a safe relative POSIX path.");
            } else {
                if (!paths.add(path) || !insensitive.add(path.toLowerCase(Locale.ROOT))) {
                    add(diagnostics, GeneratedProjectDiagnosticCode.DUPLICATE_PATH, path,
                            "Generated path is duplicated.");
                }
                filesByPath.putIfAbsent(path, file);
            }
            if (file.type() == GeneratedFileType.TEXT) {
                String text = validateText(file, diagnostics);
                if (text != null && path != null) {
                    textByPath.put(path, text);
                    validateTemplateMarkers(path, text, diagnostics);
                }
            }
        }

        validateRequiredSkeleton(filesByPath, textByPath, diagnostics);
        validateJavaSources(textByPath, diagnostics);
        validateApiArtifacts(textByPath, diagnostics);

        if (!diagnostics.isEmpty()) {
            throw new GeneratedProjectException(diagnostics);
        }
    }


    private void validateApiArtifacts(
            Map<String, String> textByPath,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        boolean hasOpenApi = textByPath.containsKey("openapi.yaml");
        boolean hasPostman = textByPath.containsKey("postman_collection.json");
        if (!hasOpenApi && !hasPostman) return;
        if (!hasOpenApi || !hasPostman) {
            add(diagnostics, GeneratedProjectDiagnosticCode.API_CONTRACT_ARTIFACT_MISSING, null,
                    "OpenAPI and Postman artifacts must be generated together.");
            return;
        }

        Set<String> openApiOperations = new HashSet<>();
        try {
            String yaml = textByPath.get("openapi.yaml");
            YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
            factory.setResources(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
            Properties properties = factory.getObject();
            if (properties == null || !"3.0.3".equals(properties.getProperty("openapi"))) {
                throw new IllegalArgumentException("OpenAPI root version must be 3.0.3.");
            }
            Matcher matcher = OPENAPI_OPERATION_ID.matcher(yaml);
            while (matcher.find()) openApiOperations.add(matcher.group(1));
            if (openApiOperations.isEmpty()) {
                throw new IllegalArgumentException("OpenAPI must contain at least one operationId.");
            }
        } catch (RuntimeException exception) {
            add(diagnostics, GeneratedProjectDiagnosticCode.API_CONTRACT_ARTIFACT_INVALID, "openapi.yaml",
                    "Generated OpenAPI is not parseable/valid for the ClassForge contract: " + exception.getMessage());
            return;
        }

        Set<String> postmanOperations = new HashSet<>();
        try {
            JsonNode root = JsonMapper.builder().build().readTree(textByPath.get("postman_collection.json"));
            if (root == null || root.get("info") == null || root.get("info").get("schema") == null
                    || !root.get("info").get("schema").asText().contains("/v2.1.0/")) {
                throw new IllegalArgumentException("Postman collection schema must be v2.1.");
            }
            collectPostmanOperationIds(root.get("item"), postmanOperations);
            if (postmanOperations.isEmpty()) {
                throw new IllegalArgumentException("Postman collection must contain requests.");
            }
        } catch (Exception exception) {
            add(diagnostics, GeneratedProjectDiagnosticCode.API_CONTRACT_ARTIFACT_INVALID, "postman_collection.json",
                    "Generated Postman collection is not parseable/valid for the ClassForge contract: " + exception.getMessage());
            return;
        }

        if (!openApiOperations.equals(postmanOperations)) {
            add(diagnostics, GeneratedProjectDiagnosticCode.API_CONTRACT_OPERATION_MISMATCH, null,
                    "OpenAPI operationIds and Postman request names must match exactly. OpenAPI="
                            + openApiOperations + ", Postman=" + postmanOperations);
        }
    }

    private void collectPostmanOperationIds(JsonNode items, Set<String> operations) {
        if (items == null || !items.isArray()) return;
        for (JsonNode item : items) {
            if (item.has("request") && item.has("name")) {
                operations.add(item.get("name").asText());
            } else {
                collectPostmanOperationIds(item.get("item"), operations);
            }
        }
    }

    private boolean validPath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/")
                || path.matches("^[A-Za-z]:.*") || path.contains("\\")) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private String validateText(
            GeneratedFile file,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.content()));
        } catch (CharacterCodingException exception) {
            add(diagnostics, GeneratedProjectDiagnosticCode.INVALID_TEXT_ENCODING, file.path(),
                    "Text content is not valid UTF-8.");
            return null;
        }
        byte[] content = file.content();
        for (byte value : content) {
            if (value == '\r') {
                add(diagnostics, GeneratedProjectDiagnosticCode.INVALID_LINE_ENDINGS, file.path(),
                        "Text content must use LF only.");
                break;
            }
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private void validateRequiredSkeleton(
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

    private void validateJavaSources(
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

    private void validateSamePackageReference(
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

    private void requireGeneratedTypeIfInternalCandidate(
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

    private void validateTemplateMarkers(
            String path,
            String text,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        if (text.contains("<#") || text.contains("</#") || text.contains("<@")
                || text.contains("</@") || text.contains("[#") || text.contains("[/#")) {
            add(diagnostics, GeneratedProjectDiagnosticCode.UNRESOLVED_TEMPLATE_MARKER, path,
                    "Generated text still contains a FreeMarker directive.");
        }
        if (path.equals("gradlew") || path.equals("gradlew.bat")) {
            return;
        }
        Matcher matcher = FREEMARKER_EXPRESSION.matcher(text);
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            String key = expression.contains(":")
                    ? expression.substring(0, expression.indexOf(':'))
                    : expression;
            if (!ALLOWED_RUNTIME_PLACEHOLDERS.contains(key)) {
                add(diagnostics, GeneratedProjectDiagnosticCode.UNRESOLVED_TEMPLATE_MARKER, path,
                        "Generated text still contains an unresolved template expression: ${" + expression + "}");
            }
        }
    }

    private void add(
            List<GeneratedProjectDiagnostic> diagnostics,
            GeneratedProjectDiagnosticCode code,
            String path,
            String message
    ) {
        diagnostics.add(new GeneratedProjectDiagnostic(code, path, message));
    }

    private record JavaSource(String path, String packageName, String simpleName, String text) {
        String fqcn() {
            return packageName + "." + simpleName;
        }
    }
}
