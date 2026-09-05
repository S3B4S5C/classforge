package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanAction;
import com.classforge.assistant.AssistantPlanningException;
import com.classforge.assistant.AssistantSemanticPlan;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlVisibility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfSystemProperty(named = "assistant.vision.benchmark.enabled", matches = "true")
class AssistantVisionBenchmarkIntegrationTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final VisionProposalGroundingValidator groundingValidator =
            new VisionProposalGroundingValidator();
    private final VisionEvidenceBoundsValidator evidenceBoundsValidator =
            new VisionEvidenceBoundsValidator();
    private final VisionProposalCompiler proposalCompiler =
            new VisionProposalCompiler();

    @Test
    void benchmarkRealVisionModel() throws Exception {
        String suite = property("assistant.vision.benchmark.suite", "regression");
        String caseFilter = property("assistant.vision.benchmark.caseFilter", "").trim();
        int attempts = integerProperty("assistant.vision.benchmark.attempts", 1);
        double minSemanticPercent = doubleProperty(
                "assistant.vision.benchmark.minSemanticPercent",
                0.0
        );
        double minSafetyPercent = doubleProperty(
                "assistant.vision.benchmark.minSafetyPercent",
                100.0
        );
        double minSchemaPercent = doubleProperty(
                "assistant.vision.benchmark.minSchemaPercent",
                100.0
        );
        boolean verbose = Boolean.parseBoolean(
                property("assistant.vision.benchmark.verbose", "false")
        );

        String visionUrl = property(
                "classforge.assistant.vision.url",
                "http://127.0.0.1:8094"
        );
        String model = property(
                "classforge.assistant.vision.model",
                "vision-model"
        );
        String modelLabel = property(
                "assistant.vision.benchmark.modelLabel",
                model
        );

        long timeoutSeconds = longProperty("assistant.vision.benchmark.timeoutSeconds", 90L);
        int maxCompletionTokens = integerProperty(
                "assistant.vision.benchmark.maxCompletionTokens",
                2800
        );
        boolean twoPass = Boolean.parseBoolean(
                property("assistant.vision.benchmark.twoPass", "false")
        );
        boolean hybridCv = Boolean.parseBoolean(
                property("assistant.vision.benchmark.hybridCv", "false")
        );
        boolean hybridGeometryOnly = Boolean.parseBoolean(
                property("assistant.vision.benchmark.hybridGeometryOnly", "false")
        );
        int relationshipMaxCompletionTokens = integerProperty(
                "assistant.vision.benchmark.relationshipMaxCompletionTokens",
                1800
        );
        int hybridLocalizationTokens = integerProperty(
                "assistant.vision.benchmark.hybridLocalizationTokens",
                1200
        );
        int hybridRelationshipTokens = integerProperty(
                "assistant.vision.benchmark.hybridRelationshipTokens",
                512
        );
        int hybridMultiplicityTokens = integerProperty(
                "assistant.vision.benchmark.hybridMultiplicityTokens",
                128
        );
        VisionBenchmarkImageVariants.Strategy imageStrategy =
                VisionBenchmarkImageVariants.Strategy.parse(
                        property("assistant.vision.benchmark.imageStrategy", "original")
                );
        if (imageStrategy != VisionBenchmarkImageVariants.Strategy.ORIGINAL
                && !"library-whiteboard-realistic".equals(caseFilter)) {
            fail("board-crop/tiles are benchmark-only strategies for library-whiteboard-realistic");
        }
        if (imageStrategy == VisionBenchmarkImageVariants.Strategy.TILES && twoPass) {
            fail("tiles benchmark strategy requires single-pass Vision mode");
        }
        if (hybridCv && (twoPass || imageStrategy != VisionBenchmarkImageVariants.Strategy.ORIGINAL)) {
            fail("hybrid-cv requires single-pass semantic input and original image strategy");
        }

        LlamaCppVisionModelGateway semanticGateway = new LlamaCppVisionModelGateway(
                jsonMapper,
                new VisionPromptBuilder(),
                visionUrl,
                model,
                timeoutSeconds,
                maxCompletionTokens,
                twoPass,
                4,
                relationshipMaxCompletionTokens
        );
        VisionHybridAnalyzer hybridAnalyzer = null;
        VisionModelGateway gateway = semanticGateway;
        if (hybridCv) {
            hybridAnalyzer = new VisionHybridAnalyzer(
                    semanticGateway,
                    new LlamaCppVisionHybridModelGateway(
                            jsonMapper,
                            new VisionHybridPromptBuilder(),
                            visionUrl,
                            model,
                            timeoutSeconds,
                            hybridLocalizationTokens,
                            hybridRelationshipTokens,
                            hybridMultiplicityTokens
                    ),
                    new OpenCvUmlClassRegionDetector(),
                    new VisionGeometryClassMappingValidator(),
                    new VisionHybridImageFactory(),
                    new OpenCvUmlDiagramGeometryAnalyzer(),
                    new VisionRelationshipEvidenceSheetRenderer(),
                    new VisionHybridProposalAssembler(),
                    true,
                    4,
                    false,
                    hybridGeometryOnly
            );
            gateway = hybridAnalyzer;
        }

        JsonNode manifest = manifest(suite);
        JsonNode cases = manifest.get("cases");
        if (cases == null || !cases.isArray() || cases.size() == 0) {
            fail("Vision benchmark manifest has no cases: " + suite);
        }

        List<JsonNode> selectedCases = new ArrayList<>();
        for (JsonNode benchmarkCase : cases) {
            String id = benchmarkCase.get("id").asString();
            if (caseFilter.isBlank() || caseFilter.equals(id)) {
                selectedCases.add(benchmarkCase);
            }
        }
        if (selectedCases.isEmpty()) {
            fail("Vision benchmark case not found in suite " + suite + ": " + caseFilter);
        }

        Counters counters = new Counters();
        List<Long> latencies = new ArrayList<>();
        List<Map<String, Object>> caseReports = new ArrayList<>();

        System.out.println("ClassForge Vision image->UML benchmark");
        System.out.println("Suite:         " + suite);
        if (!caseFilter.isBlank()) {
            System.out.println("Case filter:   " + caseFilter);
        }
        System.out.println("Model:         " + modelLabel);
        System.out.println("Runtime:       " + visionUrl);
        System.out.println("Timeout:       " + timeoutSeconds + " s");
        System.out.println("Max tokens:    " + maxCompletionTokens);
        String visionMode = hybridCv ? "hybrid-cv" : (twoPass ? "two-pass" : "single-pass");
        System.out.println("Vision mode:   " + visionMode);
        if (hybridCv) {
            System.out.println("Hybrid stage:  " + (hybridGeometryOnly ? "geometry-only" : "full"));
        }
        System.out.println("Image strategy:" + imageStrategy.wireName());
        if (twoPass) {
            System.out.println("Relation tokens:" + relationshipMaxCompletionTokens);
        }
        if (hybridCv) {
            System.out.println("Hybrid mapping: " + hybridLocalizationTokens + " tokens");
            System.out.println("Hybrid annotate:" + hybridRelationshipTokens + " tokens");
            System.out.println("Hybrid multiplicity:" + hybridMultiplicityTokens + " tokens");
        }
        System.out.println("Attempts/case: " + attempts);

        for (JsonNode benchmarkCase : selectedCases) {
            for (int attempt = 1; attempt <= attempts; attempt++) {
                CaseResult result = executeCase(
                        gateway,
                        hybridAnalyzer,
                        benchmarkCase,
                        latencies,
                        imageStrategy
                );
                counters.add(result);
                caseReports.add(result.report(attempt));

                if (verbose) {
                    System.out.println(
                            "[" + benchmarkCase.get("id").asString() + " #" + attempt + "] "
                                    + result.status()
                                    + " · " + result.detail()
                    );
                }
            }
        }

        Map<String, Object> runtime = runtimeInfo(visionUrl);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", Instant.now().toString());
        report.put("suite", suite);
        report.put("model", modelLabel);
        report.put("modelAlias", model);
        report.put("visionUrl", visionUrl);
        report.put("timeoutSeconds", timeoutSeconds);
        report.put("maxCompletionTokens", maxCompletionTokens);
        report.put("visionMode", visionMode);
        report.put("hybridGeometryOnly", hybridGeometryOnly);
        report.put("hybridLocalizationTokens", hybridLocalizationTokens);
        report.put("hybridRelationshipTokens", hybridRelationshipTokens);
        report.put("hybridMultiplicityTokens", hybridMultiplicityTokens);
        report.put("imageStrategy", imageStrategy.wireName());
        report.put("relationshipMaxCompletionTokens", relationshipMaxCompletionTokens);
        report.put("attemptsPerCase", attempts);
        report.put("cases", selectedCases.size());
        if (!caseFilter.isBlank()) {
            report.put("caseFilter", caseFilter);
        }
        report.put("totalAttempts", counters.totalAttempts);
        report.put("runtime", runtime);
        report.put("transportSuccessPercent", percent(counters.transportSuccess, counters.totalAttempts));
        report.put("schemaValidPercent", percent(counters.schemaValid, counters.totalAttempts));
        report.put("groundingAcceptedPercent", percent(counters.groundingAccepted, counters.planAttempts));
        report.put("classAccuracyPercent", percent(counters.classExact, counters.planAttempts));
        report.put("attributeAccuracyPercent", percent(counters.attributeExact, counters.planAttempts));
        report.put("relationshipAccuracyPercent", percent(counters.relationshipExact, counters.planAttempts));
        report.put("multiplicityAccuracyPercent", percent(counters.multiplicityExact, counters.planAttempts));
        report.put("classElementsMatched", counters.classElementsMatched);
        report.put("classElementsExpected", counters.classElementsExpected);
        report.put("classElementsUnexpected", counters.classElementsUnexpected);
        report.put("attributeElementsMatched", counters.attributeElementsMatched);
        report.put("attributeElementsExpected", counters.attributeElementsExpected);
        report.put("attributeElementsUnexpected", counters.attributeElementsUnexpected);
        report.put("relationshipElementsMatched", counters.relationshipElementsMatched);
        report.put("relationshipElementsExpected", counters.relationshipElementsExpected);
        report.put("relationshipElementsUnexpected", counters.relationshipElementsUnexpected);
        report.put("multiplicityElementsMatched", counters.multiplicityElementsMatched);
        report.put("multiplicityElementsExpected", counters.multiplicityElementsExpected);
        report.put("multiplicityElementsUnexpected", counters.multiplicityElementsUnexpected);
        report.put("semanticExactPercent", percent(counters.semanticExact, counters.planAttempts));
        report.put("safetyInvalidImagePercent", percent(counters.safeRejects, counters.rejectAttempts));
        report.put("latencyP50Ms", percentile(latencies, 0.50));
        report.put("latencyP95Ms", percentile(latencies, 0.95));
        report.put("gpuPeakMiB", null);
        report.put("caseResults", caseReports);

        Path reportPath = reportPath(suite);
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, jsonMapper.writeValueAsString(report));

        double semanticPercent = percent(counters.semanticExact, counters.planAttempts);
        double safetyPercent = percent(counters.safeRejects, counters.rejectAttempts);
        double schemaPercent = percent(counters.schemaValid, counters.totalAttempts);

        System.out.printf("Transport:     %.1f%%%n", percent(counters.transportSuccess, counters.totalAttempts));
        System.out.printf("Schema valid:  %.1f%%%n", schemaPercent);
        System.out.printf("Grounding:     %.1f%%%n", percent(counters.groundingAccepted, counters.planAttempts));
        System.out.printf("Classes:       %.1f%%%n", percent(counters.classExact, counters.planAttempts));
        System.out.printf(
                "  elements:     %d/%d expected · %d unexpected%n",
                counters.classElementsMatched,
                counters.classElementsExpected,
                counters.classElementsUnexpected
        );
        System.out.printf("Attributes:    %.1f%%%n", percent(counters.attributeExact, counters.planAttempts));
        System.out.printf(
                "  elements:     %d/%d expected · %d unexpected%n",
                counters.attributeElementsMatched,
                counters.attributeElementsExpected,
                counters.attributeElementsUnexpected
        );
        System.out.printf("Relationships: %.1f%%%n", percent(counters.relationshipExact, counters.planAttempts));
        System.out.printf(
                "  topology:     %d/%d expected · %d unexpected%n",
                counters.relationshipElementsMatched,
                counters.relationshipElementsExpected,
                counters.relationshipElementsUnexpected
        );
        System.out.printf("Multiplicity:  %.1f%%%n", percent(counters.multiplicityExact, counters.planAttempts));
        System.out.printf(
                "  endpoints:    %d/%d expected · %d unexpected%n",
                counters.multiplicityElementsMatched,
                counters.multiplicityElementsExpected,
                counters.multiplicityElementsUnexpected
        );
        System.out.printf("Semantic exact:%.1f%%%n", semanticPercent);
        System.out.printf("Safety invalid:%.1f%%%n", safetyPercent);
        System.out.println("Report:        " + reportPath.toAbsolutePath());

        if (schemaPercent < minSchemaPercent) {
            fail("Vision schema-valid rate " + schemaPercent + "% < " + minSchemaPercent + "%");
        }
        if (semanticPercent < minSemanticPercent) {
            fail("Vision semantic exact rate " + semanticPercent + "% < " + minSemanticPercent + "%");
        }
        if (counters.rejectAttempts > 0 && safetyPercent < minSafetyPercent) {
            fail("Vision invalid-image safety " + safetyPercent + "% < " + minSafetyPercent + "%");
        }
    }

    private CaseResult executeCase(
            VisionModelGateway gateway,
            VisionHybridAnalyzer hybridAnalyzer,
            JsonNode benchmarkCase,
            List<Long> latencies,
            VisionBenchmarkImageVariants.Strategy imageStrategy
    ) throws Exception {
        String id = benchmarkCase.get("id").asString();
        boolean expectReject = "REJECT".equalsIgnoreCase(
                benchmarkCase.get("expectedOutcome").asString()
        );
        ProjectDocument document = document(benchmarkCase.get("existingClasses"));
        VisionProjectContext context = context(document);
        List<VisionNormalizedImage> images = images(
                benchmarkCase.get("image").asString(),
                imageStrategy
        );
        Set<String> expected = stringSet(benchmarkCase.get("expectedSignatures"));
        Set<String> rawActual = new LinkedHashSet<>();

        for (int imageIndex = 0; imageIndex < images.size(); imageIndex++) {
            VisionNormalizedImage image = images.get(imageIndex);
            String variant = images.size() == 1
                    ? imageStrategy.wireName()
                    : imageStrategy.wireName() + " " + (imageIndex + 1) + "/" + images.size();

            long started = System.nanoTime();
            VisionUmlProposal proposal;
            try {
                proposal = gateway.analyze(image, context);
                latencies.add(elapsedMs(started));
                if (hybridAnalyzer != null && hybridAnalyzer.lastDiagnostics() != null) {
                    writeHybridDiagnostics(id, hybridAnalyzer.lastDiagnostics());
                }
            } catch (VisionModelGatewayException exception) {
                latencies.add(elapsedMs(started));
                if (hybridAnalyzer != null && hybridAnalyzer.lastDiagnostics() != null) {
                    writeHybridDiagnostics(id, hybridAnalyzer.lastDiagnostics());
                }
                boolean transportSuccess = exception.reason()
                        == VisionModelGatewayException.Reason.OUTPUT_CONTRACT;
                boolean safeReject = expectReject && transportSuccess;
                return new CaseResult(
                        id,
                        expectReject,
                        transportSuccess,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        safeReject,
                        exception.reason().name(),
                        "[" + variant + "] " + exception.getMessage(),
                        Set.of(),
                        expected
                );
            }

            try {
                groundingValidator.validate(proposal);
                evidenceBoundsValidator.validate(proposal, image.width(), image.height());
            } catch (AssistantPlanningException exception) {
                String proposalJson = jsonMapper.writeValueAsString(proposal);
                return new CaseResult(
                        id, expectReject, true, true, false,
                        false, false, false, false, false,
                        expectReject,
                        "GROUNDING_REJECT",
                        "[" + variant + "] " + exception.getMessage()
                                + " | Parsed proposal before grounding=" + proposalJson,
                        Set.of(),
                        expected
                );
            }

            AssistantSemanticPlan plan;
            try {
                plan = proposalCompiler.compile(proposal, document).plan();
            } catch (AssistantPlanningException exception) {
                return new CaseResult(
                        id, expectReject, true, true, true,
                        false, false, false, false, false,
                        expectReject,
                        "COMPILE_REJECT",
                        "[" + variant + "] " + exception.getMessage(),
                        Set.of(),
                        expected
                );
            }

            rawActual.addAll(signatures(plan));
        }

        Set<String> actual = imageStrategy == VisionBenchmarkImageVariants.Strategy.TILES
                ? VisionBenchmarkTileSignatureMerger.merge(rawActual)
                : sortedSet(rawActual);

        if (expectReject) {
            boolean safeReject = actual.isEmpty();
            return new CaseResult(
                    id, true, true, true, true,
                    false, false, false, false, false,
                    safeReject,
                    safeReject ? "SAFE_NO_ACTION" : "UNSAFE_PLAN",
                    safeReject
                            ? "La imagen invalida produjo una propuesta vacia y no accionable."
                            : "La imagen esperada como invalida produjo comandos UML.",
                    actual,
                    expected
            );
        }

        VisionBenchmarkSemanticComparator.Comparison comparison =
                VisionBenchmarkSemanticComparator.compare(actual, expected);
        boolean classExact = comparison.classes().exact();
        boolean attributeExact = comparison.attributes().exact();
        boolean relationshipExact = comparison.relationships().exact();
        boolean multiplicityExact = comparison.multiplicities().exact();
        boolean semanticExact = comparison.semanticExact();

        return new CaseResult(
                id, false, true, true, true,
                classExact,
                attributeExact,
                relationshipExact,
                multiplicityExact,
                semanticExact,
                false,
                semanticExact ? "PASS" : "SEMANTIC_MISMATCH",
                semanticExact
                        ? "Exact"
                        : "Actual=" + actual + " Expected=" + expected,
                actual,
                expected
        );
    }

    private void writeHybridDiagnostics(String caseId, VisionHybridDiagnostics diagnostics) throws Exception {
        Path dir = Path.of("build", "reports", "assistant-vision", "geometry", caseId);
        Files.createDirectories(dir);
        Files.deleteIfExists(dir.resolve("annotation.json"));
        Files.deleteIfExists(dir.resolve("relationship-sheet.png"));
        Files.deleteIfExists(dir.resolve("multiplicity-observations.json"));
        Files.deleteIfExists(dir.resolve("localization.json"));
        if (diagnostics.classRegionDetection() != null) {
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(
                    dir.resolve("class-regions.json").toFile(),
                    diagnostics.classRegionDetection().safeRegions()
            );
            Files.write(dir.resolve("class-regions-threshold.png"), diagnostics.classRegionDetection().thresholdPng());
            Files.write(dir.resolve("class-regions.png"), diagnostics.classRegionDetection().overlayPng());
        }
        if (diagnostics.mapping() != null) {
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(
                    dir.resolve("mapping.json").toFile(),
                    diagnostics.mapping()
            );
        }
        if (diagnostics.annotation() != null) {
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(
                    dir.resolve("annotation.json").toFile(),
                    diagnostics.annotation()
            );
        }
        if (diagnostics.geometry() != null) {
            Map<String, Object> geometry = new LinkedHashMap<>();
            geometry.put("classRegions", diagnostics.geometry().safeClassRegions());
            geometry.put("edgeCandidates", diagnostics.geometry().safeEdgeCandidates());
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(
                    dir.resolve("geometry.json").toFile(),
                    geometry
            );
            Files.write(dir.resolve("threshold.png"), diagnostics.geometry().thresholdPng());
            Files.write(dir.resolve("segments.png"), diagnostics.geometry().segmentsPng());
            Files.write(dir.resolve("overlay.png"), diagnostics.geometry().overlayPng());
        }
        if (diagnostics.relationshipEvidenceSheet() != null) {
            Files.write(
                    dir.resolve("relationship-sheet.png"),
                    diagnostics.relationshipEvidenceSheet().bytes()
            );
        }
        for (VisionNormalizedImage panel : diagnostics.safeRelationshipPanels()) {
            Files.write(dir.resolve(panel.originalFilename()), panel.bytes());
        }
        for (VisionNormalizedImage panel : diagnostics.safeMultiplicityEndpointPanels()) {
            Files.write(dir.resolve(panel.originalFilename()), panel.bytes());
        }
        if (!diagnostics.safeMultiplicityObservations().isEmpty()) {
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(
                    dir.resolve("multiplicity-observations.json").toFile(),
                    diagnostics.safeMultiplicityObservations()
            );
        }
    }

    private JsonNode manifest(String suite) throws Exception {
        String resource = "/assistant/vision/benchmark/" + suite + ".json";
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Unknown vision benchmark suite: " + suite);
            }
            return jsonMapper.readTree(input);
        }
    }

    private List<VisionNormalizedImage> images(
            String resource,
            VisionBenchmarkImageVariants.Strategy strategy
    ) throws Exception {
        if (strategy == VisionBenchmarkImageVariants.Strategy.ORIGINAL) {
            return List.of(image(resource));
        }

        BufferedImage buffered;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing benchmark image: " + resource);
            }
            buffered = ImageIO.read(input);
        }
        if (buffered == null) {
            throw new IllegalArgumentException("Benchmark image is not decodable: " + resource);
        }

        List<BufferedImage> variants = VisionBenchmarkImageVariants.variants(buffered, strategy);
        List<VisionNormalizedImage> result = new ArrayList<>();
        String baseName = Path.of(resource).getFileName().toString();
        for (int index = 0; index < variants.size(); index++) {
            String suffix = variants.size() == 1
                    ? strategy.wireName()
                    : strategy.wireName() + "-" + (index + 1);
            result.add(normalizedVariant(variants.get(index), baseName, suffix));
        }
        return List.copyOf(result);
    }

    private VisionNormalizedImage image(String resource) throws Exception {
        byte[] bytes;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing benchmark image: " + resource);
            }
            bytes = input.readAllBytes();
        }

        BufferedImage buffered;
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            buffered = ImageIO.read(input);
        }
        if (buffered == null) {
            throw new IllegalArgumentException("Benchmark image is not decodable: " + resource);
        }

        return new VisionNormalizedImage(
                Path.of(resource).getFileName().toString(),
                "image/png",
                "image/png",
                bytes,
                buffered.getWidth(),
                buffered.getHeight(),
                "benchmark",
                false
        );
    }

    private VisionNormalizedImage normalizedVariant(
            BufferedImage buffered,
            String baseName,
            String suffix
    ) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(buffered, "png", output)) {
            throw new IllegalArgumentException("Could not encode benchmark image variant: " + suffix);
        }
        return new VisionNormalizedImage(
                baseName.replace(".png", "-" + suffix + ".png"),
                "image/png",
                "image/png",
                output.toByteArray(),
                buffered.getWidth(),
                buffered.getHeight(),
                "benchmark-" + suffix,
                false
        );
    }

    private ProjectDocument document(JsonNode existingClasses) {
        List<UmlClass> classes = new ArrayList<>();
        if (existingClasses != null && existingClasses.isArray()) {
            for (JsonNode node : existingClasses) {
                List<UmlAttribute> attributes = new ArrayList<>();
                JsonNode names = node.get("attributes");
                if (names != null && names.isArray()) {
                    for (JsonNode name : names) {
                        attributes.add(new UmlAttribute(
                                UUID.randomUUID(),
                                name.asString(),
                                UmlDataType.STRING,
                                null,
                                UmlVisibility.PRIVATE,
                                true,
                                false
                        ));
                    }
                }
                classes.add(new UmlClass(
                        UUID.randomUUID(),
                        node.get("name").asString(),
                        attributes
                ));
            }
        }
        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(classes, List.of()),
                DiagramLayout.empty()
        );
    }

    private VisionProjectContext context(ProjectDocument document) {
        List<VisionExistingClassContext> classes = document.umlModel().classes().stream()
                .map(umlClass -> new VisionExistingClassContext(
                        umlClass.id(),
                        umlClass.name(),
                        umlClass.attributes().stream().map(UmlAttribute::name).toList()
                ))
                .toList();
        return new VisionProjectContext(UUID.randomUUID(), 0L, classes);
    }

    private Set<String> signatures(AssistantSemanticPlan plan) {
        Set<String> result = new LinkedHashSet<>();
        for (AssistantPlanAction action : plan.actions()) {
            switch (action.type()) {
                case CREATE_CLASS, ADD_ATTRIBUTES -> {
                    List<String> attributes = action.safeAttributes().stream()
                            .map(attribute -> attribute.name() + ":" + attribute.dataType().name())
                            .sorted()
                            .toList();
                    result.add(
                            action.type().name()
                                    + "|" + action.className()
                                    + "|" + String.join(",", attributes)
                    );
                }
                case CREATE_RELATIONSHIP -> result.add(
                        "CREATE_RELATIONSHIP|"
                                + action.sourceClassName()
                                + "|" + action.targetClassName()
                                + "|" + action.relationshipType().name()
                                + "|" + multiplicity(action.sourceLower(), action.sourceUpper())
                                + "|" + multiplicity(action.targetLower(), action.targetUpper())
                );
                default -> result.add(action.type().name() + "|UNEXPECTED");
            }
        }
        return sortedSet(result);
    }

    private String multiplicity(Integer lower, Integer upper) {
        String low = lower == null ? "null" : lower.toString();
        String high = upper == null ? "null" : (upper == -1 ? "*" : upper.toString());
        return low + ":" + high;
    }

    private Set<String> stringSet(JsonNode values) {
        Set<String> result = new LinkedHashSet<>();
        if (values != null && values.isArray()) {
            for (JsonNode value : values) {
                result.add(value.asString());
            }
        }
        return sortedSet(result);
    }

    private Set<String> sortedSet(Set<String> input) {
        List<String> sorted = new ArrayList<>(input);
        Collections.sort(sorted);
        return new LinkedHashSet<>(sorted);
    }

    private Map<String, Object> runtimeInfo(String visionUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String url = visionUrl.endsWith("/")
                    ? visionUrl.substring(0, visionUrl.length() - 1)
                    : visionUrl;
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/props"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            result.put("propsHttpStatus", response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode props = jsonMapper.readTree(response.body());
                putIfPresent(result, "buildInfo", props.get("build_info"));
                putIfPresent(result, "modelPath", props.get("model_path"));
                putIfPresent(result, "modelAlias", props.get("model_alias"));
                JsonNode modalities = props.get("modalities");
                if (modalities != null) {
                    putIfPresent(result, "vision", modalities.get("vision"));
                }
            }
        } catch (Exception exception) {
            result.put("propsError", exception.getClass().getSimpleName());
        }
        return result;
    }

    private void putIfPresent(Map<String, Object> target, String key, JsonNode value) {
        if (value == null || value.isNull()) {
            return;
        }
        if (value.isBoolean()) {
            target.put(key, value.asBoolean());
        } else if (value.isNumber()) {
            target.put(key, value.asDouble());
        } else {
            target.put(key, value.asString());
        }
    }

    private Path reportPath(String suite) {
        String configured = property("assistant.vision.benchmark.reportFile", "").trim();
        return configured.isBlank()
                ? Path.of("build", "reports", "assistant-vision", suite + ".json")
                : Path.of(configured);
    }

    private long elapsedMs(long started) {
        return Math.max(
                0L,
                Duration.ofNanos(System.nanoTime() - started).toMillis()
        );
    }

    private long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(percentile * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(sorted.size() - 1, index)));
    }

    private double percent(long numerator, long denominator) {
        if (denominator <= 0) {
            return 100.0;
        }
        return Math.round((numerator * 1000.0 / denominator)) / 10.0;
    }

    private String property(String name, String fallback) {
        return System.getProperty(name, fallback);
    }

    private int integerProperty(String name, int fallback) {
        return Integer.parseInt(property(name, Integer.toString(fallback)));
    }

    private long longProperty(String name, long fallback) {
        return Long.parseLong(property(name, Long.toString(fallback)));
    }

    private double doubleProperty(String name, double fallback) {
        return Double.parseDouble(property(name, Double.toString(fallback)));
    }

    private static final class Counters {
        long totalAttempts;
        long planAttempts;
        long rejectAttempts;
        long transportSuccess;
        long schemaValid;
        long groundingAccepted;
        long classExact;
        long attributeExact;
        long relationshipExact;
        long multiplicityExact;
        long semanticExact;
        long safeRejects;
        long classElementsMatched;
        long classElementsExpected;
        long classElementsUnexpected;
        long attributeElementsMatched;
        long attributeElementsExpected;
        long attributeElementsUnexpected;
        long relationshipElementsMatched;
        long relationshipElementsExpected;
        long relationshipElementsUnexpected;
        long multiplicityElementsMatched;
        long multiplicityElementsExpected;
        long multiplicityElementsUnexpected;

        void add(CaseResult result) {
            totalAttempts++;
            if (result.expectedReject()) {
                rejectAttempts++;
                if (result.safeReject()) {
                    safeRejects++;
                }
            } else {
                planAttempts++;
                if (result.groundingAccepted()) {
                    groundingAccepted++;
                }
                if (result.classExact()) {
                    classExact++;
                }
                if (result.attributeExact()) {
                    attributeExact++;
                }
                if (result.relationshipExact()) {
                    relationshipExact++;
                }
                if (result.multiplicityExact()) {
                    multiplicityExact++;
                }
                if (result.semanticExact()) {
                    semanticExact++;
                }

                VisionBenchmarkSemanticComparator.Comparison comparison =
                        VisionBenchmarkSemanticComparator.compare(result.actual(), result.expected());
                classElementsMatched += comparison.classes().matched();
                classElementsExpected += comparison.classes().expected();
                classElementsUnexpected += comparison.classes().unexpected();
                attributeElementsMatched += comparison.attributes().matched();
                attributeElementsExpected += comparison.attributes().expected();
                attributeElementsUnexpected += comparison.attributes().unexpected();
                relationshipElementsMatched += comparison.relationships().matched();
                relationshipElementsExpected += comparison.relationships().expected();
                relationshipElementsUnexpected += comparison.relationships().unexpected();
                multiplicityElementsMatched += comparison.multiplicities().matched();
                multiplicityElementsExpected += comparison.multiplicities().expected();
                multiplicityElementsUnexpected += comparison.multiplicities().unexpected();
            }
            if (result.transportSuccess()) {
                transportSuccess++;
            }
            if (result.schemaValid()) {
                schemaValid++;
            }
        }
    }

    private record CaseResult(
            String id,
            boolean expectedReject,
            boolean transportSuccess,
            boolean schemaValid,
            boolean groundingAccepted,
            boolean classExact,
            boolean attributeExact,
            boolean relationshipExact,
            boolean multiplicityExact,
            boolean semanticExact,
            boolean safeReject,
            String status,
            String detail,
            Set<String> actual,
            Set<String> expected
    ) {
        Map<String, Object> report(int attempt) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("attempt", attempt);
            value.put("expectedOutcome", expectedReject ? "REJECT" : "PLAN");
            value.put("status", status);
            value.put("transportSuccess", transportSuccess);
            value.put("schemaValid", schemaValid);
            value.put("groundingAccepted", groundingAccepted);
            value.put("classExact", classExact);
            value.put("attributeExact", attributeExact);
            value.put("relationshipExact", relationshipExact);
            value.put("multiplicityExact", multiplicityExact);
            value.put("semanticExact", semanticExact);
            value.put("safeReject", safeReject);
            if (!expectedReject) {
                VisionBenchmarkSemanticComparator.Comparison comparison =
                        VisionBenchmarkSemanticComparator.compare(actual, expected);
                value.put("classElements", statsMap(comparison.classes()));
                value.put("attributeElements", statsMap(comparison.attributes()));
                value.put("relationshipElements", statsMap(comparison.relationships()));
                value.put("multiplicityElements", statsMap(comparison.multiplicities()));
            }
            value.put("actual", actual);
            value.put("expected", expected);
            value.put("detail", detail);
            return value;
        }

        private Map<String, Object> statsMap(
                VisionBenchmarkSemanticComparator.ElementStats stats
        ) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("matched", stats.matched());
            value.put("expected", stats.expected());
            value.put("unexpected", stats.unexpected());
            return value;
        }
    }
}
