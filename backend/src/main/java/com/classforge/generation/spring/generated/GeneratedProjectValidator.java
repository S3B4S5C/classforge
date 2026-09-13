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
import java.util.UUID;
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
        boolean hasManifest = textByPath.containsKey("domain-manifest.json");
        if (!hasOpenApi && !hasPostman && !hasManifest) return;
        if (!hasOpenApi || !hasPostman) {
            add(diagnostics, GeneratedProjectDiagnosticCode.API_CONTRACT_ARTIFACT_MISSING, null,
                    "OpenAPI and Postman artifacts must be generated together.");
            return;
        }
        if (!hasManifest) {
            add(diagnostics, GeneratedProjectDiagnosticCode.DOMAIN_MANIFEST_ARTIFACT_MISSING, "domain-manifest.json",
                    "Domain Manifest must be generated with the API contract artifacts.");
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

        validateDomainManifest(textByPath.get("domain-manifest.json"), openApiOperations, diagnostics);
    }

    private void validateDomainManifest(
            String json,
            Set<String> canonicalOperations,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        try {
            JsonNode root = JsonMapper.builder().build().readTree(json);
            if (root == null || !"1.0".equals(text(root, "schemaVersion"))) {
                throw new IllegalArgumentException("schemaVersion must be 1.0.");
            }
            String mode = text(root, "generationMode");
            if (!"SIMPLE_CRUD".equals(mode) && !"AUTH_INFORMATION_SYSTEM".equals(mode)) {
                throw new IllegalArgumentException("generationMode is invalid: " + mode);
            }
            JsonNode api = root.get("api");
            if (api == null
                    || !"http://localhost:8080".equals(text(api, "baseUrl"))
                    || !"openapi.yaml".equals(text(api, "openApiFile"))
                    || !"postman_collection.json".equals(text(api, "postmanFile"))) {
                throw new IllegalArgumentException("api metadata must reference the canonical generated contract files.");
            }

            JsonNode entities = root.get("entities");
            if (entities == null || !entities.isArray() || entities.size() == 0) {
                throw new IllegalArgumentException("entities must be a non-empty array.");
            }
            Set<String> entityIds = new HashSet<>();
            Map<String, Set<String>> attributeIdsByEntity = new HashMap<>();
            for (JsonNode entity : entities) {
                String entityId = uuidText(entity, "id");
                if (!entityIds.add(entityId)) {
                    throw new IllegalArgumentException("entity ids must be unique: " + entityId);
                }
                JsonNode attributes = entity.get("attributes");
                if (attributes == null || !attributes.isArray()) {
                    throw new IllegalArgumentException("entity attributes must be an array: " + entityId);
                }
                Set<String> attributeIds = new HashSet<>();
                for (JsonNode attribute : attributes) {
                    attributeIds.add(uuidText(attribute, "id"));
                }
                attributeIdsByEntity.put(entityId, attributeIds);

                JsonNode identifier = entity.get("identifier");
                if (identifier == null || identifier.get("fields") == null || !identifier.get("fields").isArray()
                        || identifier.get("fields").size() == 0) {
                    throw new IllegalArgumentException("entity identifier must declare fields: " + entityId);
                }
                for (JsonNode field : identifier.get("fields")) {
                    String attributeId = uuidText(field, "attributeId");
                    if (!attributeIds.contains(attributeId)) {
                        throw new IllegalArgumentException("identifier attribute does not resolve in entity " + entityId + ": " + attributeId);
                    }
                }
            }

            for (JsonNode entity : entities) {
                String entityId = text(entity, "id");
                JsonNode inheritance = entity.get("inheritance");
                if (inheritance != null && inheritance.has("superEntityId")) {
                    String superEntityId = uuidText(inheritance, "superEntityId");
                    if (!entityIds.contains(superEntityId)) {
                        add(diagnostics, GeneratedProjectDiagnosticCode.DOMAIN_MANIFEST_REFERENCE_INVALID, "domain-manifest.json",
                                "Inheritance superEntityId does not resolve: " + superEntityId);
                    }
                }
                JsonNode relations = entity.get("relations");
                if (relations != null && relations.isArray()) {
                    for (JsonNode relation : relations) {
                        uuidText(relation, "id");
                        String targetEntityId = uuidText(relation, "targetEntityId");
                        if (!entityIds.contains(targetEntityId)) {
                            add(diagnostics, GeneratedProjectDiagnosticCode.DOMAIN_MANIFEST_REFERENCE_INVALID, "domain-manifest.json",
                                    "Relation targetEntityId does not resolve: " + targetEntityId + " from " + entityId);
                        }
                    }
                }
            }

            JsonNode authentication = root.get("authentication");
            if (authentication == null || !authentication.has("enabled")) {
                throw new IllegalArgumentException("authentication.enabled is required.");
            }
            boolean authEnabled = authentication.get("enabled").asBoolean();
            if ("SIMPLE_CRUD".equals(mode) && authEnabled) {
                throw new IllegalArgumentException("Simple mode cannot enable authentication.");
            }
            if ("AUTH_INFORMATION_SYSTEM".equals(mode)) {
                if (!authEnabled || !"BEARER_JWT".equals(text(authentication, "scheme"))) {
                    throw new IllegalArgumentException("Auth mode requires BEARER_JWT authentication metadata.");
                }
                String authEntityId = uuidText(authentication, "entityId");
                String usernameAttributeId = uuidText(authentication, "usernameAttributeId");
                String passwordAttributeId = uuidText(authentication, "passwordAttributeId");
                if (!entityIds.contains(authEntityId)) {
                    throw new IllegalArgumentException("authentication.entityId does not resolve.");
                }
                Set<String> authAttributes = attributeIdsByEntity.get(authEntityId);
                if (!authAttributes.contains(usernameAttributeId) || !authAttributes.contains(passwordAttributeId)) {
                    throw new IllegalArgumentException("Authentication attribute ids must resolve in the auth entity.");
                }
                JsonNode password = findById(entities, authEntityId, passwordAttributeId);
                if (password == null
                        || !password.get("sensitive").asBoolean()
                        || !password.get("writeOnly").asBoolean()
                        || password.get("readable").asBoolean()
                        || password.get("searchable").asBoolean()
                        || password.get("filterable").asBoolean()
                        || password.get("sortable").asBoolean()) {
                    throw new IllegalArgumentException("Password privacy flags are invalid.");
                }
            }

            JsonNode operations = root.get("operations");
            if (operations == null || !operations.isArray()) {
                throw new IllegalArgumentException("operations must be an array.");
            }
            Set<String> manifestOperations = new HashSet<>();
            for (JsonNode operation : operations) {
                String operationId = text(operation, "operationId");
                if (operationId == null || operationId.isBlank()) {
                    throw new IllegalArgumentException("Manifest operationId is required.");
                }
                manifestOperations.add(operationId);
                if (operation.has("entityId")) {
                    String entityId = uuidText(operation, "entityId");
                    if (!entityIds.contains(entityId)) {
                        throw new IllegalArgumentException("Operation entityId does not resolve: " + entityId);
                    }
                }
            }
            if (!manifestOperations.equals(canonicalOperations)) {
                add(diagnostics, GeneratedProjectDiagnosticCode.DOMAIN_MANIFEST_OPERATION_MISMATCH, "domain-manifest.json",
                        "Domain Manifest operationIds must equal the canonical API contract. Manifest="
                                + manifestOperations + ", canonical=" + canonicalOperations);
            }
        } catch (Exception exception) {
            add(diagnostics, GeneratedProjectDiagnosticCode.DOMAIN_MANIFEST_ARTIFACT_INVALID, "domain-manifest.json",
                    "Generated Domain Manifest is not valid for schema v1: " + exception.getMessage());
        }
    }

    private JsonNode findById(JsonNode entities, String entityId, String attributeId) {
        for (JsonNode entity : entities) {
            if (!entityId.equals(text(entity, "id"))) continue;
            JsonNode attributes = entity.get("attributes");
            if (attributes == null || !attributes.isArray()) return null;
            for (JsonNode attribute : attributes) {
                if (attributeId.equals(text(attribute, "id"))) return attribute;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String uuidText(JsonNode node, String field) {
        String value = text(node, field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required.");
        UUID.fromString(value);
        return value;
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
