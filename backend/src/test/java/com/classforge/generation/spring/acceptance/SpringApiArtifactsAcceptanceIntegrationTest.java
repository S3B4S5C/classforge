package com.classforge.generation.spring.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.archive.DeterministicZipWriter;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedProject;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ByteArrayResource;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfSystemProperty(named = "spring.api.artifacts.acceptance.enabled", matches = "true")
class SpringApiArtifactsAcceptanceIntegrationTest {
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);
    private static final String JAVA_HOME_PROPERTY = "spring.api.artifacts.acceptance.java-home";
    private static final String GRADLE_USER_HOME_PROPERTY = "spring.api.artifacts.acceptance.gradle-user-home";
    private static final Pattern OPERATION_ID = Pattern.compile("(?m)^\\s+operationId:\\s*([A-Za-z0-9_]+)\\s*$");

    @TempDir
    Path tempDir;

    @Test
    void simpleAndAuthArtifactsAreParseableConsistentDeterministicAndBuildable() throws Exception {
        Fixture fixture = fixture();
        verifyMode("simple", fixture, SpringBootGenerationOptions.simpleCrud(false), false);
        verifyMode("auth", fixture, SpringBootGenerationOptions.authenticated(
                false, fixture.authClassId, fixture.usernameId, fixture.passwordId
        ), true);
    }

    private void verifyMode(
            String name,
            Fixture fixture,
            SpringBootGenerationOptions options,
            boolean auth
    ) throws Exception {
        GeneratedProjectValidator validator = new GeneratedProjectValidator();
        SpringProjectRenderer renderer = new SpringProjectRenderer(new SpringFreeMarkerRenderer(), validator);
        DeterministicZipWriter zipWriter = new DeterministicZipWriter(validator);
        GeneratedProject first = renderer.render(fixture.model, options);
        GeneratedProject second = renderer.render(fixture.model, options);
        byte[] firstZip = zipWriter.write(first);
        byte[] secondZip = zipWriter.write(second);
        assertArrayEquals(firstZip, secondZip, "Generated archive must be byte-for-byte deterministic");

        String openApi = text(first, "openapi.yaml");
        String postman = text(first, "postman_collection.json");
        parseYaml(openApi);
        JsonNode collection = JsonMapper.builder().build().readTree(postman);
        assertEquals("https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
                collection.get("info").get("schema").asText());
        assertEquals(operationIds(openApi), postmanOperationIds(collection),
                "OpenAPI and Postman must expose the exact same canonical operations");
        assertTrue(openApi.contains("'/api/detalle/by-id':"), "Composite IDs must use /by-id");
        assertTrue(openApi.contains("filter.username"), "Expressive filters must be documented");

        if (auth) {
            assertTrue(openApi.contains("securitySchemes:"));
            assertTrue(openApi.contains("bearerAuth:"));
            assertTrue(openApi.contains("'/api/auth/bootstrap':"));
            assertTrue(openApi.contains("'/api/auth/login':"));
            assertTrue(openApi.contains("writeOnly: true"));
            String response = section(openApi, "  UsuarioResponse:", "  UsuarioPageResponse:");
            assertFalse(response.contains("password:"), "Password must never be present in response schemas");
            assertTrue(postman.contains("{{jwt}}"));
            assertTrue(postman.contains("pm.collectionVariables.set('jwt'"));
            assertEquals("noauth", requestByName(collection, "bootstrapAuthentication").get("auth").get("type").asText());
            assertEquals("noauth", requestByName(collection, "loginAuthentication").get("auth").get("type").asText());
        } else {
            assertFalse(openApi.contains("securitySchemes:"));
            assertFalse(openApi.contains("/api/auth/"));
            assertFalse(postman.contains("{{jwt}}"));
        }

        Path root = extract(firstZip, tempDir.resolve(name));
        assertTrue(Files.exists(root.resolve("openapi.yaml")));
        assertTrue(Files.exists(root.resolve("postman_collection.json")));
        runGeneratedBuild(root, name);
        assertContextLoadsAndH2(root);
    }

    private String text(GeneratedProject project, String path) {
        GeneratedFile file = project.files().stream()
                .filter(candidate -> candidate.path().equals(path))
                .findFirst().orElseThrow();
        return new String(file.content(), StandardCharsets.UTF_8);
    }

    private void parseYaml(String yaml) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8)));
        Properties properties = factory.getObject();
        assertNotNull(properties);
        assertEquals("3.0.3", properties.getProperty("openapi"));
    }

    private Set<String> operationIds(String openApi) {
        Set<String> result = new HashSet<>();
        Matcher matcher = OPERATION_ID.matcher(openApi);
        while (matcher.find()) result.add(matcher.group(1));
        assertFalse(result.isEmpty());
        return result;
    }

    private JsonNode requestByName(JsonNode collection, String name) {
        return findRequest(collection.get("item"), name);
    }

    private JsonNode findRequest(JsonNode items, String name) {
        if (items == null || !items.isArray()) return null;
        for (JsonNode item : items) {
            if (item.has("request") && item.has("name") && name.equals(item.get("name").asText())) {
                return item.get("request");
            }
            JsonNode nested = findRequest(item.get("item"), name);
            if (nested != null) return nested;
        }
        return null;
    }

    private Set<String> postmanOperationIds(JsonNode collection) {
        Set<String> result = new HashSet<>();
        collectRequests(collection.get("item"), result);
        assertFalse(result.isEmpty());
        return result;
    }

    private void collectRequests(JsonNode items, Set<String> result) {
        if (items == null || !items.isArray()) return;
        for (JsonNode item : items) {
            if (item.has("request")) {
                result.add(item.get("name").asText());
            } else {
                collectRequests(item.get("item"), result);
            }
        }
    }

    private String section(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }

    private Path extract(byte[] archive, Path destination) throws IOException {
        Files.createDirectories(destination);
        String rootName = null;
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(
                new ByteArrayInputStream(archive), StandardCharsets.UTF_8.name(), true, true
        )) {
            for (ZipArchiveEntry entry; (entry = input.getNextZipEntry()) != null;) {
                assertFalse(entry.isDirectory());
                String entryName = entry.getName();
                int separator = entryName.indexOf('/');
                assertTrue(separator > 0);
                String currentRoot = entryName.substring(0, separator);
                rootName = rootName == null ? currentRoot : rootName;
                assertEquals(rootName, currentRoot);
                String relative = entryName.substring(separator + 1);
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
        if (gradleUserHome != null && !gradleUserHome.isBlank()) builder.environment().put("GRADLE_USER_HOME", gradleUserHome);
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
        UUID authClassId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userIdId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID usernameId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID passwordId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        UUID detailClassId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        UUID detailAId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        UUID detailBId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        UUID detailNameId = UUID.fromString("88888888-8888-8888-8888-888888888888");

        SpringEntityIdModel userId = new SpringEntityIdModel(
                SpringIdKind.SIMPLE, SpringJavaType.UUID, null,
                List.of(new SpringIdFieldModel(userIdId, "id", "id", SpringJavaType.UUID)), true
        );
        SpringEntityModel user = new SpringEntityModel(
                authClassId, "Usuario", "Usuario", "usuario",
                new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()),
                userId,
                List.of(
                        new SpringScalarFieldModel(userIdId, "id", "id", "id", SpringJavaType.UUID, false, true),
                        new SpringScalarFieldModel(usernameId, "username", "username", "username", SpringJavaType.STRING, false, false),
                        new SpringScalarFieldModel(passwordId, "password", "password", "password", SpringJavaType.STRING, false, false)
                ),
                List.of(), List.of(), List.of(), List.of()
        );

        SpringEntityIdModel detailId = new SpringEntityIdModel(
                SpringIdKind.COMPOSITE, null, "DetalleId",
                List.of(
                        new SpringIdFieldModel(detailAId, "ordenId", "orden_id", SpringJavaType.LONG),
                        new SpringIdFieldModel(detailBId, "linea", "linea", SpringJavaType.INTEGER)
                ), true
        );
        SpringEntityModel detail = new SpringEntityModel(
                detailClassId, "Detalle", "Detalle", "detalle",
                new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()),
                detailId,
                List.of(
                        new SpringScalarFieldModel(detailAId, "ordenId", "ordenId", "orden_id", SpringJavaType.LONG, false, true),
                        new SpringScalarFieldModel(detailBId, "linea", "linea", "linea", SpringJavaType.INTEGER, false, true),
                        new SpringScalarFieldModel(detailNameId, "nombre", "nombre", "nombre", SpringJavaType.STRING, true, false)
                ),
                List.of(), List.of(), List.of(), List.of()
        );

        SpringGenerationModel model = new SpringGenerationModel(
                "1.0", "cu15-acceptance", "com.example.cu15", "Cu15AcceptanceApplication", "21", "4.0.8", "9.2.0",
                List.of(user, detail),
                List.of(
                        new SpringRepositoryModel("UsuarioRepository", "Usuario", "UUID", "java.util.UUID", false),
                        new SpringRepositoryModel("DetalleRepository", "Detalle", "DetalleId", "com.example.cu15.entity.DetalleId", true)
                )
        );
        return new Fixture(model, authClassId, usernameId, passwordId);
    }

    private record Fixture(SpringGenerationModel model, UUID authClassId, UUID usernameId, UUID passwordId) { }
}
