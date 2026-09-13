package com.classforge.generation.spring.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.archive.DeterministicZipWriter;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.model.SpringEntityIdModel;
import com.classforge.generation.spring.model.SpringEntityModel;
import com.classforge.generation.spring.model.SpringGenerationModel;
import com.classforge.generation.spring.model.SpringIdFieldModel;
import com.classforge.generation.spring.model.SpringIdKind;
import com.classforge.generation.spring.model.SpringInheritanceKind;
import com.classforge.generation.spring.model.SpringInheritanceModel;
import com.classforge.generation.spring.model.SpringJavaType;
import com.classforge.generation.spring.model.SpringRepositoryModel;
import com.classforge.generation.spring.model.SpringScalarFieldModel;
import com.classforge.generation.spring.rendering.SpringFreeMarkerRenderer;
import com.classforge.generation.spring.rendering.SpringProjectRenderer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@EnabledIfSystemProperty(named = "spring.crud.acceptance.enabled", matches = "true")
class SpringCrudGenerationAcceptanceIntegrationTest {
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);
    private static final String JAVA_HOME_PROPERTY = "spring.crud.acceptance.java-home";
    private static final String GRADLE_USER_HOME_PROPERTY = "spring.crud.acceptance.gradle-user-home";

    @TempDir
    Path tempDir;

    @Test
    void simpleCrudAndAuthenticatedSystemBuildAndLoadContext() throws Exception {
        Fixture fixture = fixture();
        runMode(
                "simple-crud",
                fixture.model,
                SpringBootGenerationOptions.simpleCrud(false),
                false
        );
        runMode(
                "auth-information-system",
                fixture.model,
                SpringBootGenerationOptions.authenticated(
                        false,
                        fixture.classId,
                        fixture.usernameId,
                        fixture.passwordId
                ),
                true
        );
    }

    private void runMode(
            String name,
            SpringGenerationModel model,
            SpringBootGenerationOptions options,
            boolean auth
    ) throws Exception {
        GeneratedProjectValidator validator = new GeneratedProjectValidator();
        SpringProjectRenderer renderer = new SpringProjectRenderer(new SpringFreeMarkerRenderer(), validator);
        byte[] bytes = new DeterministicZipWriter(validator).write(renderer.render(model, options));
        Path root = extract(bytes, tempDir.resolve(name));

        assertTrue(Files.exists(root.resolve("src/main/java/com/example/cu14/controller/UsuarioController.java")));
        assertTrue(Files.exists(root.resolve("src/main/java/com/example/cu14/service/UsuarioService.java")));
        assertEquals(auth, Files.exists(root.resolve("src/main/java/com/example/cu14/security/SecurityConfig.java")));
        if (auth) {
            String response = Files.readString(root.resolve("src/main/java/com/example/cu14/dto/UsuarioResponse.java"));
            assertFalse(response.contains("password"), "Auth response DTO must not expose password");
            String service = Files.readString(root.resolve("src/main/java/com/example/cu14/service/UsuarioService.java"));
            assertTrue(service.contains("passwordEncoder.encode"), "Auth writes must hash password");
        }

        runGeneratedBuild(root, name);
        assertContextLoadsAndH2(root);
    }

    private Path extract(byte[] archive, Path destination) throws IOException {
        Files.createDirectories(destination);
        String rootName = null;
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(
                new ByteArrayInputStream(archive), StandardCharsets.UTF_8.name(), true, true
        )) {
            for (ZipArchiveEntry entry; (entry = input.getNextZipEntry()) != null;) {
                assertFalse(entry.isDirectory());
                String name = entry.getName();
                int separator = name.indexOf('/');
                assertTrue(separator > 0, "Entry must be under one artifact root: " + name);
                String currentRoot = name.substring(0, separator);
                rootName = rootName == null ? currentRoot : rootName;
                assertEquals(rootName, currentRoot);
                String relative = name.substring(separator + 1);
                Path expectedRoot = destination.resolve(rootName).normalize();
                Path output = expectedRoot.resolve(relative).normalize();
                assertTrue(output.startsWith(expectedRoot));
                Files.createDirectories(output.getParent());
                Files.write(output, input.readAllBytes());
                if (relative.equals("gradlew")) {
                    assertTrue(output.toFile().setExecutable(true, false) || Files.isExecutable(output));
                }
            }
        }
        assertNotNull(rootName);
        return destination.resolve(rootName);
    }

    private void runGeneratedBuild(Path projectRoot, String fixtureName) throws Exception {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        List<String> command = windows
                ? List.of("cmd.exe", "/d", "/c", "gradlew.bat", "clean", "build", "--no-daemon", "--console=plain")
                : List.of("./gradlew", "clean", "build", "--no-daemon", "--console=plain");
        Path log = tempDir.resolve(fixtureName + "-gradle.log");
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(projectRoot.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        String javaHome = System.getProperty(JAVA_HOME_PROPERTY);
        if (javaHome == null || javaHome.isBlank()) javaHome = System.getProperty("java.home");
        builder.environment().put("JAVA_HOME", javaHome);
        String gradleUserHome = System.getProperty(GRADLE_USER_HOME_PROPERTY);
        if (gradleUserHome != null && !gradleUserHome.isBlank()) {
            builder.environment().put("GRADLE_USER_HOME", gradleUserHome);
        }
        Process process = builder.start();
        boolean finished = process.waitFor(BUILD_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            fail("Generated build timed out for " + fixtureName + "\n" + tail(log, 20_000));
        }
        String output = Files.exists(log) ? Files.readString(log) : "";
        assertEquals(0, process.exitValue(), () -> "Generated build failed for " + fixtureName + "\n" + tail(output, 20_000));
        assertTrue(output.contains("BUILD SUCCESSFUL"), () -> "Generated build did not report success\n" + tail(output, 20_000));
    }

    private void assertContextLoadsAndH2(Path projectRoot) throws IOException {
        Path config = projectRoot.resolve("src/test/resources/application.yml");
        assertTrue(Files.readString(config).contains("jdbc:h2:mem:"));
        Path results = projectRoot.resolve("build/test-results/test");
        try (Stream<Path> files = Files.walk(results)) {
            List<Path> xmlFiles = files.filter(path -> path.getFileName().toString().startsWith("TEST-")
                    && path.getFileName().toString().endsWith(".xml")).toList();
            assertFalse(xmlFiles.isEmpty());
            boolean passed = false;
            for (Path xml : xmlFiles) {
                try {
                    Element suite = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.toFile()).getDocumentElement();
                    if (!"0".equals(suite.getAttribute("failures")) || !"0".equals(suite.getAttribute("errors"))) continue;
                    NodeList cases = suite.getElementsByTagName("testcase");
                    for (int i = 0; i < cases.getLength(); i++) {
                        Element testCase = (Element) cases.item(i);
                        String testName = testCase.getAttribute("name");
                        if (("contextLoads".equals(testName) || "contextLoads()".equals(testName))
                                && testCase.getElementsByTagName("skipped").getLength() == 0) {
                            passed = true;
                            break;
                        }
                    }
                } catch (Exception exception) {
                    throw new IOException("Could not parse generated JUnit XML: " + xml, exception);
                }
                if (passed) break;
            }
            assertTrue(passed, "Generated contextLoads did not pass");
        }
    }

    private String tail(Path path, int max) throws IOException {
        return Files.exists(path) ? tail(Files.readString(path), max) : "";
    }

    private String tail(String value, int max) {
        return value.length() <= max ? value : value.substring(value.length() - max);
    }

    private Fixture fixture() {
        UUID classId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID idId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID usernameId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID passwordId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID nameId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        SpringIdFieldModel idField = new SpringIdFieldModel(idId, "id", "id", SpringJavaType.UUID);
        SpringEntityIdModel id = new SpringEntityIdModel(SpringIdKind.SIMPLE, SpringJavaType.UUID, null, List.of(idField), true);
        SpringEntityModel user = new SpringEntityModel(
                classId,
                "Usuario",
                "Usuario",
                "usuario",
                new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()),
                id,
                List.of(
                        new SpringScalarFieldModel(idId, "id", "id", "id", SpringJavaType.UUID, false, true),
                        new SpringScalarFieldModel(usernameId, "username", "username", "username", SpringJavaType.STRING, false, false),
                        new SpringScalarFieldModel(passwordId, "password", "password", "password", SpringJavaType.STRING, false, false),
                        new SpringScalarFieldModel(nameId, "nombre", "nombre", "nombre", SpringJavaType.STRING, true, false)
                ),
                List.of(), List.of(), List.of(), List.of()
        );
        SpringGenerationModel model = new SpringGenerationModel(
                "1.0",
                "cu14-acceptance",
                "com.example.cu14",
                "Cu14AcceptanceApplication",
                "21",
                "4.0.8",
                "9.2.0",
                List.of(user),
                List.of(new SpringRepositoryModel("UsuarioRepository", "Usuario", "UUID", "java.util.UUID", false))
        );
        return new Fixture(model, classId, usernameId, passwordId);
    }

    private record Fixture(SpringGenerationModel model, UUID classId, UUID usernameId, UUID passwordId) { }
}
