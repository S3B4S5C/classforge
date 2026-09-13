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
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "spring.angular.acceptance.enabled", matches = "true")
class GeneratedAngularAcceptanceIntegrationTest {
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(4);
    private static final String NODE_MODULES_PROPERTY = "spring.angular.acceptance.node-modules";

    @TempDir
    Path tempDir;

    @Test
    void simpleAndAuthFrontendsAreSpecificDeterministicAndBuildable() throws Exception {
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
        assertEquals(first, second, name + " Angular generation must be deterministic.");

        assertTrue(has(first, "frontend/src/app/dashboard/dashboard.component.ts"));
        assertTrue(first.files().stream().anyMatch(file -> file.path().endsWith("-list.component.ts")));
        assertTrue(first.files().stream().anyMatch(file -> file.path().endsWith("-detail.component.ts")));
        assertTrue(first.files().stream().anyMatch(file -> file.path().endsWith("-form.component.ts")));
        assertTrue(first.files().stream().anyMatch(file -> file.path().endsWith(".api.ts")));
        assertTrue(first.files().stream().anyMatch(file -> file.path().endsWith(".models.ts")));
        assertEquals(auth, has(first, "frontend/src/app/auth/login.component.ts"));
        assertEquals(auth, has(first, "frontend/src/app/core/auth/auth.interceptor.ts"));

        Path frontend = tempDir.resolve(name).resolve("frontend");
        materializeFrontend(first, frontend);
        linkNodeModules(frontend);
        runAngularBuild(frontend, name);
        assertTrue(Files.exists(frontend.resolve("dist")), "Angular build must produce dist output.");
    }

    private void materializeFrontend(GeneratedProject project, Path frontend) throws IOException {
        Files.createDirectories(frontend);
        for (GeneratedFile file : project.files()) {
            if (!file.path().startsWith("frontend/")) continue;
            String relative = file.path().substring("frontend/".length());
            Path target = frontend.resolve(relative);
            Files.createDirectories(target.getParent());
            Files.write(target, file.content());
        }
    }

    private void linkNodeModules(Path frontend) throws Exception {
        String configured = System.getProperty(NODE_MODULES_PROPERTY);
        assertNotNull(configured, "spring.angular.acceptance.node-modules is required.");
        Path target = Path.of(configured).toAbsolutePath().normalize();
        assertTrue(Files.isDirectory(target), "Configured frontend node_modules does not exist: " + target);
        Path link = frontend.resolve("node_modules");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            Process process = new ProcessBuilder(
                    "cmd.exe", "/d", "/c", "mklink", "/J",
                    link.toAbsolutePath().toString(),
                    target.toString()
            ).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes());
            assertEquals(0, process.waitFor(), () -> "Could not create node_modules junction:\n" + output);
        } else {
            Files.createSymbolicLink(link, target);
        }
    }

    private void runAngularBuild(Path frontend, String name) throws Exception {
        Path nodeModules = frontend.resolve("node_modules");
        Path cli = nodeModules.resolve("@angular/cli/bin/ng.js");
        assertTrue(Files.isRegularFile(cli), "Angular CLI not available through acceptance node_modules.");
        Path log = tempDir.resolve(name + "-angular.log");
        ProcessBuilder builder = new ProcessBuilder(
                "node",
                cli.toAbsolutePath().toString(),
                "build",
                "--configuration",
                "production",
                "--no-progress"
        ).directory(frontend.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
        Process process = builder.start();
        boolean finished = process.waitFor(BUILD_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("Generated Angular build timed out for " + name + "\n" + tail(log));
        }
        assertEquals(0, process.exitValue(), () ->
                "Generated Angular build failed for " + name + "\n" + tail(log));
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
