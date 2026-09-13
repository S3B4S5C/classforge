package com.classforge.generation.spring.acceptance;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.relational.RelationalModelMapper;
import com.classforge.generation.relational.RelationalModelValidator;
import com.classforge.generation.relational.RelationalNamingStrategy;
import com.classforge.generation.spring.archive.DeterministicZipWriter;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.model.SpringGenerationConfig;
import com.classforge.generation.spring.planning.SpringGenerationPlanner;
import com.classforge.generation.spring.planning.SpringJavaNamingStrategy;
import com.classforge.generation.spring.rendering.SpringFreeMarkerRenderer;
import com.classforge.generation.spring.rendering.SpringProjectRenderer;
import com.classforge.generation.spring.validation.SpringGenerationModelValidator;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.domain.document.UmlVisibility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "spring.generation.acceptance.enabled", matches = "true")
class SpringGenerationAcceptanceIntegrationTest {
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(5);
    private static final String REPORT_PROPERTY = "spring.generation.acceptance.report-file";
    private static final String JAVA_HOME_PROPERTY = "spring.generation.acceptance.java-home";
    private static final String GRADLE_USER_HOME_PROPERTY = "spring.generation.acceptance.gradle-user-home";

    @TempDir
    Path tempDir;

    @Test
    void generatedArchivesBuildAndLoadSpringContextAcrossTheAcceptanceMatrix() throws Exception {
        List<Fixture> fixtures = List.of(relationsFixture(), compositeFixture(), inheritanceFixture());
        List<FixtureResult> results = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();

        clearReport();
        byte[] deterministicA = generate(fixtures.getFirst());
        byte[] deterministicB = generate(fixtures.getFirst());
        String deterministicSha = sha256(deterministicA);
        String deterministicShaB = sha256(deterministicB);
        boolean deterministicEqual = Arrays.equals(deterministicA, deterministicB)
                && deterministicSha.equals(deterministicShaB);
        if (!deterministicEqual) {
            System.err.println("CU-13 acceptance determinism failed: bytesEqual="
                    + Arrays.equals(deterministicA, deterministicB)
                    + ", shaA=" + deterministicSha
                    + ", shaB=" + deterministicShaB);
            failures.add(new AssertionError(
                    "Equal canonical inputs must produce byte-identical ZIPs and the same SHA-256"
            ));
        }

        for (Fixture fixture : fixtures) {
            try {
                byte[] archive = fixture == fixtures.getFirst() ? deterministicA : generate(fixture);
                Path projectRoot = extract(archive, tempDir.resolve(fixture.name()));
                runGeneratedBuild(projectRoot, fixture.name());
                assertContextLoadsAndH2(projectRoot);
                results.add(new FixtureResult(fixture.name(), fixture.coverage(), "PASS", "PASS", "PASS"));
            } catch (Throwable failure) {
                System.err.println("CU-13 acceptance fixture failed: " + fixture.name());
                failure.printStackTrace(System.err);
                results.add(new FixtureResult(fixture.name(), fixture.coverage(), "FAIL", "FAIL", "FAIL"));
                failures.add(new AssertionError("Acceptance fixture failed: " + fixture.name(), failure));
            }
        }

        writeReport(results, deterministicSha, deterministicEqual, failures.isEmpty());
        if (!failures.isEmpty()) {
            AssertionError aggregate = new AssertionError(failures.size() + " Spring generation acceptance check(s) failed");
            failures.forEach(aggregate::addSuppressed);
            throw aggregate;
        }
    }

    private byte[] generate(Fixture fixture) {
        RelationalModelMapper relational = new RelationalModelMapper(
                new RelationalNamingStrategy(),
                new RelationalModelValidator()
        );
        SpringGenerationPlanner planner = new SpringGenerationPlanner(
                new SpringJavaNamingStrategy(),
                new SpringGenerationModelValidator()
        );
        GeneratedProjectValidator generatedValidator = new GeneratedProjectValidator();
        SpringProjectRenderer renderer = new SpringProjectRenderer(
                new SpringFreeMarkerRenderer(),
                generatedValidator
        );
        DeterministicZipWriter archive = new DeterministicZipWriter(generatedValidator);
        return archive.write(renderer.render(planner.plan(
                relational.map(fixture.model()),
                new SpringGenerationConfig(fixture.artifactName(), fixture.basePackage())
        )));
    }

    private Path extract(byte[] archive, Path destination) throws IOException {
        Files.createDirectories(destination);
        String rootName = null;
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(
                new ByteArrayInputStream(archive), StandardCharsets.UTF_8.name(), true, true
        )) {
            for (ZipArchiveEntry entry; (entry = input.getNextZipEntry()) != null;) {
                assertFalse(entry.isDirectory(), "CU-13 ZIPs must not contain directory entries");
                String name = entry.getName();
                int separator = name.indexOf('/');
                assertTrue(separator > 0, "Archive entry must be below one artifact root: " + name);
                String entryRoot = name.substring(0, separator);
                if (rootName == null) {
                    rootName = entryRoot;
                } else {
                    assertEquals(rootName, entryRoot, "All archive entries must share one artifact root");
                }
                String relative = name.substring(separator + 1);
                Path output = destination.resolve(entryRoot).resolve(relative).normalize();
                Path expectedRoot = destination.resolve(entryRoot).normalize();
                assertTrue(output.startsWith(expectedRoot), "Archive extraction escaped the fixture root: " + name);
                Files.createDirectories(output.getParent());
                Files.write(output, input.readAllBytes());
                if (relative.equals("gradlew")) {
                    assertTrue(output.toFile().setExecutable(true, false) || Files.isExecutable(output),
                            "Generated Unix wrapper could not be made executable");
                }
            }
        }
        assertNotNull(rootName, "Generated archive must contain files");
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
        if (javaHome == null || javaHome.isBlank()) {
            javaHome = System.getProperty("java.home");
        }
        builder.environment().put("JAVA_HOME", javaHome);

        String gradleUserHome = System.getProperty(GRADLE_USER_HOME_PROPERTY);
        if (gradleUserHome != null && !gradleUserHome.isBlank()) {
            builder.environment().put("GRADLE_USER_HOME", gradleUserHome);
        }

        Process process = builder.start();
        boolean finished = process.waitFor(BUILD_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(15, TimeUnit.SECONDS);
            fail("Generated Gradle build timed out for " + fixtureName + " after " + BUILD_TIMEOUT
                    + "\n" + tail(log, 20_000));
        }
        String output = Files.exists(log) ? Files.readString(log) : "";
        assertEquals(0, process.exitValue(), () -> "Generated build failed for " + fixtureName + "\n" + tail(output, 20_000));
        assertTrue(output.contains("BUILD SUCCESSFUL"), () -> "Generated build did not report success for " + fixtureName + "\n" + tail(output, 20_000));
    }

    private void assertContextLoadsAndH2(Path projectRoot) throws IOException {
        Path testConfig = projectRoot.resolve("src/test/resources/application.yml");
        assertTrue(Files.exists(testConfig), "Generated test H2 configuration is missing");
        assertTrue(Files.readString(testConfig).contains("jdbc:h2:mem:"), "Generated tests must use H2");

        Path results = projectRoot.resolve("build/test-results/test");
        assertTrue(Files.isDirectory(results), "Generated Gradle build did not produce test results");
        try (Stream<Path> files = Files.walk(results)) {
            List<Path> xmlFiles = files
                    .filter(path -> path.getFileName().toString().startsWith("TEST-")
                            && path.getFileName().toString().endsWith(".xml"))
                    .toList();
            assertFalse(xmlFiles.isEmpty(), "Generated Gradle build produced no JUnit XML");
            boolean contextLoadsPassed = false;
            for (Path xml : xmlFiles) {
                try {
                    Element suite = DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder()
                            .parse(xml.toFile())
                            .getDocumentElement();
                    if (!"0".equals(suite.getAttribute("failures"))
                            || !"0".equals(suite.getAttribute("errors"))) {
                        continue;
                    }
                    NodeList cases = suite.getElementsByTagName("testcase");
                    for (int index = 0; index < cases.getLength(); index++) {
                        Element testCase = (Element) cases.item(index);
                        String testName = testCase.getAttribute("name");
                        if (("contextLoads".equals(testName) || "contextLoads()".equals(testName))
                                && testCase.getElementsByTagName("skipped").getLength() == 0) {
                            contextLoadsPassed = true;
                            break;
                        }
                    }
                    if (contextLoadsPassed) {
                        break;
                    }
                } catch (Exception exception) {
                    throw new IOException("Could not parse generated JUnit XML: " + xml, exception);
                }
            }
            assertTrue(contextLoadsPassed, "Generated ApplicationTests.contextLoads did not pass");
        }
    }

    private void clearReport() throws IOException {
        String configured = System.getProperty(REPORT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return;
        }
        Files.deleteIfExists(Path.of(configured).toAbsolutePath().normalize());
    }

    private void writeReport(
            List<FixtureResult> results,
            String sha256,
            boolean deterministicEqual,
            boolean success
    ) throws IOException {
        String configured = System.getProperty(REPORT_PROPERTY);
        if (configured == null || configured.isBlank()) {
            return;
        }
        Path report = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(report.getParent());
        StringBuilder json = new StringBuilder();
        json.append("{\n")
                .append("  \"schemaVersion\": \"1.0\",\n")
                .append("  \"status\": \"").append(success ? "PASS" : "FAIL").append("\",\n")
                .append("  \"generatorTarget\": {\n")
                .append("    \"java\": \"21\",\n")
                .append("    \"springBoot\": \"4.0.8\",\n")
                .append("    \"gradle\": \"9.2.0\"\n")
                .append("  },\n")
                .append("  \"determinism\": {\n")
                .append("    \"fixture\": \"relations\",\n")
                .append("    \"equalZipBytes\": ").append(deterministicEqual).append(",\n")
                .append("    \"sha256\": \"").append(sha256).append("\"\n")
                .append("  },\n")
                .append("  \"fixtures\": [\n");
        for (int index = 0; index < results.size(); index++) {
            FixtureResult result = results.get(index);
            json.append("    {\n")
                    .append("      \"name\": \"").append(result.name()).append("\",\n")
                    .append("      \"coverage\": [");
            for (int coverageIndex = 0; coverageIndex < result.coverage().size(); coverageIndex++) {
                if (coverageIndex > 0) {
                    json.append(", ");
                }
                json.append("\"").append(result.coverage().get(coverageIndex)).append("\"");
            }
            json.append("],\n")
                    .append("      \"generatedBuild\": \"").append(result.generatedBuild()).append("\",\n")
                    .append("      \"contextLoads\": \"").append(result.contextLoads()).append("\",\n")
                    .append("      \"h2\": \"").append(result.h2()).append("\"\n")
                    .append("    }");
            if (index + 1 < results.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  ]\n}\n");
        Files.writeString(report, json.toString(), StandardCharsets.UTF_8);
    }

    private Fixture relationsFixture() {
        UmlClass cliente = identified("relations", "Cliente");
        UmlClass prestamo = identified("relations", "Prestamo");
        UmlClass cuenta = identified("relations", "Cuenta");
        UmlClass perfil = identified("relations", "Perfil");
        UmlClass autor = identified("relations", "Autor");
        UmlClass libro = identified("relations", "Libro");
        UmlClass biblioteca = identified("relations", "Biblioteca");
        UmlClass ejemplar = identified("relations", "Ejemplar");
        UmlClass pedido = identified("relations", "Pedido");
        UmlClass lineaPedido = identified("relations", "LineaPedido");

        return new Fixture(
                "relations",
                "cu13-relations",
                "com.example.cu13relations",
                new UmlModel(
                        List.of(cliente, prestamo, cuenta, perfil, autor, libro, biblioteca, ejemplar, pedido, lineaPedido),
                        List.of(
                                relation("relations:cliente-prestamo", cliente, prestamo, UmlRelationshipType.ASSOCIATION, one(), many()),
                                relation("relations:cuenta-perfil", cuenta, perfil, UmlRelationshipType.ASSOCIATION, one(), zeroOne()),
                                relation("relations:autor-libro", autor, libro, UmlRelationshipType.ASSOCIATION, many(), many()),
                                relation("relations:biblioteca-ejemplar", biblioteca, ejemplar, UmlRelationshipType.AGGREGATION, one(), many()),
                                relation("relations:pedido-linea", pedido, lineaPedido, UmlRelationshipType.COMPOSITION, one(), oneMany())
                        )
                ),
                List.of("simple-id", "1:N", "1:1", "N:M", "aggregation", "composition")
        );
    }

    private Fixture compositeFixture() {
        UmlClass tenant = clazz(
                "composite",
                "Tenant",
                attribute("composite", "Tenant", "countryCode", UmlDataType.STRING, true),
                attribute("composite", "Tenant", "tenantId", UmlDataType.UUID, true)
        );
        UmlClass factura = clazz(
                "composite",
                "Factura",
                attribute("composite", "Factura", "id", UmlDataType.UUID, true),
                attribute("composite", "Factura", "total", UmlDataType.DECIMAL, false)
        );
        return new Fixture(
                "composite",
                "cu13-composite",
                "com.example.cu13composite",
                new UmlModel(
                        List.of(tenant, factura),
                        List.of(relation("composite:tenant-factura", tenant, factura, UmlRelationshipType.ASSOCIATION, one(), many()))
                ),
                List.of("composite-id", "composite-fk")
        );
    }

    private Fixture inheritanceFixture() {
        UmlClass persona = clazz(
                "inheritance",
                "Persona",
                attribute("inheritance", "Persona", "id", UmlDataType.UUID, true),
                attribute("inheritance", "Persona", "nombre", UmlDataType.STRING, false)
        );
        UmlClass empleado = clazz(
                "inheritance",
                "Empleado",
                attribute("inheritance", "Empleado", "salario", UmlDataType.DECIMAL, false)
        );
        UmlClass gerente = clazz(
                "inheritance",
                "Gerente",
                attribute("inheritance", "Gerente", "nivel", UmlDataType.INTEGER, false)
        );
        UmlClass proyecto = clazz(
                "inheritance",
                "Proyecto",
                attribute("inheritance", "Proyecto", "id", UmlDataType.UUID, true),
                attribute("inheritance", "Proyecto", "nombre", UmlDataType.STRING, false)
        );
        return new Fixture(
                "inheritance",
                "cu13-inheritance",
                "com.example.cu13inheritance",
                new UmlModel(
                        List.of(persona, empleado, gerente, proyecto),
                        List.of(
                                relation("inheritance:empleado-persona", empleado, persona, UmlRelationshipType.GENERALIZATION, null, null),
                                relation("inheritance:gerente-empleado", gerente, empleado, UmlRelationshipType.GENERALIZATION, null, null),
                                relation("inheritance:proyecto-empleado", proyecto, empleado, UmlRelationshipType.ASSOCIATION, many(), one())
                        )
                ),
                List.of("joined", "multi-level-joined", "subclass-target")
        );
    }

    private UmlClass identified(String scope, String name) {
        return clazz(scope, name, attribute(scope, name, "id", UmlDataType.UUID, true));
    }

    private UmlClass clazz(String scope, String name, UmlAttribute... attributes) {
        return new UmlClass(uuid(scope + ":class:" + name), name, List.of(attributes));
    }

    private UmlAttribute attribute(String scope, String className, String name, UmlDataType type, boolean identifier) {
        return new UmlAttribute(
                uuid(scope + ":attribute:" + className + ":" + name),
                name,
                type,
                null,
                UmlVisibility.PRIVATE,
                false,
                identifier
        );
    }

    private UmlRelationship relation(
            String key,
            UmlClass source,
            UmlClass target,
            UmlRelationshipType type,
            Multiplicity sourceMultiplicity,
            Multiplicity targetMultiplicity
    ) {
        return new UmlRelationship(
                uuid("relationship:" + key),
                source.id(),
                target.id(),
                type,
                sourceMultiplicity,
                targetMultiplicity
        );
    }

    private UUID uuid(String key) {
        return UUID.nameUUIDFromBytes(("classforge-cu13-" + key).getBytes(StandardCharsets.UTF_8));
    }

    private Multiplicity one() {
        return new Multiplicity(1, 1);
    }

    private Multiplicity zeroOne() {
        return new Multiplicity(0, 1);
    }

    private Multiplicity many() {
        return new Multiplicity(0, null);
    }

    private Multiplicity oneMany() {
        return new Multiplicity(1, null);
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String tail(Path file, int maxChars) throws IOException {
        if (!Files.exists(file)) {
            return "<no log>";
        }
        return tail(Files.readString(file), maxChars);
    }

    private String tail(String value, int maxChars) {
        return value.length() <= maxChars ? value : value.substring(value.length() - maxChars);
    }

    private record Fixture(
            String name,
            String artifactName,
            String basePackage,
            UmlModel model,
            List<String> coverage
    ) { }

    private record FixtureResult(
            String name,
            List<String> coverage,
            String generatedBuild,
            String contextLoads,
            String h2
    ) { }
}
