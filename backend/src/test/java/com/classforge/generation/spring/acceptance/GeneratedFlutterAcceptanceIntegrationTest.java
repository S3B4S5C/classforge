package com.classforge.generation.spring.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.application.SpringBootGenerationOptions;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "spring.flutter.acceptance.enabled", matches = "true")
class GeneratedFlutterAcceptanceIntegrationTest {
    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(10);
    private static final String FLUTTER_PROPERTY = "spring.flutter.acceptance.command";

    @TempDir
    Path tempDir;

    @Test
    void simpleAndAuthFlutterAppsAreSpecificSecureDeterministicAndBuildable() throws Exception {
        verify("simple", simpleFixture(), SpringBootGenerationOptions.simpleCrud(false, "#7C3AED"), false);
        AuthFixture auth = authFixture();
        verify(
                "auth",
                auth.model(),
                SpringBootGenerationOptions.authenticated(
                        false,
                        auth.authClassId(),
                        auth.usernameId(),
                        auth.passwordId(),
                        "#0F766E"
                ),
                true
        );
    }

    private void verify(
            String name,
            SpringGenerationModel model,
            SpringBootGenerationOptions options,
            boolean auth
    ) throws Exception {
        SpringProjectRenderer renderer = new SpringProjectRenderer(
                new SpringFreeMarkerRenderer(),
                new GeneratedProjectValidator()
        );
        GeneratedProject first = renderer.render(model, options);
        GeneratedProject second = renderer.render(model, options);
        assertEquals(first, second, name + " Flutter generation must be deterministic.");

        assertTrue(has(first, "mobile/pubspec.yaml"));
        assertTrue(has(first, "mobile/lib/dashboard/dashboard_page.dart"));
        assertTrue(first.files().stream().anyMatch(file -> file.path().endsWith("_list_page.dart")));
        assertTrue(first.files().stream().anyMatch(file -> file.path().endsWith("_detail_page.dart")));
        assertTrue(first.files().stream().anyMatch(file -> file.path().endsWith("_form_page.dart")));
        assertTrue(first.files().stream().anyMatch(file -> file.path().endsWith("_api.dart")));
        assertTrue(first.files().stream().anyMatch(file -> file.path().endsWith("_model.dart")));
        assertTrue(has(first, "mobile/android/app/src/main/AndroidManifest.xml"));
        assertEquals(auth, has(first, "mobile/lib/auth/login_page.dart"));
        assertEquals(auth, has(first, "mobile/lib/core/auth/token_store.dart"));
        if (auth) {
            assertTrue(text(first, "mobile/lib/core/auth/token_store.dart").contains("FlutterSecureStorage"));
        }

        Path mobile = tempDir.resolve(name).resolve("mobile");
        materializeMobile(first, mobile);
        runFlutter(mobile, name, "pub-get", "pub", "get");
        runFlutter(mobile, name, "analyze", "analyze", "--no-fatal-infos", "--no-fatal-warnings");
        runFlutter(mobile, name, "test", "test");
        runFlutter(mobile, name, "apk", "build", "apk", "--debug");
        assertTrue(Files.exists(mobile.resolve("build/app/outputs/flutter-apk/app-debug.apk")),
                "Flutter Android build must produce app-debug.apk.");
    }

    private void materializeMobile(GeneratedProject project, Path mobile) throws IOException {
        Files.createDirectories(mobile);
        for (GeneratedFile file : project.files()) {
            if (!file.path().startsWith("mobile/")) continue;
            String relative = file.path().substring("mobile/".length());
            Path target = mobile.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.write(target, file.content());
        }
    }

    private void runFlutter(Path mobile, String name, String phase, String... arguments) throws Exception {
        String flutter = System.getProperty(FLUTTER_PROPERTY);
        assertNotNull(flutter, "spring.flutter.acceptance.command is required.");
        List<String> command = new java.util.ArrayList<>();
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        if (windows && flutter.toLowerCase(java.util.Locale.ROOT).endsWith(".bat")) {
            command.add("cmd.exe");
            command.add("/d");
            command.add("/c");
        }
        command.add(flutter);
        command.addAll(List.of(arguments));
        Path log = tempDir.resolve(name + "-flutter-" + phase + ".log");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(mobile.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        Process process = builder.start();
        boolean finished = process.waitFor(COMMAND_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("Generated Flutter command timed out for " + name + " / " + phase + "\n" + tail(log));
        }
        assertEquals(0, process.exitValue(), () ->
                "Generated Flutter command failed for " + name + " / " + phase + "\n" + tail(log));
    }

    private String text(GeneratedProject project, String path) {
        GeneratedFile file = project.files().stream().filter(candidate -> candidate.path().equals(path)).findFirst().orElseThrow();
        return new String(file.content(), java.nio.charset.StandardCharsets.UTF_8);
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

    private boolean has(GeneratedProject project, String path) {
        return project.files().stream().anyMatch(file -> file.path().equals(path));
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
