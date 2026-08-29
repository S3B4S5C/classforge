package com.classforge.assistant;

import com.classforge.auth.web.AuthResponse;
import com.classforge.auth.web.RegisterRequest;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import com.classforge.project.domain.document.UmlVisibility;
import com.classforge.project.web.CreateProjectRequest;
import com.classforge.project.web.ProjectResponse;
import com.classforge.project.web.SaveProjectDocumentRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:classforge-assistant-e2e-reliability;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        }
)
@EnabledIfSystemProperty(
        named = "assistant.benchmark.enabled",
        matches = "true"
)
class AssistantE2EReliabilityIntegrationTest {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(100);
    private static final int MAX_FAILURE_EXAMPLES = 5;

    @LocalServerPort
    private int port;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void benchmarksRealAssistantEndpointAcrossOperationsAndTypos() throws Exception {
        BenchmarkConfig config = BenchmarkConfig.fromSystemProperties();
        Fixture fixture = Fixture.mediumVeterinaryProject();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        String baseUrl = "http://127.0.0.1:" + port;

        AuthResponse auth = registerBenchmarkUser(
                httpClient,
                baseUrl
        );

        ProjectResponse saved = createAndSaveBenchmarkProject(
                httpClient,
                baseUrl,
                auth.accessToken(),
                fixture.document()
        );

        BenchmarkClient client = new BenchmarkClient(
                httpClient,
                jsonMapper,
                baseUrl,
                saved.id(),
                auth.accessToken()
        );

        AssistantRuntimeHealthResponse health = client.health();

        System.out.println();
        System.out.println("ClassForge Assistant E2E reliability benchmark");
        System.out.println("==============================================");
        System.out.println("Endpoint:      POST /api/projects/{id}/assistant/plan");
        System.out.println("Project:       Veterinaria E2E Benchmark");
        System.out.println("Classes:       " + fixture.classIds().size());
        System.out.println("Relationships: " + fixture.relationshipIds().size());
        String plannerMode = System.getProperty("classforge.assistant.planner-mode", "legacy");
        System.out.println("Planner mode:  " + plannerMode);
        System.out.println("Attempts/type: " + config.attemptsPerCategory());
        System.out.printf(
                Locale.ROOT,
                "ERROR rule:   failure rate > %.1f%%%n",
                config.errorFailurePercent()
        );
        System.out.println("LLM health:    " + health.llama().state() + " - " + health.llama().message());
        System.out.println();

        assertTrue(
                health.readyForText(),
                () -> "llama.cpp no esta listo para el benchmark: " + health.llama().message()
        );

        List<Scenario> scenarios = scenarios(fixture);
        List<CategoryResult> results = new ArrayList<>();

        for (Scenario scenario : scenarios) {
            results.add(runScenario(client, scenario, config));
        }

        printSummary(results, config);
        writeMachineReport(results, config, plannerMode);

        List<CategoryResult> errors = results.stream()
                .filter(result -> result.status(config) == BenchmarkStatus.ERROR)
                .toList();

        assertTrue(
                errors.isEmpty(),
                () -> "Categorias ERROR (safety falla con cualquier unsafe accept; resto usa > "
                        + config.errorFailurePercent()
                        + "%): "
                        + errors.stream().map(CategoryResult::category).toList()
        );
    }


    private AuthResponse registerBenchmarkUser(
            HttpClient httpClient,
            String baseUrl
    ) throws Exception {
        RegisterRequest registration = new RegisterRequest(
                "Benchmark Owner",
                "assistant-benchmark-" + UUID.randomUUID() + "@classforge.local",
                "benchmark-secret-2026"
        );

        HttpResponse<String> response = sendJson(
                httpClient,
                "POST",
                baseUrl + "/api/auth/register",
                null,
                registration
        );

        if (response.statusCode() != 201) {
            throw new IllegalStateException(
                    "No se pudo registrar el usuario benchmark. HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }

        return jsonMapper.readValue(response.body(), AuthResponse.class);
    }

    private ProjectResponse createAndSaveBenchmarkProject(
            HttpClient httpClient,
            String baseUrl,
            String token,
            ProjectDocument document
    ) throws Exception {
        HttpResponse<String> createResponse = sendJson(
                httpClient,
                "POST",
                baseUrl + "/api/projects",
                token,
                new CreateProjectRequest("Veterinaria E2E Benchmark")
        );

        if (createResponse.statusCode() != 201) {
            throw new IllegalStateException(
                    "No se pudo crear el proyecto benchmark. HTTP "
                            + createResponse.statusCode()
                            + ": "
                            + createResponse.body()
            );
        }

        ProjectResponse created = jsonMapper.readValue(
                createResponse.body(),
                ProjectResponse.class
        );

        HttpResponse<String> saveResponse = sendJson(
                httpClient,
                "PUT",
                baseUrl + "/api/projects/" + created.id() + "/document",
                token,
                new SaveProjectDocumentRequest(
                        created.revision(),
                        document
                )
        );

        if (saveResponse.statusCode() != 200) {
            throw new IllegalStateException(
                    "No se pudo guardar el fixture UML benchmark. HTTP "
                            + saveResponse.statusCode()
                            + ": "
                            + saveResponse.body()
            );
        }

        return jsonMapper.readValue(saveResponse.body(), ProjectResponse.class);
    }

    private HttpResponse<String> sendJson(
            HttpClient httpClient,
            String method,
            String url,
            String token,
            Object body
    ) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json");

        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token);
        }

        String json = jsonMapper.writeValueAsString(body);

        builder.method(
                method,
                HttpRequest.BodyPublishers.ofString(json)
        );

        return httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private CategoryResult runScenario(
            BenchmarkClient client,
            Scenario scenario,
            BenchmarkConfig config
    ) {
        int passed = 0;
        long totalNanos = 0L;
        Map<String, Integer> failureReasons = new LinkedHashMap<>();
        List<FailureExample> failureExamples = new ArrayList<>();

        System.out.println("==> " + scenario.category());

        for (int attempt = 1; attempt <= config.attemptsPerCategory(); attempt++) {
            PromptCase promptCase = scenario.cases().get(
                    Math.floorMod(attempt - 1, scenario.cases().size())
            );

            long startedAt = System.nanoTime();
            HttpOutcome outcome;

            try {
                outcome = client.plan(promptCase.prompt());
            } catch (Exception exception) {
                outcome = HttpOutcome.transportFailure(exception);
            }

            totalNanos += System.nanoTime() - startedAt;

            Evaluation evaluation;

            try {
                evaluation = promptCase.evaluator().evaluate(outcome);
            } catch (Exception exception) {
                evaluation = Evaluation.failure(
                        "EVALUATOR_EXCEPTION",
                        exception.getClass().getSimpleName() + ": " + exception.getMessage()
                );
            }

            if (evaluation.success()) {
                passed++;
            } else {
                failureReasons.merge(evaluation.reason(), 1, Integer::sum);

                if (failureExamples.size() < MAX_FAILURE_EXAMPLES) {
                    failureExamples.add(
                            new FailureExample(
                                    attempt,
                                    promptCase.label(),
                                    promptCase.prompt(),
                                    outcome.statusCode(),
                                    outcome.stage(),
                                    evaluation.reason(),
                                    evaluation.detail()
                            )
                    );
                }
            }

            if (config.verboseAttempts() || !evaluation.success()) {
                System.out.printf(
                        Locale.ROOT,
                        "  [%02d/%02d] %-5s %-18s %s%n",
                        attempt,
                        config.attemptsPerCategory(),
                        evaluation.success() ? "OK" : "FAIL",
                        promptCase.label(),
                        evaluation.success() ? "" : evaluation.reason()
                );
            }
        }

        CategoryResult result = new CategoryResult(
                scenario.category(),
                passed,
                config.attemptsPerCategory(),
                totalNanos,
                Map.copyOf(failureReasons),
                List.copyOf(failureExamples)
        );

        System.out.printf(
                Locale.ROOT,
                "    approval=%.1f%% failure=%.1f%% avg=%.0fms status=%s%n%n",
                result.approvalPercent(),
                result.failurePercent(),
                result.averageMillis(),
                result.status(config)
        );

        return result;
    }

    private void printSummary(
            List<CategoryResult> results,
            BenchmarkConfig config
    ) {
        System.out.println();
        System.out.println("FINAL SUMMARY");
        System.out.println("=============");
        System.out.printf(
                "%-32s %8s %10s %10s %10s %10s%n",
                "CATEGORY",
                "PASS",
                "APPROVAL",
                "FAILURE",
                "AVG MS",
                "STATUS"
        );

        for (CategoryResult result : results) {
            System.out.printf(
                    Locale.ROOT,
                    "%-32s %3d/%-4d %9.1f%% %9.1f%% %10.0f %10s%n",
                    result.category(),
                    result.passed(),
                    result.total(),
                    result.approvalPercent(),
                    result.failurePercent(),
                    result.averageMillis(),
                    result.status(config)
            );
        }

        int totalAttempts = results.stream().mapToInt(CategoryResult::total).sum();
        int totalPassed = results.stream().mapToInt(CategoryResult::passed).sum();
        double overallApproval = totalAttempts == 0
                ? 0.0d
                : totalPassed * 100.0d / totalAttempts;

        System.out.println();
        System.out.printf(
                Locale.ROOT,
                "OVERALL: %d/%d = %.1f%% approval%n",
                totalPassed,
                totalAttempts,
                overallApproval
        );
        System.out.printf(
                Locale.ROOT,
                "A category is ERROR only when its failure rate is > %.1f%%%n",
                config.errorFailurePercent()
        );

        for (CategoryResult result : results) {
            if (result.failures().isEmpty()) {
                continue;
            }

            System.out.println();
            System.out.println("Failures - " + result.category());
            result.failureReasons().forEach(
                    (reason, count) -> System.out.println("  " + reason + ": " + count)
            );

            for (FailureExample example : result.failures()) {
                System.out.println(
                        "  #" + example.attempt()
                                + " [" + example.variant() + "] "
                                + "HTTP=" + example.httpStatus()
                                + " stage=" + valueOrDash(example.stage())
                                + " reason=" + example.reason()
                );
                System.out.println("     prompt: " + example.prompt());
                System.out.println("     detail: " + example.detail());
            }
        }

        System.out.println();
    }

    private void writeMachineReport(
            List<CategoryResult> results,
            BenchmarkConfig config,
            String plannerMode
    ) throws Exception {
        String reportFile = System.getProperty("assistant.benchmark.reportFile", "").trim();
        if (reportFile.isBlank()) {
            return;
        }

        int totalAttempts = results.stream().mapToInt(CategoryResult::total).sum();
        int totalPassed = results.stream().mapToInt(CategoryResult::passed).sum();
        double overallApproval = totalAttempts == 0
                ? 0.0d
                : totalPassed * 100.0d / totalAttempts;

        List<Map<String, Object>> categories = results.stream()
                .map(result -> {
                    Map<String, Object> category = new LinkedHashMap<>();
                    category.put("category", result.category());
                    category.put("passed", result.passed());
                    category.put("total", result.total());
                    category.put("approval", result.approvalPercent());
                    category.put("failure", result.failurePercent());
                    category.put("averageMillis", result.averageMillis());
                    category.put("status", result.status(config).name());
                    category.put("failureReasons", result.failureReasons());
                    category.put(
                            "failures",
                            result.failures().stream().map(example -> Map.<String, Object>of(
                                    "attempt", example.attempt(),
                                    "variant", example.variant(),
                                    "prompt", example.prompt(),
                                    "httpStatus", example.httpStatus(),
                                    "stage", valueOrDash(example.stage()),
                                    "reason", example.reason(),
                                    "detail", example.detail()
                            )).toList()
                    );
                    return category;
                })
                .toList();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("plannerMode", plannerMode);
        report.put("attemptsPerCategory", config.attemptsPerCategory());
        report.put("overallPassed", totalPassed);
        report.put("overallTotal", totalAttempts);
        report.put("overallApproval", overallApproval);
        report.put("categories", categories);

        Path path = Path.of(reportFile).toAbsolutePath().normalize();
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, jsonMapper.writeValueAsString(report));
        System.out.println("Machine report: " + path);
    }

    private List<Scenario> scenarios(Fixture fixture) {
        return List.of(
                new Scenario(
                        "CREATE_CLASS",
                        List.of(
                                successCase("clean", "Crea una clase HistorialClinico", expectCreateClass("HistorialClinico")),
                                successCase("verb-typo", "Crea una clse HistorialClinico", expectCreateClass("HistorialClinico")),
                                successCase("conversational", "Por favor creame una nueva clase llamada HistorialClinico", expectCreateClass("HistorialClinico")),
                                successCase("preserve-new-name", "Crea la clase HistorialClinco", expectCreateClass("HistorialClinco")),
                                successCase("short", "nueva clase HistorialClinico", expectCreateClass("HistorialClinico"))
                        )
                ),
                new Scenario(
                        "RENAME_CLASS",
                        List.of(
                                successCase("clean", "Renombra Veterinario a MedicoVeterinario", expectRename(fixture, "Veterinario", "MedicoVeterinario")),
                                successCase("transposition", "Renombra Veterinairo a MedicoVeterinario", expectRename(fixture, "Veterinario", "MedicoVeterinario")),
                                successCase("missing-letter", "Cambia veternario por MedicoVeterinario", expectRename(fixture, "Veterinario", "MedicoVeterinario")),
                                successCase("case", "A VETERINARIO cambiale el nombre a MedicoVeterinario", expectRename(fixture, "Veterinario", "MedicoVeterinario")),
                                successCase("accent-noise", "renonbra Veterinário a MedicoVeterinario", expectRename(fixture, "Veterinario", "MedicoVeterinario"))
                        )
                ),
                new Scenario(
                        "DELETE_CLASS",
                        List.of(
                                successCase("clean", "Elimina la clase Tratamiento", expectDeleteClass(fixture, "Tratamiento")),
                                successCase("missing-letter", "Borra Trtamiento", expectDeleteClass(fixture, "Tratamiento")),
                                successCase("transposition", "Elimina Tratamietno", expectDeleteClass(fixture, "Tratamiento")),
                                successCase("case", "borra TRATAMIENTO del modelo", expectDeleteClass(fixture, "Tratamiento")),
                                successCase("conversational", "Ya no necesito la clase tratmiento, eliminála", expectDeleteClass(fixture, "Tratamiento"))
                        )
                ),
                new Scenario(
                        "ADD_ATTRIBUTES",
                        List.of(
                                successCase("clean", "Agrega el atributo telefono STRING a Propietario", expectAddAttribute(fixture, "Propietario", "telefono", UmlDataType.STRING)),
                                successCase("class-typo", "A Propetario agregale telefono de tipo STRING", expectAddAttribute(fixture, "Propietario", "telefono", UmlDataType.STRING)),
                                successCase("class-transposition", "Añade a propietairo el atributo telefono string", expectAddAttribute(fixture, "Propietario", "telefono", UmlDataType.STRING)),
                                successCase("natural-type", "En propietario crea el campo telefono como texto", expectAddAttribute(fixture, "Propietario", "telefono", UmlDataType.STRING)),
                                successCase("implicit-type", "Ponle un telefono a Propietario", expectAddAttribute(fixture, "Propietario", "telefono", UmlDataType.STRING))
                        )
                ),
                new Scenario(
                        "UPDATE_ATTRIBUTE",
                        List.of(
                                successCase("clean", "En Mascota renombra el atributo peso a pesoKg", expectUpdateAttribute(fixture, "Mascota", "peso", "pesoKg")),
                                successCase("class-typo", "En Masctoa cambia peso por pesoKg", expectUpdateAttribute(fixture, "Mascota", "peso", "pesoKg")),
                                successCase("attribute-typo", "En Mascota cambia pseo a pesoKg", expectUpdateAttribute(fixture, "Mascota", "peso", "pesoKg")),
                                successCase("double-typo", "renonbra el atributo pseo de msacota a pesoKg", expectUpdateAttribute(fixture, "Mascota", "peso", "pesoKg")),
                                successCase("conversational", "Para la clase mascota el campo peso ahora se llama pesoKg", expectUpdateAttribute(fixture, "Mascota", "peso", "pesoKg"))
                        )
                ),
                new Scenario(
                        "DELETE_ATTRIBUTE",
                        List.of(
                                successCase("clean", "Elimina el atributo motivo de Consulta", expectDeleteAttribute(fixture, "Consulta", "motivo")),
                                successCase("class-typo", "Borra motivo de Consutla", expectDeleteAttribute(fixture, "Consulta", "motivo")),
                                successCase("attribute-typo", "Elimina motvio de Consulta", expectDeleteAttribute(fixture, "Consulta", "motivo")),
                                successCase("double-typo", "Quita motvio de Consutla", expectDeleteAttribute(fixture, "Consulta", "motivo")),
                                successCase("conversational", "La consulta ya no necesita el campo motivo, sacalo", expectDeleteAttribute(fixture, "Consulta", "motivo"))
                        )
                ),
                new Scenario(
                        "CREATE_RELATIONSHIP",
                        List.of(
                                successCase("association-typos", "Crea una asociacion entre 4nimal y veternaria", expectCreateRelationship(fixture, "Animal", "Veterinaria", UmlRelationshipType.ASSOCIATION)),
                                successCase("composition-typos", "Veternaria esta compuesta por Factrua", expectCreateRelationship(fixture, "Veterinaria", "Factura", UmlRelationshipType.COMPOSITION)),
                                successCase("generalization", "AnimalDomestcio hereda de 4nimal", expectCreateRelationship(fixture, "AnimalDomestico", "Animal", UmlRelationshipType.GENERALIZATION)),
                                successCase("aggregation-natural", "Veterinaria agrupa Vacuna", expectCreateRelationship(fixture, "Veterinaria", "Vacuna", UmlRelationshipType.AGGREGATION)),
                                successCase("association-noise", "Porfa conecta Propietairo con Veternaria mediante una asociacion", expectCreateRelationship(fixture, "Propietario", "Veterinaria", UmlRelationshipType.ASSOCIATION))
                        )
                ),
                new Scenario(
                        "UPDATE_RELATIONSHIP",
                        List.of(
                                successCase("clean", "En la relacion entre Propietario y Mascota cambia la multiplicidad de Mascota a 0..*", expectUpdateRelationshipMany(fixture)),
                                successCase("source-typo", "En la relacion Propetario Mascota deja muchas mascotas del lado de Mascota", expectUpdateRelationshipMany(fixture)),
                                successCase("target-typo", "Un Propietario puede tener muchas Masctoas; actualiza esa relacion", expectUpdateRelationshipMany(fixture)),
                                successCase("double-typo", "cambia la relacion propetario msacota para que el lado mascota sea 0..*", expectUpdateRelationshipMany(fixture)),
                                successCase("natural", "Haz que un propietario pueda relacionarse con cero o muchas mascotas en la relacion existente", expectUpdateRelationshipMany(fixture))
                        )
                ),
                new Scenario(
                        "DELETE_RELATIONSHIP",
                        List.of(
                                successCase("clean", "Elimina la relacion entre Mascota y Vacuna", expectDeleteRelationship(fixture, "Mascota->Vacuna")),
                                successCase("source-typo", "Borra la relacion Masctoa Vacuna", expectDeleteRelationship(fixture, "Mascota->Vacuna")),
                                successCase("target-typo", "Quita la relacion entre Mascota y Vcauna", expectDeleteRelationship(fixture, "Mascota->Vacuna")),
                                successCase("double-typo", "elimina la relacion msacota vcauna", expectDeleteRelationship(fixture, "Mascota->Vacuna")),
                                successCase("conversational", "Ya no quiero que mascota y vacuna esten relacionadas, elimina esa relacion", expectDeleteRelationship(fixture, "Mascota->Vacuna"))
                        )
                ),
                new Scenario(
                        "SAFETY_UNKNOWN_REFERENCE",
                        List.of(
                                rejectionCase("unknown-class", "Relaciona Mascota con FantasmaQueNoExiste"),
                                rejectionCase("unknown-delete", "Elimina la clase PacienteeQueNoExiste"),
                                rejectionCase("unknown-rename", "Renombra VeternarioFantasma a Medico"),
                                rejectionCase("unknown-attribute", "Elimina codigoSecreto de Veterinaria"),
                                rejectionCase("two-unknowns", "Crea una asociacion entre FooInexistente y BarInexistente")
                        )
                )
        );
    }

    private PromptCase successCase(
            String label,
            String prompt,
            OutcomeEvaluator evaluator
    ) {
        return new PromptCase(label, prompt, evaluator);
    }

    private PromptCase rejectionCase(
            String label,
            String prompt
    ) {
        return new PromptCase(
                label,
                prompt,
                outcome -> {
                    if (outcome.transportError() != null) {
                        return Evaluation.failure("TRANSPORT", outcome.transportError());
                    }

                    if (outcome.statusCode() >= 200 && outcome.statusCode() < 300) {
                        return Evaluation.failure(
                                "UNSAFE_ACCEPT",
                                "El endpoint acepto una referencia que no existe en el ProjectDocument"
                        );
                    }

                    if (outcome.statusCode() != 503) {
                        return Evaluation.failure(
                                "WRONG_HTTP_STATUS",
                                "Se esperaba 503 de planning y llego " + outcome.statusCode()
                        );
                    }

                    if ("LLM".equalsIgnoreCase(outcome.stage())) {
                        return Evaluation.failure(
                                "LLM_INFRASTRUCTURE",
                                "La peticion fallo antes de llegar a la proteccion semantica: " + outcome.message()
                        );
                    }

                    return Evaluation.ok();
                }
        );
    }

    private OutcomeEvaluator expectCreateClass(String expectedName) {
        return outcome -> withResponse(outcome, response -> {
            AssistantPlanAction action = firstAction(response, AssistantActionType.CREATE_CLASS);

            if (action == null) {
                return Evaluation.failure("WRONG_ACTION", "No existe CREATE_CLASS en el plan final");
            }

            if (!expectedName.equals(action.className())) {
                return Evaluation.failure(
                        "WRONG_CLASS_NAME",
                        "Esperado=" + expectedName + " actual=" + action.className()
                );
            }

            UmlCommandPayload command = firstCommand(response, UmlCommandType.CREATE_CLASS);

            if (command == null || command.umlClass() == null) {
                return Evaluation.failure("WRONG_COMMAND", "No existe comando CREATE_CLASS resuelto");
            }

            if (!expectedName.equals(command.umlClass().name())) {
                return Evaluation.failure(
                        "WRONG_COMMAND_NAME",
                        "El comando final creo " + command.umlClass().name()
                );
            }

            return Evaluation.ok();
        });
    }

    private OutcomeEvaluator expectRename(
            Fixture fixture,
            String canonicalClass,
            String newName
    ) {
        return outcome -> withResponse(outcome, response -> {
            AssistantPlanAction action = firstAction(response, AssistantActionType.RENAME_CLASS);

            if (action == null) {
                return Evaluation.failure("WRONG_ACTION", "No existe RENAME_CLASS; plan=" + response.plan());
            }

            if (!canonicalClass.equals(action.className())) {
                return Evaluation.failure("REFERENCE_NOT_CANONICAL", "className=" + action.className());
            }

            if (!newName.equals(action.newName())) {
                return Evaluation.failure("WRONG_NEW_NAME", "newName=" + action.newName());
            }

            UmlCommandPayload command = firstCommand(response, UmlCommandType.RENAME_CLASS);

            if (command == null
                    || !fixture.classId(canonicalClass).equals(command.classId())
                    || !newName.equals(command.name())) {
                return Evaluation.failure("WRONG_COMMAND", "RENAME_CLASS no resolvio al UUID/nombre esperado");
            }

            return Evaluation.ok();
        });
    }

    private OutcomeEvaluator expectDeleteClass(
            Fixture fixture,
            String canonicalClass
    ) {
        return outcome -> withResponse(outcome, response -> {
            AssistantPlanAction action = firstAction(response, AssistantActionType.DELETE_CLASS);

            if (action == null) {
                return Evaluation.failure("WRONG_ACTION", "No existe DELETE_CLASS");
            }

            if (!canonicalClass.equals(action.className())) {
                return Evaluation.failure("REFERENCE_NOT_CANONICAL", "className=" + action.className());
            }

            UmlCommandPayload command = firstCommand(response, UmlCommandType.DELETE_CLASS);

            if (command == null || !fixture.classId(canonicalClass).equals(command.classId())) {
                return Evaluation.failure("WRONG_COMMAND", "DELETE_CLASS no apunto al UUID esperado");
            }

            return Evaluation.ok();
        });
    }

    private OutcomeEvaluator expectAddAttribute(
            Fixture fixture,
            String canonicalClass,
            String attributeName,
            UmlDataType dataType
    ) {
        return outcome -> withResponse(outcome, response -> {
            AssistantPlanAction action = firstAction(response, AssistantActionType.ADD_ATTRIBUTES);

            if (action == null) {
                return Evaluation.failure("WRONG_ACTION", "No existe ADD_ATTRIBUTES");
            }

            if (!canonicalClass.equals(action.className())) {
                return Evaluation.failure("REFERENCE_NOT_CANONICAL", "className=" + action.className());
            }

            boolean planContainsAttribute = action.safeAttributes().stream().anyMatch(
                    attribute -> attributeName.equals(attribute.name()) && dataType == attribute.dataType()
            );

            if (!planContainsAttribute) {
                return Evaluation.failure("WRONG_ATTRIBUTE", "El plan no contiene " + attributeName + " " + dataType);
            }

            UmlCommandPayload command = firstCommand(response, UmlCommandType.ADD_ATTRIBUTE);

            if (command == null
                    || !fixture.classId(canonicalClass).equals(command.classId())
                    || command.attribute() == null
                    || !attributeName.equals(command.attribute().name())
                    || dataType != command.attribute().dataType()) {
                return Evaluation.failure("WRONG_COMMAND", "ADD_ATTRIBUTE no coincide con clase/nombre/tipo esperado");
            }

            return Evaluation.ok();
        });
    }

    private OutcomeEvaluator expectUpdateAttribute(
            Fixture fixture,
            String canonicalClass,
            String oldName,
            String newName
    ) {
        return outcome -> withResponse(outcome, response -> {
            AssistantPlanAction action = firstAction(response, AssistantActionType.UPDATE_ATTRIBUTE);

            if (action == null) {
                return Evaluation.failure("WRONG_ACTION", "No existe UPDATE_ATTRIBUTE; plan=" + response.plan());
            }

            if (!canonicalClass.equals(action.className())) {
                return Evaluation.failure("REFERENCE_NOT_CANONICAL", "className=" + action.className());
            }

            if (!oldName.equals(action.attributeName())) {
                return Evaluation.failure("ATTRIBUTE_NOT_RESOLVED", "attributeName=" + action.attributeName());
            }

            if (!newName.equals(action.newAttributeName())) {
                return Evaluation.failure("WRONG_NEW_ATTRIBUTE", "newAttributeName=" + action.newAttributeName());
            }

            UmlCommandPayload command = firstCommand(response, UmlCommandType.UPDATE_ATTRIBUTE);

            if (command == null
                    || !fixture.classId(canonicalClass).equals(command.classId())
                    || command.attribute() == null
                    || !fixture.attributeId(canonicalClass, oldName).equals(command.attribute().id())
                    || !newName.equals(command.attribute().name())) {
                return Evaluation.failure("WRONG_COMMAND", "UPDATE_ATTRIBUTE no resolvio atributo/UUID esperado");
            }

            return Evaluation.ok();
        });
    }

    private OutcomeEvaluator expectDeleteAttribute(
            Fixture fixture,
            String canonicalClass,
            String attributeName
    ) {
        return outcome -> withResponse(outcome, response -> {
            AssistantPlanAction action = firstAction(response, AssistantActionType.DELETE_ATTRIBUTE);

            if (action == null) {
                return Evaluation.failure("WRONG_ACTION", "No existe DELETE_ATTRIBUTE");
            }

            if (!canonicalClass.equals(action.className())) {
                return Evaluation.failure("REFERENCE_NOT_CANONICAL", "className=" + action.className());
            }

            if (!attributeName.equals(action.attributeName())) {
                return Evaluation.failure("ATTRIBUTE_NOT_RESOLVED", "attributeName=" + action.attributeName());
            }

            UmlCommandPayload command = firstCommand(response, UmlCommandType.DELETE_ATTRIBUTE);

            if (command == null
                    || !fixture.classId(canonicalClass).equals(command.classId())
                    || !fixture.attributeId(canonicalClass, attributeName).equals(command.attributeId())) {
                return Evaluation.failure("WRONG_COMMAND", "DELETE_ATTRIBUTE no resolvio atributo/UUID esperado");
            }

            return Evaluation.ok();
        });
    }

    private OutcomeEvaluator expectCreateRelationship(
            Fixture fixture,
            String source,
            String target,
            UmlRelationshipType type
    ) {
        return outcome -> withResponse(outcome, response -> {
            AssistantPlanAction action = firstAction(response, AssistantActionType.CREATE_RELATIONSHIP);

            if (action == null) {
                return Evaluation.failure("WRONG_ACTION", "No existe CREATE_RELATIONSHIP");
            }

            Evaluation endpoints = evaluateRelationshipAction(action, source, target, type);

            if (!endpoints.success()) {
                return endpoints;
            }

            UmlCommandPayload command = firstCommand(response, UmlCommandType.CREATE_RELATIONSHIP);

            if (command == null || command.relationship() == null) {
                return Evaluation.failure("WRONG_COMMAND", "No existe relacion resuelta en el BATCH");
            }

            if (!fixture.classId(source).equals(command.relationship().sourceClassId())
                    || !fixture.classId(target).equals(command.relationship().targetClassId())
                    || type != command.relationship().type()) {
                return Evaluation.failure("WRONG_COMMAND_ENDPOINTS", "La relacion final no usa los UUID/tipo esperados");
            }

            return Evaluation.ok();
        });
    }

    private OutcomeEvaluator expectUpdateRelationshipMany(Fixture fixture) {
        return outcome -> withResponse(outcome, response -> {
            AssistantPlanAction action = firstAction(response, AssistantActionType.UPDATE_RELATIONSHIP);

            if (action == null) {
                return Evaluation.failure("WRONG_ACTION", "No existe UPDATE_RELATIONSHIP; plan=" + response.plan());
            }

            Evaluation endpoints = evaluateRelationshipAction(
                    action,
                    "Propietario",
                    "Mascota",
                    null
            );

            if (!endpoints.success()) {
                return endpoints;
            }

            UmlCommandPayload command = firstCommand(response, UmlCommandType.UPDATE_RELATIONSHIP);

            if (command == null || command.relationship() == null) {
                return Evaluation.failure("WRONG_COMMAND", "No existe UPDATE_RELATIONSHIP resuelto");
            }

            UmlRelationship relationship = command.relationship();

            if (!fixture.relationshipId("Propietario->Mascota").equals(relationship.id())) {
                return Evaluation.failure("WRONG_RELATIONSHIP_ID", "No actualizo la relacion persistida esperada");
            }

            if (relationship.targetMultiplicity() == null
                    || relationship.targetMultiplicity().upper() != null
                    || relationship.targetMultiplicity().lower() != 0) {
                return Evaluation.failure(
                        "WRONG_MULTIPLICITY",
                        "Esperado lado Mascota 0..*, actual=" + relationship.targetMultiplicity()
                );
            }

            return Evaluation.ok();
        });
    }

    private OutcomeEvaluator expectDeleteRelationship(
            Fixture fixture,
            String relationshipKey
    ) {
        return outcome -> withResponse(outcome, response -> {
            AssistantPlanAction action = firstAction(response, AssistantActionType.DELETE_RELATIONSHIP);

            if (action == null) {
                return Evaluation.failure("WRONG_ACTION", "No existe DELETE_RELATIONSHIP");
            }

            UmlCommandPayload command = firstCommand(response, UmlCommandType.DELETE_RELATIONSHIP);

            if (command == null || !fixture.relationshipId(relationshipKey).equals(command.relationshipId())) {
                return Evaluation.failure("WRONG_COMMAND", "DELETE_RELATIONSHIP no resolvio el relationshipId esperado");
            }

            return Evaluation.ok();
        });
    }

    private Evaluation evaluateRelationshipAction(
            AssistantPlanAction action,
            String source,
            String target,
            UmlRelationshipType expectedType
    ) {
        if (!source.equals(action.sourceClassName())) {
            return Evaluation.failure("SOURCE_NOT_CANONICAL", "sourceClassName=" + action.sourceClassName());
        }

        if (!target.equals(action.targetClassName())) {
            return Evaluation.failure("TARGET_NOT_CANONICAL", "targetClassName=" + action.targetClassName());
        }

        if (expectedType != null && expectedType != action.relationshipType()) {
            return Evaluation.failure("WRONG_RELATIONSHIP_TYPE", "relationshipType=" + action.relationshipType());
        }

        return Evaluation.ok();
    }

    private Evaluation withResponse(
            HttpOutcome outcome,
            ResponseEvaluator evaluator
    ) {
        if (outcome.transportError() != null) {
            return Evaluation.failure("TRANSPORT", outcome.transportError());
        }

        if (outcome.statusCode() < 200 || outcome.statusCode() >= 300) {
            String reason = outcome.stage() == null
                    ? "HTTP_" + outcome.statusCode()
                    : "HTTP_" + outcome.statusCode() + "_" + outcome.stage();

            return Evaluation.failure(reason, outcome.message());
        }

        if (outcome.response() == null) {
            return Evaluation.failure("EMPTY_RESPONSE", "HTTP exitoso sin AssistantPlanResponse");
        }

        return evaluator.evaluate(outcome.response());
    }

    private AssistantPlanAction firstAction(
            AssistantPlanResponse response,
            AssistantActionType type
    ) {
        if (response.plan() == null || response.plan().actions() == null) {
            return null;
        }

        return response.plan().actions().stream()
                .filter(action -> action != null && action.type() == type)
                .findFirst()
                .orElse(null);
    }

    private UmlCommandPayload firstCommand(
            AssistantPlanResponse response,
            UmlCommandType type
    ) {
        if (response.command() == null) {
            return null;
        }

        if (response.command().type() == type) {
            return response.command();
        }

        return response.command().safeCommands().stream()
                .filter(command -> command != null && command.type() == type)
                .findFirst()
                .orElse(null);
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    @FunctionalInterface
    private interface OutcomeEvaluator {
        Evaluation evaluate(HttpOutcome outcome) throws Exception;
    }

    @FunctionalInterface
    private interface ResponseEvaluator {
        Evaluation evaluate(AssistantPlanResponse response);
    }

    private record PromptCase(
            String label,
            String prompt,
            OutcomeEvaluator evaluator
    ) {
    }

    private record Scenario(
            String category,
            List<PromptCase> cases
    ) {
    }

    private record Evaluation(
            boolean success,
            String reason,
            String detail
    ) {
        static Evaluation ok() {
            return new Evaluation(true, "OK", "");
        }

        static Evaluation failure(String reason, String detail) {
            return new Evaluation(false, reason, detail == null ? "" : detail);
        }
    }

    private record FailureExample(
            int attempt,
            String variant,
            String prompt,
            int httpStatus,
            String stage,
            String reason,
            String detail
    ) {
    }

    private enum BenchmarkStatus {
        OK,
        WARN,
        ERROR
    }

    private record CategoryResult(
            String category,
            int passed,
            int total,
            long totalNanos,
            Map<String, Integer> failureReasons,
            List<FailureExample> failures
    ) {
        double approvalPercent() {
            return total == 0 ? 0.0d : passed * 100.0d / total;
        }

        double failurePercent() {
            return 100.0d - approvalPercent();
        }

        double averageMillis() {
            return total == 0 ? 0.0d : totalNanos / 1_000_000.0d / total;
        }

        BenchmarkStatus status(BenchmarkConfig config) {
            if ("SAFETY_UNKNOWN_REFERENCE".equals(category) && passed < total) {
                return BenchmarkStatus.ERROR;
            }

            if (failurePercent() > config.errorFailurePercent()) {
                return BenchmarkStatus.ERROR;
            }

            if (passed < total) {
                return BenchmarkStatus.WARN;
            }

            return BenchmarkStatus.OK;
        }
    }

    private record BenchmarkConfig(
            int attemptsPerCategory,
            double errorFailurePercent,
            boolean verboseAttempts
    ) {
        static BenchmarkConfig fromSystemProperties() {
            int attempts = Integer.parseInt(
                    System.getProperty("assistant.benchmark.attempts", "5")
            );

            double errorThreshold = Double.parseDouble(
                    System.getProperty("assistant.benchmark.errorFailurePercent", "60")
            );

            boolean verbose = Boolean.parseBoolean(
                    System.getProperty("assistant.benchmark.verbose", "false")
            );

            if (attempts < 1) {
                throw new IllegalArgumentException("assistant.benchmark.attempts debe ser >= 1");
            }

            if (errorThreshold < 0.0d || errorThreshold > 100.0d) {
                throw new IllegalArgumentException("assistant.benchmark.errorFailurePercent debe estar entre 0 y 100");
            }

            return new BenchmarkConfig(attempts, errorThreshold, verbose);
        }
    }

    private static final class BenchmarkClient {
        private final HttpClient httpClient;
        private final JsonMapper jsonMapper;
        private final String baseUrl;
        private final UUID projectId;
        private final String token;

        private BenchmarkClient(
                HttpClient httpClient,
                JsonMapper jsonMapper,
                String baseUrl,
                UUID projectId,
                String token
        ) {
            this.httpClient = httpClient;
            this.jsonMapper = jsonMapper;
            this.baseUrl = baseUrl;
            this.projectId = projectId;
            this.token = token;
        }

        AssistantRuntimeHealthResponse health() throws Exception {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(baseUrl + "/api/projects/" + projectId + "/assistant/health")
                    )
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Health endpoint HTTP " + response.statusCode() + ": " + response.body()
                );
            }

            return jsonMapper.readValue(response.body(), AssistantRuntimeHealthResponse.class);
        }

        HttpOutcome plan(String prompt) throws Exception {
            String body = jsonMapper.writeValueAsString(new AssistantPlanRequest(prompt));

            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(baseUrl + "/api/projects/" + projectId + "/assistant/plan")
                    )
                    .timeout(HTTP_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return new HttpOutcome(
                        response.statusCode(),
                        jsonMapper.readValue(response.body(), AssistantPlanResponse.class),
                        null,
                        null,
                        null
                );
            }

            JsonNode error;

            try {
                error = jsonMapper.readTree(response.body());
            } catch (Exception ignored) {
                error = null;
            }

            return new HttpOutcome(
                    response.statusCode(),
                    null,
                    error == null || error.get("stage") == null
                            ? null
                            : error.get("stage").asString(),
                    error == null
                            ? response.body()
                            : error.toPrettyString(),
                    null
            );
        }
    }

    private record HttpOutcome(
            int statusCode,
            AssistantPlanResponse response,
            String stage,
            String message,
            String transportError
    ) {
        static HttpOutcome transportFailure(Exception exception) {
            return new HttpOutcome(
                    0,
                    null,
                    null,
                    null,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage()
            );
        }
    }

    private record Fixture(
            ProjectDocument document,
            Map<String, UUID> classIds,
            Map<String, UUID> attributeIds,
            Map<String, UUID> relationshipIds
    ) {
        UUID classId(String name) {
            return required(classIds, name);
        }

        UUID attributeId(String className, String attributeName) {
            return required(attributeIds, className + "." + attributeName);
        }

        UUID relationshipId(String key) {
            return required(relationshipIds, key);
        }

        private UUID required(Map<String, UUID> values, String key) {
            UUID value = values.get(key);

            if (value == null) {
                throw new IllegalArgumentException("Fixture key inexistente: " + key);
            }

            return value;
        }

        static Fixture mediumVeterinaryProject() {
            Map<String, UUID> classIds = new LinkedHashMap<>();
            Map<String, UUID> attributeIds = new LinkedHashMap<>();
            Map<String, UUID> relationshipIds = new LinkedHashMap<>();
            List<UmlClass> classes = new ArrayList<>();

            classes.add(umlClass(classIds, attributeIds, "Animal",
                    attribute(attributeIds, "Animal", "id", UmlDataType.LONG, false, true),
                    attribute(attributeIds, "Animal", "nombre", UmlDataType.STRING, false, false),
                    attribute(attributeIds, "Animal", "especie", UmlDataType.STRING, true, false)));

            classes.add(umlClass(classIds, attributeIds, "Mascota",
                    attribute(attributeIds, "Mascota", "id", UmlDataType.UUID, false, true),
                    attribute(attributeIds, "Mascota", "nombre", UmlDataType.STRING, false, false),
                    attribute(attributeIds, "Mascota", "fechaNacimiento", UmlDataType.DATE, true, false),
                    attribute(attributeIds, "Mascota", "peso", UmlDataType.DECIMAL, true, false)));

            classes.add(umlClass(classIds, attributeIds, "AnimalDomestico",
                    attribute(attributeIds, "AnimalDomestico", "id", UmlDataType.UUID, false, true)));

            classes.add(umlClass(classIds, attributeIds, "AnimalSalvaje",
                    attribute(attributeIds, "AnimalSalvaje", "id", UmlDataType.UUID, false, true)));

            classes.add(umlClass(classIds, attributeIds, "Propietario",
                    attribute(attributeIds, "Propietario", "id", UmlDataType.UUID, false, true),
                    attribute(attributeIds, "Propietario", "nombre", UmlDataType.STRING, false, false),
                    attribute(attributeIds, "Propietario", "email", UmlDataType.STRING, false, false)));

            classes.add(umlClass(classIds, attributeIds, "Veterinaria",
                    attribute(attributeIds, "Veterinaria", "id", UmlDataType.UUID, false, true),
                    attribute(attributeIds, "Veterinaria", "nombre", UmlDataType.STRING, false, false),
                    attribute(attributeIds, "Veterinaria", "direccion", UmlDataType.STRING, true, false)));

            classes.add(umlClass(classIds, attributeIds, "Veterinario",
                    attribute(attributeIds, "Veterinario", "id", UmlDataType.UUID, false, true),
                    attribute(attributeIds, "Veterinario", "nombre", UmlDataType.STRING, false, false),
                    attribute(attributeIds, "Veterinario", "matricula", UmlDataType.STRING, false, false)));

            classes.add(umlClass(classIds, attributeIds, "Consulta",
                    attribute(attributeIds, "Consulta", "id", UmlDataType.UUID, false, true),
                    attribute(attributeIds, "Consulta", "fecha", UmlDataType.DATETIME, false, false),
                    attribute(attributeIds, "Consulta", "motivo", UmlDataType.STRING, true, false)));

            classes.add(umlClass(classIds, attributeIds, "Tratamiento",
                    attribute(attributeIds, "Tratamiento", "id", UmlDataType.UUID, false, true),
                    attribute(attributeIds, "Tratamiento", "descripcion", UmlDataType.STRING, false, false),
                    attribute(attributeIds, "Tratamiento", "activo", UmlDataType.BOOLEAN, false, false)));

            classes.add(umlClass(classIds, attributeIds, "Vacuna",
                    attribute(attributeIds, "Vacuna", "id", UmlDataType.UUID, false, true),
                    attribute(attributeIds, "Vacuna", "nombre", UmlDataType.STRING, false, false),
                    attribute(attributeIds, "Vacuna", "dosis", UmlDataType.INTEGER, true, false)));

            classes.add(umlClass(classIds, attributeIds, "Factura",
                    attribute(attributeIds, "Factura", "id", UmlDataType.UUID, false, true),
                    attribute(attributeIds, "Factura", "total", UmlDataType.DECIMAL, false, false),
                    attribute(attributeIds, "Factura", "pagada", UmlDataType.BOOLEAN, false, false)));

            classes.add(umlClass(classIds, attributeIds, "Direccion",
                    attribute(attributeIds, "Direccion", "id", UmlDataType.UUID, false, true),
                    attribute(attributeIds, "Direccion", "calle", UmlDataType.STRING, false, false),
                    attribute(attributeIds, "Direccion", "ciudad", UmlDataType.STRING, false, false)));

            List<UmlRelationship> relationships = new ArrayList<>();

            relationships.add(relationship(relationshipIds, "Mascota->Animal", classIds, "Mascota", "Animal", UmlRelationshipType.GENERALIZATION, null, null));
            relationships.add(relationship(relationshipIds, "Propietario->Mascota", classIds, "Propietario", "Mascota", UmlRelationshipType.ASSOCIATION, Multiplicity.one(), Multiplicity.one()));
            relationships.add(relationship(relationshipIds, "Veterinaria->Veterinario", classIds, "Veterinaria", "Veterinario", UmlRelationshipType.AGGREGATION, Multiplicity.one(), Multiplicity.many()));
            relationships.add(relationship(relationshipIds, "Consulta->Mascota", classIds, "Consulta", "Mascota", UmlRelationshipType.ASSOCIATION, Multiplicity.many(), Multiplicity.one()));
            relationships.add(relationship(relationshipIds, "Consulta->Veterinario", classIds, "Consulta", "Veterinario", UmlRelationshipType.ASSOCIATION, Multiplicity.many(), Multiplicity.one()));
            relationships.add(relationship(relationshipIds, "Consulta->Tratamiento", classIds, "Consulta", "Tratamiento", UmlRelationshipType.COMPOSITION, Multiplicity.one(), Multiplicity.many()));
            relationships.add(relationship(relationshipIds, "Mascota->Vacuna", classIds, "Mascota", "Vacuna", UmlRelationshipType.ASSOCIATION, Multiplicity.many(), Multiplicity.many()));
            relationships.add(relationship(relationshipIds, "Propietario->Direccion", classIds, "Propietario", "Direccion", UmlRelationshipType.COMPOSITION, Multiplicity.one(), Multiplicity.one()));
            relationships.add(relationship(relationshipIds, "Consulta->Factura", classIds, "Consulta", "Factura", UmlRelationshipType.ASSOCIATION, Multiplicity.one(), Multiplicity.one()));

            Map<UUID, DiagramNodeLayout> nodes = new LinkedHashMap<>();

            for (int index = 0; index < classes.size(); index++) {
                nodes.put(classes.get(index).id(), DiagramNodeLayout.defaultForIndex(index));
            }

            return new Fixture(
                    new ProjectDocument(
                            ProjectDocument.CURRENT_SCHEMA_VERSION,
                            new UmlModel(classes, relationships),
                            new DiagramLayout(nodes)
                    ),
                    Map.copyOf(classIds),
                    Map.copyOf(attributeIds),
                    Map.copyOf(relationshipIds)
            );
        }

        private static UmlClass umlClass(
                Map<String, UUID> classIds,
                Map<String, UUID> attributeIds,
                String name,
                UmlAttribute... attributes
        ) {
            UUID id = UUID.randomUUID();
            classIds.put(name, id);
            return new UmlClass(id, name, List.of(attributes));
        }

        private static UmlAttribute attribute(
                Map<String, UUID> attributeIds,
                String className,
                String name,
                UmlDataType dataType,
                boolean nullable,
                boolean identifier
        ) {
            UUID id = UUID.randomUUID();
            attributeIds.put(className + "." + name, id);
            return new UmlAttribute(
                    id,
                    name,
                    dataType,
                    null,
                    UmlVisibility.PRIVATE,
                    nullable,
                    identifier
            );
        }

        private static UmlRelationship relationship(
                Map<String, UUID> relationshipIds,
                String key,
                Map<String, UUID> classIds,
                String source,
                String target,
                UmlRelationshipType type,
                Multiplicity sourceMultiplicity,
                Multiplicity targetMultiplicity
        ) {
            UUID id = UUID.randomUUID();
            relationshipIds.put(key, id);
            return new UmlRelationship(
                    id,
                    classIds.get(source),
                    classIds.get(target),
                    type,
                    sourceMultiplicity,
                    targetMultiplicity
            );
        }
    }
}
