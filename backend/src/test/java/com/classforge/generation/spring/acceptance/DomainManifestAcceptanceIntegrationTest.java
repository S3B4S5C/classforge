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
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@EnabledIfSystemProperty(named = "spring.domain.manifest.acceptance.enabled", matches = "true")
class DomainManifestAcceptanceIntegrationTest {
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);
    private static final String JAVA_HOME_PROPERTY = "spring.domain.manifest.acceptance.java-home";
    private static final String GRADLE_USER_HOME_PROPERTY = "spring.domain.manifest.acceptance.gradle-user-home";
    private static final Pattern OPERATION_ID = Pattern.compile("(?m)^\\s+operationId:\\s*([A-Za-z0-9_]+)\\s*$");

    @TempDir
    Path tempDir;

    @Test
    void schemaV1CoversRelationsCompositeIdsAuthAndJoinedInheritance() throws Exception {
        verify(simpleRelationsFixture());
        verify(compositeFixture());
        verify(authFixture());
        verify(joinedFixture());
    }

    private void verify(Fixture fixture) throws Exception {
        GeneratedProjectValidator validator = new GeneratedProjectValidator();
        SpringProjectRenderer renderer = new SpringProjectRenderer(new SpringFreeMarkerRenderer(), validator);
        DeterministicZipWriter zipWriter = new DeterministicZipWriter(validator);

        GeneratedProject first = renderer.render(fixture.model(), fixture.options());
        GeneratedProject second = renderer.render(fixture.model(), fixture.options());
        String firstManifest = text(first, "domain-manifest.json");
        String secondManifest = text(second, "domain-manifest.json");
        assertEquals(firstManifest, secondManifest, fixture.name() + " manifest must be byte-identical");
        byte[] firstZip = zipWriter.write(first);
        byte[] secondZip = zipWriter.write(second);
        assertArrayEquals(firstZip, secondZip, fixture.name() + " ZIP must remain byte-identical");

        JsonNode manifest = JsonMapper.builder().build().readTree(firstManifest);
        assertEquals("1.0", manifest.get("schemaVersion").asText());
        assertEquals(fixture.expectedMode(), manifest.get("generationMode").asText());
        assertEquals(operationIds(text(first, "openapi.yaml")), manifestOperationIds(manifest));
        assertReferencesResolve(manifest);
        fixture.assertion().verify(manifest);

        Path root = extract(firstZip, tempDir.resolve(fixture.name()));
        assertTrue(Files.exists(root.resolve("domain-manifest.json")));
        assertTrue(Files.exists(root.resolve("openapi.yaml")));
        assertTrue(Files.exists(root.resolve("postman_collection.json")));
        runGeneratedBuild(root, fixture.name());
        assertContextLoadsAndH2(root);
    }

    private void assertReferencesResolve(JsonNode manifest) {
        Set<String> entityIds = new HashSet<>();
        for (JsonNode entity : manifest.get("entities")) entityIds.add(entity.get("id").asText());
        for (JsonNode entity : manifest.get("entities")) {
            if (entity.get("inheritance").has("superEntityId")) {
                assertTrue(entityIds.contains(entity.get("inheritance").get("superEntityId").asText()));
            }
            for (JsonNode relation : entity.get("relations")) {
                assertTrue(entityIds.contains(relation.get("targetEntityId").asText()));
                assertDoesNotThrow(() -> UUID.fromString(relation.get("id").asText()));
            }
        }
    }

    private Fixture simpleRelationsFixture() {
        UUID userClass = uuid("10000000-0000-0000-0000-000000000001");
        UUID userId = uuid("10000000-0000-0000-0000-000000000002");
        UUID orderClass = uuid("10000000-0000-0000-0000-000000000003");
        UUID orderId = uuid("10000000-0000-0000-0000-000000000004");
        UUID relationId = uuid("10000000-0000-0000-0000-000000000005");
        SpringEntityModel user = basic(userClass, userId, "Usuario");
        SpringDirectRelationModel owner = new SpringDirectRelationModel(
                relationId, UmlRelationshipType.ASSOCIATION, SpringDirectRelationKind.MANY_TO_ONE,
                "owner", "Usuario", "usuario", List.of(new SpringJoinColumnModel("owner_id", "id", false)), false, false
        );
        SpringEntityModel order = new SpringEntityModel(
                orderClass, "Pedido", "Pedido", "pedido", none(), simpleId(orderId, true),
                List.of(field(orderId, "id", SpringJavaType.UUID, false, true)), List.of(owner), List.of(), List.of(), List.of()
        );
        SpringGenerationModel model = model("cu16-relations", List.of(user, order));
        return new Fixture("simple-relations", model, SpringBootGenerationOptions.simpleCrud(false), "SIMPLE_CRUD", manifest -> {
            JsonNode pedido = entity(manifest, orderClass);
            assertEquals(relationId.toString(), pedido.get("relations").get(0).get("id").asText());
            assertEquals(userClass.toString(), pedido.get("relations").get(0).get("targetEntityId").asText());
            assertFalse(manifest.get("authentication").get("enabled").asBoolean());
        });
    }

    private Fixture compositeFixture() {
        UUID classId = uuid("20000000-0000-0000-0000-000000000001");
        UUID a = uuid("20000000-0000-0000-0000-000000000002");
        UUID b = uuid("20000000-0000-0000-0000-000000000003");
        SpringEntityIdModel id = new SpringEntityIdModel(
                SpringIdKind.COMPOSITE, null, "DetalleId",
                List.of(
                        new SpringIdFieldModel(a, "ordenId", "orden_id", SpringJavaType.LONG),
                        new SpringIdFieldModel(b, "linea", "linea", SpringJavaType.INTEGER)
                ), true
        );
        SpringEntityModel detail = new SpringEntityModel(
                classId, "Detalle", "Detalle", "detalle", none(), id,
                List.of(
                        new SpringScalarFieldModel(a, "ordenId", "ordenId", "orden_id", SpringJavaType.LONG, false, true),
                        new SpringScalarFieldModel(b, "linea", "linea", "linea", SpringJavaType.INTEGER, false, true)
                ), List.of(), List.of(), List.of(), List.of()
        );
        return new Fixture("simple-composite", model("cu16-composite", List.of(detail)), SpringBootGenerationOptions.simpleCrud(false), "SIMPLE_CRUD", manifest -> {
            JsonNode entity = entity(manifest, classId);
            assertEquals("COMPOSITE", entity.get("identifier").get("kind").asText());
            assertEquals(2, entity.get("identifier").get("fields").size());
            assertTrue(manifestOperationIds(manifest).contains("getDetalle"));
        });
    }

    private Fixture authFixture() {
        UUID authClass = uuid("30000000-0000-0000-0000-000000000001");
        UUID id = uuid("30000000-0000-0000-0000-000000000002");
        UUID username = uuid("30000000-0000-0000-0000-000000000003");
        UUID password = uuid("30000000-0000-0000-0000-000000000004");
        SpringEntityModel user = new SpringEntityModel(
                authClass, "Usuario", "Usuario", "usuario", none(), simpleId(id, true),
                List.of(
                        field(id, "id", SpringJavaType.UUID, false, true),
                        field(username, "username", SpringJavaType.STRING, false, false),
                        field(password, "password", SpringJavaType.STRING, false, false)
                ), List.of(), List.of(), List.of(), List.of()
        );
        SpringBootGenerationOptions options = SpringBootGenerationOptions.authenticated(false, authClass, username, password);
        return new Fixture("auth", model("cu16-auth", List.of(user)), options, "AUTH_INFORMATION_SYSTEM", manifest -> {
            JsonNode auth = manifest.get("authentication");
            assertTrue(auth.get("enabled").asBoolean());
            assertEquals("BEARER_JWT", auth.get("scheme").asText());
            assertEquals(authClass.toString(), auth.get("entityId").asText());
            JsonNode passwordNode = attribute(entity(manifest, authClass), password);
            assertTrue(passwordNode.get("sensitive").asBoolean());
            assertTrue(passwordNode.get("writeOnly").asBoolean());
            assertFalse(passwordNode.get("readable").asBoolean());
            assertFalse(passwordNode.get("searchable").asBoolean());
            assertFalse(passwordNode.get("filterable").asBoolean());
            assertFalse(passwordNode.get("sortable").asBoolean());
            assertTrue(manifestOperationIds(manifest).containsAll(Set.of("bootstrapAuthentication", "loginAuthentication")));
        });
    }

    private Fixture joinedFixture() {
        UUID rootClass = uuid("40000000-0000-0000-0000-000000000001");
        UUID rootId = uuid("40000000-0000-0000-0000-000000000002");
        UUID childClass = uuid("40000000-0000-0000-0000-000000000003");
        UUID salary = uuid("40000000-0000-0000-0000-000000000004");
        SpringEntityModel root = new SpringEntityModel(
                rootClass, "Persona", "Persona", "persona",
                new SpringInheritanceModel(SpringInheritanceKind.JOINED_ROOT, null, List.of()),
                simpleId(rootId, true),
                List.of(field(rootId, "id", SpringJavaType.UUID, false, true)), List.of(), List.of(), List.of(), List.of()
        );
        SpringEntityModel child = new SpringEntityModel(
                childClass, "Empleado", "Empleado", "empleado",
                new SpringInheritanceModel(SpringInheritanceKind.JOINED_SUBCLASS, "Persona", List.of(new SpringJoinColumnModel("id", "id", false))),
                simpleId(rootId, false),
                List.of(field(salary, "salario", SpringJavaType.BIG_DECIMAL, false, false)), List.of(), List.of(), List.of(), List.of()
        );
        return new Fixture("joined", model("cu16-joined", List.of(root, child)), SpringBootGenerationOptions.simpleCrud(false), "SIMPLE_CRUD", manifest -> {
            JsonNode employee = entity(manifest, childClass);
            assertEquals("JOINED_SUBCLASS", employee.get("inheritance").get("kind").asText());
            assertEquals(rootClass.toString(), employee.get("inheritance").get("superEntityId").asText());
            assertEquals(rootId.toString(), employee.get("identifier").get("fields").get(0).get("attributeId").asText());
            assertNotNull(attribute(employee, rootId));
            assertNotNull(attribute(employee, salary));
        });
    }

    private JsonNode entity(JsonNode manifest, UUID id) {
        for (JsonNode entity : manifest.get("entities")) if (id.toString().equals(entity.get("id").asText())) return entity;
        fail("Entity not found: " + id);
        return null;
    }

    private JsonNode attribute(JsonNode entity, UUID id) {
        for (JsonNode attribute : entity.get("attributes")) if (id.toString().equals(attribute.get("id").asText())) return attribute;
        fail("Attribute not found: " + id);
        return null;
    }

    private Set<String> operationIds(String openApi) {
        Set<String> result = new HashSet<>();
        Matcher matcher = OPERATION_ID.matcher(openApi);
        while (matcher.find()) result.add(matcher.group(1));
        return result;
    }

    private Set<String> manifestOperationIds(JsonNode manifest) {
        Set<String> result = new HashSet<>();
        for (JsonNode operation : manifest.get("operations")) result.add(operation.get("operationId").asText());
        return result;
    }

    private SpringGenerationModel model(String artifactName, List<SpringEntityModel> entities) {
        return new SpringGenerationModel(
                "1.0", artifactName, "com.example." + artifactName.replace('-', '_'), "GeneratedApplication", "21", "4.0.8", "9.2.0",
                entities,
                entities.stream().map(entity -> new SpringRepositoryModel(
                        entity.className() + "Repository", entity.className(), entity.id().typeSimpleName(),
                        entity.id().typeQualifiedName(), entity.id().kind() == SpringIdKind.COMPOSITE
                )).toList()
        );
    }

    private SpringEntityModel basic(UUID classId, UUID idId, String name) {
        return new SpringEntityModel(
                classId, name, name, name.toLowerCase(Locale.ROOT), none(), simpleId(idId, true),
                List.of(field(idId, "id", SpringJavaType.UUID, false, true)), List.of(), List.of(), List.of(), List.of()
        );
    }

    private SpringEntityIdModel simpleId(UUID attributeId, boolean declaredByEntity) {
        return new SpringEntityIdModel(
                SpringIdKind.SIMPLE, SpringJavaType.UUID, null,
                List.of(new SpringIdFieldModel(attributeId, "id", "id", SpringJavaType.UUID)), declaredByEntity
        );
    }

    private SpringScalarFieldModel field(UUID id, String name, SpringJavaType type, boolean nullable, boolean identifier) {
        return new SpringScalarFieldModel(id, name, name, name, type, nullable, identifier);
    }

    private SpringInheritanceModel none() {
        return new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of());
    }

    private UUID uuid(String value) { return UUID.fromString(value); }

    private String text(GeneratedProject project, String path) {
        GeneratedFile file = project.files().stream().filter(candidate -> candidate.path().equals(path)).findFirst().orElseThrow();
        return new String(file.content(), StandardCharsets.UTF_8);
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
                if (relative.equals("gradlew")) assertTrue(output.toFile().setExecutable(true, false) || Files.isExecutable(output));
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
        ProcessBuilder builder = new ProcessBuilder(command).directory(projectRoot.toFile()).redirectErrorStream(true).redirectOutput(log.toFile());
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
        assertTrue(Files.readString(projectRoot.resolve("src/test/resources/application.yml")).contains("jdbc:h2:mem:"));
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

    private String tail(Path path, int max) throws IOException { return Files.exists(path) ? tail(Files.readString(path), max) : ""; }
    private String tail(String value, int max) { return value.length() <= max ? value : value.substring(value.length() - max); }

    @FunctionalInterface
    private interface ManifestAssertion { void verify(JsonNode manifest); }

    private record Fixture(
            String name,
            SpringGenerationModel model,
            SpringBootGenerationOptions options,
            String expectedMode,
            ManifestAssertion assertion
    ) { }
}
