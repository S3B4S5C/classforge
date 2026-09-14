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
            "DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET",
            "LLAMA_URL", "LLAMA_MODEL", "WHISPER_URL", "WHISPER_LANGUAGE", "APP_API_BASE_URL",
            "app.security.jwt-secret", "app.security.jwt-expiration-seconds",
            "app.assistant.llama-url", "app.assistant.llama-model", "app.assistant.whisper-url",
            "app.assistant.whisper-language", "app.assistant.api-base-url"
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
        validateAngularFrontend(textByPath, textByPath.get("domain-manifest.json"), diagnostics);
        validateFlutterMobile(textByPath, textByPath.get("domain-manifest.json"), diagnostics);
        validateGeneratedAssistant(textByPath, diagnostics);
    }

    private void validateAngularFrontend(
            Map<String, String> textByPath,
            String manifestJson,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        List<String> required = List.of(
                "frontend/package.json",
                "frontend/angular.json",
                "frontend/tsconfig.json",
                "frontend/tsconfig.app.json",
                "frontend/src/index.html",
                "frontend/src/main.ts",
                "frontend/src/styles.css",
                "frontend/src/app/app.component.ts",
                "frontend/src/app/app.routes.ts",
                "frontend/src/app/dashboard/dashboard.component.ts",
                "frontend/src/app/assistant/assistant.service.ts",
                "frontend/src/app/assistant/browser-wav-recorder.service.ts",
                "frontend/src/app/assistant/assistant.component.ts"
        );
        for (String path : required) {
            if (!textByPath.containsKey(path)) {
                add(diagnostics, GeneratedProjectDiagnosticCode.ANGULAR_FRONTEND_ARTIFACT_MISSING, path,
                        "CU-17 Angular frontend file is missing.");
            }
        }
        if (required.stream().anyMatch(path -> !textByPath.containsKey(path))) return;

        try {
            JsonNode packageJson = JsonMapper.builder().build().readTree(textByPath.get("frontend/package.json"));
            if (packageJson == null || packageJson.path("dependencies").path("@angular/core").asText().isBlank()) {
                throw new IllegalArgumentException("package.json must declare Angular runtime dependencies.");
            }
            JsonNode angularJson = JsonMapper.builder().build().readTree(textByPath.get("frontend/angular.json"));
            if (angularJson == null || angularJson.path("projects").isMissingNode() || angularJson.path("projects").size() == 0) {
                throw new IllegalArgumentException("angular.json must declare one generated application.");
            }
            JsonNode manifest = JsonMapper.builder().build().readTree(manifestJson);
            for (JsonNode entity : manifest.path("entities")) {
                String codeName = text(entity, "codeName");
                if (codeName == null || codeName.isBlank()) {
                    throw new IllegalArgumentException("Domain Manifest entity codeName is required for Angular generation.");
                }
                String kebab = kebab(codeName);
                String base = "frontend/src/app/entities/" + kebab + "/";
                for (String suffix : List.of(
                        kebab + ".models.ts",
                        kebab + ".api.ts",
                        kebab + "-list.component.ts",
                        kebab + "-detail.component.ts",
                        kebab + "-form.component.ts"
                )) {
                    if (!textByPath.containsKey(base + suffix)) {
                        add(diagnostics, GeneratedProjectDiagnosticCode.ANGULAR_FRONTEND_ARTIFACT_MISSING,
                                base + suffix, "Every manifest entity requires specific CU-17 Angular artifacts.");
                    }
                }
            }

            boolean authEnabled = manifest.path("authentication").path("enabled").asBoolean(false);
            if (authEnabled) {
                for (String path : List.of(
                        "frontend/src/app/core/auth/auth.service.ts",
                        "frontend/src/app/core/auth/auth.interceptor.ts",
                        "frontend/src/app/core/auth/auth.guard.ts",
                        "frontend/src/app/auth/login.component.ts",
                        "frontend/src/app/auth/bootstrap.component.ts"
                )) {
                    if (!textByPath.containsKey(path)) {
                        add(diagnostics, GeneratedProjectDiagnosticCode.ANGULAR_FRONTEND_ARTIFACT_MISSING, path,
                                "AUTH_INFORMATION_SYSTEM requires login/bootstrap/JWT Angular support.");
                    }
                }
            } else {
                boolean leakedAuth = textByPath.keySet().stream().anyMatch(path ->
                        path.startsWith("frontend/src/app/core/auth/") || path.startsWith("frontend/src/app/auth/"));
                if (leakedAuth) {
                    add(diagnostics, GeneratedProjectDiagnosticCode.ANGULAR_FRONTEND_ARTIFACT_INVALID,
                            "frontend/src/app", "SIMPLE_CRUD frontend must not contain generated authentication screens/services.");
                }
            }

            String styles = textByPath.get("frontend/src/styles.css");
            if (styles == null || !styles.matches("(?s).*--app-primary:\\s*#[0-9A-F]{6};.*")) {
                throw new IllegalArgumentException("styles.css must contain the selected #RRGGBB primary color.");
            }
            String assistantComponent = textByPath.get("frontend/src/app/assistant/assistant.component.ts");
            if (assistantComponent == null || !assistantComponent.contains("previewToken")
                    || assistantComponent.contains("pending.command") || assistantComponent.contains("command | json")) {
                throw new IllegalArgumentException("CU-19 Angular preview must use opaque previewToken and must not expose raw command values.");
            }
            String recorder = textByPath.get("frontend/src/app/assistant/browser-wav-recorder.service.ts");
            if (recorder == null || !recorder.contains("16_000") || !recorder.contains("audio/wav")) {
                throw new IllegalArgumentException("CU-19 Angular voice capture must emit 16 kHz WAV PCM.");
            }
        } catch (Exception exception) {
            add(diagnostics, GeneratedProjectDiagnosticCode.ANGULAR_FRONTEND_ARTIFACT_INVALID, "frontend",
                    "Generated Angular frontend is structurally invalid: " + exception.getMessage());
        }
    }


    private void validateFlutterMobile(
            Map<String, String> textByPath,
            String manifestJson,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        List<String> required = List.of(
                "mobile/pubspec.yaml",
                "mobile/analysis_options.yaml",
                "mobile/lib/main.dart",
                "mobile/lib/app/app.dart",
                "mobile/lib/core/theme/app_theme.dart",
                "mobile/lib/core/api/api_client.dart",
                "mobile/lib/dashboard/dashboard_page.dart",
                "mobile/lib/assistant/assistant_api.dart",
                "mobile/lib/assistant/assistant_page.dart",
                "mobile/android/settings.gradle.kts",
                "mobile/android/build.gradle.kts",
                "mobile/android/gradle.properties",
                "mobile/android/gradlew",
                "mobile/android/gradlew.bat",
                "mobile/android/gradle/wrapper/gradle-wrapper.jar",
                "mobile/android/gradle/wrapper/gradle-wrapper.properties",
                "mobile/android/app/build.gradle.kts",
                "mobile/android/app/src/main/AndroidManifest.xml"
        );
        for (String path : required) {
            boolean exists = path.endsWith(".jar") ? true : textByPath.containsKey(path);
            if (!exists && !path.endsWith(".jar")) {
                add(diagnostics, GeneratedProjectDiagnosticCode.FLUTTER_MOBILE_ARTIFACT_MISSING, path,
                        "CU-18 Flutter mobile file is missing.");
            }
        }
        if (required.stream().filter(path -> !path.endsWith(".jar")).anyMatch(path -> !textByPath.containsKey(path))) return;

        try {
            String pubspec = textByPath.get("mobile/pubspec.yaml");
            if (pubspec == null || !pubspec.contains("http:") || !pubspec.contains("record: ^7.1.1")
                    || !pubspec.contains("path_provider:")) {
                throw new IllegalArgumentException("pubspec.yaml must declare HTTP + CU-19 voice recording dependencies.");
            }
            String theme = textByPath.get("mobile/lib/core/theme/app_theme.dart");
            if (theme == null || !theme.matches("(?s).*Color\\(0xFF[0-9A-F]{6}\\).*")) {
                throw new IllegalArgumentException("Flutter theme must contain selected #RRGGBB primary color.");
            }
            JsonNode manifest = JsonMapper.builder().build().readTree(manifestJson);
            for (JsonNode entity : manifest.path("entities")) {
                String codeName = text(entity, "codeName");
                if (codeName == null || codeName.isBlank()) {
                    throw new IllegalArgumentException("Domain Manifest entity codeName is required for Flutter generation.");
                }
                String snake = snake(codeName);
                String base = "mobile/lib/entities/" + snake + "/";
                for (String suffix : List.of(
                        snake + "_model.dart",
                        snake + "_api.dart",
                        snake + "_list_page.dart",
                        snake + "_detail_page.dart",
                        snake + "_form_page.dart"
                )) {
                    if (!textByPath.containsKey(base + suffix)) {
                        add(diagnostics, GeneratedProjectDiagnosticCode.FLUTTER_MOBILE_ARTIFACT_MISSING,
                                base + suffix, "Every manifest entity requires specific CU-18 Flutter artifacts.");
                    }
                }
            }
            boolean authEnabled = manifest.path("authentication").path("enabled").asBoolean(false);
            List<String> authFiles = List.of(
                    "mobile/lib/core/auth/token_store.dart",
                    "mobile/lib/core/auth/auth_api.dart",
                    "mobile/lib/core/auth/auth_gate.dart",
                    "mobile/lib/auth/login_page.dart",
                    "mobile/lib/auth/bootstrap_page.dart"
            );
            if (authEnabled) {
                for (String path : authFiles) {
                    if (!textByPath.containsKey(path)) {
                        add(diagnostics, GeneratedProjectDiagnosticCode.FLUTTER_MOBILE_ARTIFACT_MISSING, path,
                                "AUTH_INFORMATION_SYSTEM requires secure token/login/bootstrap Flutter support.");
                    }
                }
                String tokenStore = textByPath.get("mobile/lib/core/auth/token_store.dart");
                if (tokenStore == null || !tokenStore.contains("FlutterSecureStorage") || !pubspec.contains("flutter_secure_storage:")) {
                    throw new IllegalArgumentException("Auth Flutter frontend must use flutter_secure_storage.");
                }
            } else {
                boolean leakedAuth = authFiles.stream().anyMatch(textByPath::containsKey);
                if (leakedAuth) {
                    add(diagnostics, GeneratedProjectDiagnosticCode.FLUTTER_MOBILE_ARTIFACT_INVALID,
                            "mobile/lib", "SIMPLE_CRUD mobile frontend must not contain generated authentication screens/services.");
                }
            }
            String manifestText = textByPath.get("mobile/android/app/src/main/AndroidManifest.xml");
            if (manifestText == null || !manifestText.contains("android.permission.INTERNET")
                    || !manifestText.contains("android.permission.RECORD_AUDIO")) {
                throw new IllegalArgumentException("Android manifest must permit HTTP API access and CU-19 microphone capture.");
            }
            String assistantPage = textByPath.get("mobile/lib/assistant/assistant_page.dart");
            if (assistantPage == null || !assistantPage.contains("AudioEncoder.wav") || !assistantPage.contains("sampleRate: 16000")) {
                throw new IllegalArgumentException("CU-19 Flutter voice capture must use WAV at 16 kHz.");
            }
        } catch (Exception exception) {
            add(diagnostics, GeneratedProjectDiagnosticCode.FLUTTER_MOBILE_ARTIFACT_INVALID, "mobile",
                    "Generated Flutter mobile project is structurally invalid: " + exception.getMessage());
        }
    }

    private void validateGeneratedAssistant(
            Map<String, String> textByPath,
            List<GeneratedProjectDiagnostic> diagnostics
    ) {
        List<String> names = List.of(
                "GeneratedAssistantTypes.java",
                "GeneratedAssistantMetadata.java",
                "GeneratedAssistantLlamaGateway.java",
                "GeneratedAssistantWhisperGateway.java",
                "GeneratedAssistantHttpExecutor.java",
                "GeneratedAssistantService.java",
                "GeneratedAssistantController.java"
        );
        for (String name : names) {
            boolean exists = textByPath.keySet().stream().anyMatch(path -> path.endsWith("/assistant/" + name));
            if (!exists) {
                add(diagnostics, GeneratedProjectDiagnosticCode.GENERATED_ASSISTANT_ARTIFACT_MISSING, name,
                        "CU-19 generated Spring assistant artifact is missing.");
            }
        }
        if (names.stream().anyMatch(name -> textByPath.keySet().stream().noneMatch(path -> path.endsWith("/assistant/" + name)))) return;
        try {
            String controller = textByPath.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith("/assistant/GeneratedAssistantController.java"))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
            String service = textByPath.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith("/assistant/GeneratedAssistantService.java"))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
            String llama = textByPath.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith("/assistant/GeneratedAssistantLlamaGateway.java"))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
            String whisper = textByPath.entrySet().stream()
                    .filter(entry -> entry.getKey().endsWith("/assistant/GeneratedAssistantWhisperGateway.java"))
                    .map(Map.Entry::getValue).findFirst().orElseThrow();
            if (!controller.contains("/api/assistant") || !controller.contains("/plan") || !controller.contains("/voice") || !controller.contains("/apply"))
                throw new IllegalArgumentException("CU-19 assistant controller must expose plan/voice/apply under /api/assistant.");
            if (!service.contains("previewToken") || !service.contains("PREVIEW_TTL") || !service.contains("pending.put"))
                throw new IllegalArgumentException("Mutations must use expiring opaque preview tokens before apply.");
            if (!llama.contains("/v1/chat/completions") || !llama.contains("tool_choice") || !llama.contains("route_data_request"))
                throw new IllegalArgumentException("CU-19 must reuse llama.cpp native tool calling.");
            if (!whisper.contains("/inference") || !whisper.contains("audio/wav"))
                throw new IllegalArgumentException("CU-19 must reuse whisper-server WAV transcription.");
        } catch (Exception exception) {
            add(diagnostics, GeneratedProjectDiagnosticCode.GENERATED_ASSISTANT_ARTIFACT_INVALID, "assistant",
                    "Generated CU-19 assistant is structurally invalid: " + exception.getMessage());
        }
    }


    private String snake(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_|_$", "")
                .toLowerCase(Locale.ROOT);
    }

    private String kebab(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase(Locale.ROOT);
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
        // TypeScript uses ${...} for native template literals. Those expressions are
        // runtime JavaScript syntax, not leaked FreeMarker placeholders. Keep the
        // directive checks above, but do not reinterpret Angular source literals.
        if ((path.startsWith("frontend/") && path.endsWith(".ts")) || path.startsWith("mobile/")) {
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
