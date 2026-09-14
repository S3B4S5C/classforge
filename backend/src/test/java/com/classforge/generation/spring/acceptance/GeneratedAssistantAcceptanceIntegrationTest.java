package com.classforge.generation.spring.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.archive.DeterministicZipWriter;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedProject;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.model.*;
import com.classforge.generation.spring.rendering.SpringFreeMarkerRenderer;
import com.classforge.generation.spring.rendering.SpringProjectRenderer;
import com.classforge.project.domain.document.UmlRelationshipType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "spring.generated.assistant.acceptance.enabled", matches = "true")
class GeneratedAssistantAcceptanceIntegrationTest {
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(6);
    private static final String JAVA_HOME_PROPERTY = "spring.generated.assistant.acceptance.java-home";
    private static final String GRADLE_USER_HOME_PROPERTY = "spring.generated.assistant.acceptance.gradle-user-home";

    @TempDir
    Path tempDir;

    @Test
    void simpleAndAuthGeneratedAssistantsArePrivateDeterministicAndBuildable() throws Exception {
        verify("simple", simpleFixture(), SpringBootGenerationOptions.simpleCrud(false, "#7C3AED"), false);
        AuthFixture auth = authFixture();
        verify(
                "auth",
                auth.model(),
                SpringBootGenerationOptions.authenticated(
                        false, auth.authClassId(), auth.usernameId(), auth.passwordId(), "#0F766E"
                ),
                true
        );
    }

    private void verify(String name, SpringGenerationModel model, SpringBootGenerationOptions options, boolean auth) throws Exception {
        GeneratedProjectValidator validator = new GeneratedProjectValidator();
        SpringProjectRenderer renderer = new SpringProjectRenderer(new SpringFreeMarkerRenderer(), validator);
        DeterministicZipWriter zipWriter = new DeterministicZipWriter(validator);

        GeneratedProject first = renderer.render(model, options);
        GeneratedProject second = renderer.render(model, options);
        assertEquals(first, second, name + " generated assistant must be deterministic.");
        assertArrayEquals(zipWriter.write(first), zipWriter.write(second), name + " generated ZIP must be byte-identical.");

        String base = "src/main/java/com/example/generated/assistant/";
        for (String file : List.of(
                "GeneratedAssistantTypes.java", "GeneratedAssistantMetadata.java",
                "GeneratedAssistantLlamaGateway.java", "GeneratedAssistantWhisperGateway.java",
                "GeneratedAssistantHttpExecutor.java", "GeneratedAssistantService.java",
                "GeneratedAssistantController.java"
        )) {
            assertTrue(has(first, base + file), "Missing generated assistant file " + file);
        }
        assertTrue(has(first, "frontend/src/app/assistant/assistant.component.ts"));
        assertTrue(has(first, "frontend/src/app/assistant/browser-wav-recorder.service.ts"));
        assertTrue(has(first, "mobile/lib/assistant/assistant_page.dart"));

        String controller = text(first, base + "GeneratedAssistantController.java");
        assertTrue(controller.contains("/api/assistant"));
        assertTrue(controller.contains("/plan"));
        assertTrue(controller.contains("/voice"));
        assertTrue(controller.contains("/apply"));
        String llama = text(first, base + "GeneratedAssistantLlamaGateway.java");
        assertTrue(llama.contains("/v1/chat/completions"));
        assertTrue(llama.contains("route_data_request"));
        assertTrue(llama.contains("tool_choice"));
        String whisper = text(first, base + "GeneratedAssistantWhisperGateway.java");
        assertTrue(whisper.contains("/inference"));
        assertTrue(whisper.contains("validateWav(bytes)"));
        assertTrue(whisper.contains("bytes[0] != 'R'"));
        assertTrue(whisper.contains("bytes[8] != 'W'"));
        String recorder = text(first, "frontend/src/app/assistant/browser-wav-recorder.service.ts");
        assertTrue(recorder.contains("'RIFF'"));
        assertTrue(recorder.contains("'WAVE'"));
        assertTrue(recorder.contains("16_000"));
        String service = text(first, base + "GeneratedAssistantService.java");
        assertTrue(service.contains("previewToken"));
        assertTrue(service.contains("PREVIEW_TTL"));
        String angular = text(first, "frontend/src/app/assistant/assistant.component.ts");
        assertFalse(angular.contains("pending.command"));
        assertFalse(angular.contains("command | json"));
        assertTrue(angular.contains("previewToken"));

        if (auth) {
            String metadata = text(first, base + "GeneratedAssistantMetadata.java");
            assertTrue(metadata.contains("field.sensitive() ? \"***\" : value"));
            String flutterApi = text(first, "mobile/lib/assistant/assistant_api.dart");
            assertTrue(flutterApi.contains("TokenStore"));
            assertTrue(flutterApi.contains("Authorization"));
        }

        Path root = tempDir.resolve(name);
        materialize(first, root);
        runGeneratedBuild(root, name);
        assertTrue(Files.exists(root.resolve("build")), "Generated Spring build must produce build output.");
    }

    private void materialize(GeneratedProject project, Path root) throws IOException {
        Files.createDirectories(root);
        for (GeneratedFile file : project.files()) {
            Path target = root.resolve(file.path());
            if (target.getParent() != null) Files.createDirectories(target.getParent());
            Files.write(target, file.content());
        }
    }

    private void runGeneratedBuild(Path root, String name) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> command = windows
                ? List.of("cmd.exe", "/d", "/c", "gradlew.bat", "clean", "build", "--no-daemon", "--console=plain")
                : List.of("./gradlew", "clean", "build", "--no-daemon", "--console=plain");
        if (!windows) root.resolve("gradlew").toFile().setExecutable(true);
        ProcessBuilder builder = new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true);
        String javaHome = System.getProperty(JAVA_HOME_PROPERTY);
        if (javaHome != null && !javaHome.isBlank()) builder.environment().put("JAVA_HOME", javaHome);
        String gradleUserHome = System.getProperty(GRADLE_USER_HOME_PROPERTY);
        if (gradleUserHome != null && !gradleUserHome.isBlank()) builder.environment().put("GRADLE_USER_HOME", gradleUserHome);
        Path log = tempDir.resolve(name + "-generated-assistant-build.log");
        builder.redirectOutput(log.toFile());
        Process process = builder.start();
        boolean finished = process.waitFor(BUILD_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("Generated Spring build timed out for " + name + "\n" + tail(log));
        }
        assertEquals(0, process.exitValue(), () -> "Generated Spring build failed for " + name + "\n" + tail(log));
    }

    private String text(GeneratedProject project, String path) {
        GeneratedFile file = project.files().stream().filter(candidate -> candidate.path().equals(path)).findFirst().orElseThrow();
        return new String(file.content(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private boolean has(GeneratedProject project, String path) {
        return project.files().stream().anyMatch(file -> file.path().equals(path));
    }

    private String tail(Path log) {
        try {
            if (!Files.exists(log)) return "";
            String value = Files.readString(log);
            int max = 20_000;
            return value.length() <= max ? value : value.substring(value.length() - max);
        } catch (IOException exception) {
            return exception.toString();
        }
    }

    private SpringGenerationModel simpleFixture() {
        UUID roleClass = uuid("10000000-0000-0000-0000-000000000001");
        UUID roleId = uuid("10000000-0000-0000-0000-000000000002");
        UUID userClass = uuid("10000000-0000-0000-0000-000000000003");
        UUID userId = uuid("10000000-0000-0000-0000-000000000004");
        UUID userName = uuid("10000000-0000-0000-0000-000000000005");
        UUID relationId = uuid("10000000-0000-0000-0000-000000000006");
        UUID detailClass = uuid("10000000-0000-0000-0000-000000000007");
        UUID orderId = uuid("10000000-0000-0000-0000-000000000008");
        UUID lineId = uuid("10000000-0000-0000-0000-000000000009");

        SpringEntityModel role = entity(
                roleClass,
                "Rol",
                "Rol",
                "rol",
                simpleId(roleId, SpringJavaType.LONG),
                List.of(field(roleId, "id", SpringJavaType.LONG, false, true))
        );
        SpringDirectRelationModel roleRelation = new SpringDirectRelationModel(
                relationId,
                UmlRelationshipType.ASSOCIATION,
                SpringDirectRelationKind.MANY_TO_ONE,
                "rol",
                "Rol",
                "rol",
                List.of(new SpringJoinColumnModel("rol_id", "id", false)),
                false,
                false
        );
        SpringEntityModel user = new SpringEntityModel(
                userClass,
                "Usuario",
                "Usuario",
                "usuario",
                none(),
                simpleId(userId, SpringJavaType.UUID),
                List.of(
                        field(userId, "id", SpringJavaType.UUID, false, true),
                        field(userName, "nombre", SpringJavaType.STRING, false, false)
                ),
                List.of(roleRelation),
                List.of(),
                List.of(),
                List.of()
        );
        SpringEntityIdModel composite = new SpringEntityIdModel(
                SpringIdKind.COMPOSITE,
                null,
                "DetalleId",
                List.of(
                        new SpringIdFieldModel(orderId, "ordenId", "orden_id", SpringJavaType.LONG),
                        new SpringIdFieldModel(lineId, "linea", "linea", SpringJavaType.INTEGER)
                ),
                true
        );
        SpringEntityModel detail = new SpringEntityModel(
                detailClass,
                "Detalle",
                "Detalle",
                "detalle",
                none(),
                composite,
                List.of(
                        field(orderId, "ordenId", SpringJavaType.LONG, false, true),
                        field(lineId, "linea", SpringJavaType.INTEGER, false, true)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        return model("cu17-simple", List.of(role, user, detail));
    }

    private AuthFixture authFixture() {
        UUID classId = uuid("20000000-0000-0000-0000-000000000001");
        UUID idId = uuid("20000000-0000-0000-0000-000000000002");
        UUID usernameId = uuid("20000000-0000-0000-0000-000000000003");
        UUID passwordId = uuid("20000000-0000-0000-0000-000000000004");
        SpringEntityModel user = entity(
                classId,
                "Cuenta",
                "Cuenta",
                "cuenta",
                simpleId(idId, SpringJavaType.UUID),
                List.of(
                        field(idId, "id", SpringJavaType.UUID, false, true),
                        field(usernameId, "username", SpringJavaType.STRING, false, false),
                        field(passwordId, "password", SpringJavaType.STRING, false, false)
                )
        );
        return new AuthFixture(model("cu17-auth", List.of(user)), classId, usernameId, passwordId);
    }

    private SpringEntityModel entity(
            UUID classId,
            String logicalName,
            String className,
            String tableName,
            SpringEntityIdModel id,
            List<SpringScalarFieldModel> fields
    ) {
        return new SpringEntityModel(
                classId, logicalName, className, tableName, none(), id,
                fields, List.of(), List.of(), List.of(), List.of()
        );
    }

    private SpringEntityIdModel simpleId(UUID attributeId, SpringJavaType type) {
        return new SpringEntityIdModel(
                SpringIdKind.SIMPLE,
                type,
                null,
                List.of(new SpringIdFieldModel(attributeId, "id", "id", type)),
                true
        );
    }

    private SpringScalarFieldModel field(
            UUID id,
            String name,
            SpringJavaType type,
            boolean nullable,
            boolean identifier
    ) {
        return new SpringScalarFieldModel(id, name, name, name, type, nullable, identifier);
    }

    private SpringInheritanceModel none() {
        return new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of());
    }

    private SpringGenerationModel model(String artifact, List<SpringEntityModel> entities) {
        List<SpringRepositoryModel> repositories = entities.stream().map(entity ->
                new SpringRepositoryModel(
                        entity.className() + "Repository",
                        entity.className(),
                        entity.id().typeSimpleName(),
                        entity.id().typeQualifiedName(),
                        entity.id().kind() == SpringIdKind.COMPOSITE
                )
        ).toList();
        return new SpringGenerationModel(
                "1.0", artifact, "com.example.generated", "GeneratedApplication",
                "21", "4.0.8", "9.2.0", entities, repositories
        );
    }

    private UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private record AuthFixture(
            SpringGenerationModel model,
            UUID authClassId,
            UUID usernameId,
            UUID passwordId
    ) { }
}
