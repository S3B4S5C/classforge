package com.classforge.assistant;

import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationshipType;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AssistantReliabilityCli {

    private AssistantReliabilityCli() {
    }

    public static void main(
            String[] args
    ) {
        Config config =
                Config.from(
                        args
                );

        ProjectDocument document =
                documentWith(
                        config.classes()
                );

        LlamaLanguageModelGateway gateway =
                new LlamaLanguageModelGateway(
                        JsonMapper.builder()
                                .build(),
                        config.llamaUrl(),
                        config.model()
                );

        AssistantEntityReferenceResolver entityResolver =
                new AssistantEntityReferenceResolver();

        AssistantSemanticCompiler compiler =
                new AssistantSemanticCompiler(
                        entityResolver
                );

        AssistantPlanGroundingFilter grounding =
                new AssistantPlanGroundingFilter(
                        entityResolver
                );

        AssistantPlanNormalizer normalizer =
                new AssistantPlanNormalizer();

        int rawSuccess = 0;
        int finalSuccess = 0;
        int repaired = 0;

        Map<String, Integer> failures =
                new LinkedHashMap<>();

        long startedAt =
                System.nanoTime();

        System.out.println();
        System.out.println("ClassForge Assistant reliability benchmark");
        System.out.println("------------------------------------------");
        System.out.println("Attempts: " + config.attempts());
        System.out.println("Prompt:   " + config.prompt());
        System.out.println("Classes:  " + String.join(", ", config.classes()));
        System.out.println(
                "Expected: "
                        + config.expectedType()
                        + " "
                        + config.expectedSource()
                        + " -> "
                        + config.expectedTarget()
        );
        System.out.println("llama:    " + config.llamaUrl());
        System.out.println();

        for (
                int attempt = 1;
                attempt <= config.attempts();
                attempt++
        ) {
            try {
                AssistantSemanticPlan raw =
                        gateway.plan(
                                config.prompt(),
                                document
                        );

                Evaluation rawEvaluation =
                        evaluate(
                                raw,
                                config
                        );

                if (rawEvaluation.success()) {
                    rawSuccess++;
                }

                AssistantSemanticPlan compiled =
                        compiler.compile(
                                config.prompt(),
                                raw,
                                document
                        );

                AssistantSemanticPlan grounded =
                        grounding.sanitize(
                                config.prompt(),
                                compiled,
                                document
                        );

                AssistantSemanticPlan normalized =
                        normalizer.normalize(
                                grounded
                        );

                Evaluation finalEvaluation =
                        evaluate(
                                normalized,
                                config
                        );

                if (finalEvaluation.success()) {
                    finalSuccess++;

                    if (!rawEvaluation.success()) {
                        repaired++;
                    }
                } else {
                    increment(
                            failures,
                            "FINAL_"
                                    + finalEvaluation.reason()
                    );
                }

                if (
                        config.verbose()
                                || !finalEvaluation.success()
                ) {
                    System.out.printf(
                            Locale.ROOT,
                            "[%03d] raw=%-5s final=%-5s rawReason=%s finalReason=%s%n",
                            attempt,
                            rawEvaluation.success()
                                    ? "OK"
                                    : "FAIL",
                            finalEvaluation.success()
                                    ? "OK"
                                    : "FAIL",
                            rawEvaluation.reason(),
                            finalEvaluation.reason()
                    );

                    if (config.verbose()) {
                        System.out.println("      raw:      " + raw);
                        System.out.println("      compiled: " + compiled);
                    }
                }

                if (!rawEvaluation.success()) {
                    increment(
                            failures,
                            "RAW_"
                                    + rawEvaluation.reason()
                    );
                }
            } catch (
                    AssistantPlanningException exception
            ) {
                increment(
                        failures,
                        "PIPELINE_EXCEPTION"
                );

                System.out.printf(
                        Locale.ROOT,
                        "[%03d] EXCEPTION %s%n",
                        attempt,
                        exception.getMessage()
                );
            } catch (Exception exception) {
                increment(
                        failures,
                        "UNEXPECTED_EXCEPTION"
                );

                System.out.printf(
                        Locale.ROOT,
                        "[%03d] UNEXPECTED %s: %s%n",
                        attempt,
                        exception.getClass()
                                .getSimpleName(),
                        exception.getMessage()
                );
            }
        }

        double elapsedSeconds =
                (System.nanoTime() - startedAt)
                        / 1_000_000_000.0d;

        System.out.println();
        System.out.println("RESULTS");
        System.out.println("-------");
        printRate(
                "Raw LLM exact",
                rawSuccess,
                config.attempts()
        );
        printRate(
                "Final pipeline",
                finalSuccess,
                config.attempts()
        );
        printRate(
                "Repaired by compiler",
                repaired,
                config.attempts()
        );

        System.out.printf(
                Locale.ROOT,
                "Elapsed: %.2fs (%.2fs/attempt)%n",
                elapsedSeconds,
                elapsedSeconds
                        / config.attempts()
        );

        if (!failures.isEmpty()) {
            System.out.println();
            System.out.println("Failure counters:");

            failures.forEach(
                    (reason, count) ->
                            System.out.println(
                                    "  "
                                            + reason
                                            + ": "
                                            + count
                            )
            );
        }

        System.out.println();

        if (
                config.failOnFinalError()
                        && finalSuccess
                        != config.attempts()
        ) {
            System.exit(2);
        }
    }

    private static Evaluation evaluate(
            AssistantSemanticPlan plan,
            Config config
    ) {
        if (
                plan == null
                        || plan.actions() == null
                        || plan.actions().isEmpty()
        ) {
            return Evaluation.failure(
                    "EMPTY_PLAN"
            );
        }

        AssistantPlanAction relationship =
                plan.actions()
                        .stream()
                        .filter(
                                action ->
                                        action != null
                                                && action.type()
                                                == AssistantActionType.CREATE_RELATIONSHIP
                        )
                        .findFirst()
                        .orElse(
                                null
                        );

        if (relationship == null) {
            return Evaluation.failure(
                    "NO_CREATE_RELATIONSHIP"
            );
        }

        if (
                relationship.relationshipType()
                        != config.expectedType()
        ) {
            return Evaluation.failure(
                    "WRONG_RELATIONSHIP_TYPE"
            );
        }

        if (
                relationship.sourceClassName()
                        == null
        ) {
            return Evaluation.failure(
                    "MISSING_SOURCE"
            );
        }

        if (
                relationship.targetClassName()
                        == null
        ) {
            return Evaluation.failure(
                    "MISSING_TARGET"
            );
        }

        if (
                !relationship.sourceClassName()
                        .equalsIgnoreCase(
                                config.expectedSource()
                        )
        ) {
            return Evaluation.failure(
                    "WRONG_SOURCE"
            );
        }

        if (
                !relationship.targetClassName()
                        .equalsIgnoreCase(
                                config.expectedTarget()
                        )
        ) {
            return Evaluation.failure(
                    "WRONG_TARGET"
            );
        }

        return Evaluation.successful();
    }

    private static void printRate(
            String label,
            int success,
            int total
    ) {
        System.out.printf(
                Locale.ROOT,
                "%-22s %4d/%-4d %6.2f%%%n",
                label + ":",
                success,
                total,
                100.0d
                        * success
                        / total
        );
    }

    private static void increment(
            Map<String, Integer> counters,
            String key
    ) {
        counters.merge(
                key,
                1,
                Integer::sum
        );
    }

    private static ProjectDocument documentWith(
            List<String> classNames
    ) {
        List<UmlClass> classes =
                classNames.stream()
                        .map(
                                name ->
                                        new UmlClass(
                                                UUID.randomUUID(),
                                                name,
                                                List.of()
                                        )
                        )
                        .toList();

        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(
                        classes,
                        List.of()
                ),
                DiagramLayout.empty()
        );
    }

    private record Evaluation(
            boolean success,
            String reason
    ) {
        private static Evaluation successful() {
            return new Evaluation(
                    true,
                    "OK"
            );
        }

        private static Evaluation failure(
                String reason
        ) {
            return new Evaluation(
                    false,
                    reason
            );
        }
    }

    private record Config(
            int attempts,
            String prompt,
            List<String> classes,
            String expectedSource,
            String expectedTarget,
            UmlRelationshipType expectedType,
            String llamaUrl,
            String model,
            boolean verbose,
            boolean failOnFinalError
    ) {
        private static Config from(
                String[] args
        ) {
            Map<String, String> options =
                    new LinkedHashMap<>();

            for (
                    String arg
                    : args
            ) {
                int separator =
                        arg.indexOf(
                                '='
                        );

                if (
                        separator <= 0
                                || separator
                                == arg.length() - 1
                ) {
                    continue;
                }

                options.put(
                        arg.substring(
                                0,
                                separator
                        ),
                        arg.substring(
                                separator + 1
                        )
                );
            }

            int attempts =
                    Integer.parseInt(
                            options.getOrDefault(
                                    "attempts",
                                    "20"
                            )
                    );

            if (attempts < 1) {
                throw new IllegalArgumentException(
                        "attempts must be >= 1"
                );
            }

            List<String> classes =
                    Arrays.stream(
                                    options.getOrDefault(
                                                    "classes",
                                                    "Animal,Mascota"
                                            )
                                            .split(
                                                    ","
                                            )
                            )
                            .map(
                                    String::trim
                            )
                            .filter(
                                    value ->
                                            !value.isBlank()
                            )
                            .toList();

            return new Config(
                    attempts,
                    options.getOrDefault(
                            "prompt",
                            "Crea una asociacion entre 4nimal y mascota"
                    ),
                    classes,
                    options.getOrDefault(
                            "expectedSource",
                            "Animal"
                    ),
                    options.getOrDefault(
                            "expectedTarget",
                            "Mascota"
                    ),
                    UmlRelationshipType.valueOf(
                            options.getOrDefault(
                                            "expectedType",
                                            "ASSOCIATION"
                                    )
                                    .toUpperCase(
                                            Locale.ROOT
                                    )
                    ),
                    options.getOrDefault(
                            "llamaUrl",
                            "http://127.0.0.1:8092"
                    ),
                    options.getOrDefault(
                            "model",
                            "local-model"
                    ),
                    Boolean.parseBoolean(
                            options.getOrDefault(
                                    "verbose",
                                    "false"
                            )
                    ),
                    Boolean.parseBoolean(
                            options.getOrDefault(
                                    "failOnFinalError",
                                    "false"
                            )
                    )
            );
        }
    }
}
