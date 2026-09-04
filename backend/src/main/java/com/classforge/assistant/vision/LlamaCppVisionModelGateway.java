package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(
        name = "classforge.assistant.vision.provider",
        havingValue = "llama-cpp"
)
public class LlamaCppVisionModelGateway implements VisionModelGateway {

    private final JsonMapper jsonMapper;
    private final VisionPromptBuilder promptBuilder;
    private final HttpClient httpClient;
    private final String url;
    private final String model;
    private final Duration requestTimeout;
    private final int maxCompletionTokens;
    private final boolean denseTwoPassEnabled;
    private final int denseTwoPassMinClasses;
    private final int relationshipMaxCompletionTokens;

    @Autowired
    public LlamaCppVisionModelGateway(
            JsonMapper jsonMapper,
            VisionPromptBuilder promptBuilder,
            @Value("${classforge.assistant.vision.url:http://127.0.0.1:8094}") String url,
            @Value("${classforge.assistant.vision.model:vision-model}") String model,
            @Value("${classforge.assistant.vision.request-timeout-seconds:180}") long requestTimeoutSeconds,
            @Value("${classforge.assistant.vision.max-completion-tokens:3200}") int maxCompletionTokens,
            @Value("${classforge.assistant.vision.dense-two-pass-enabled:true}") boolean denseTwoPassEnabled,
            @Value("${classforge.assistant.vision.dense-two-pass-min-classes:4}") int denseTwoPassMinClasses,
            @Value("${classforge.assistant.vision.relationship-max-completion-tokens:1800}") int relationshipMaxCompletionTokens
    ) {
        this.jsonMapper = jsonMapper;
        this.promptBuilder = promptBuilder;
        this.url = normalizeBaseUrl(url);
        this.model = required(model, "Vision model");
        this.requestTimeout = Duration.ofSeconds(Math.max(1L, requestTimeoutSeconds));
        this.maxCompletionTokens = Math.max(256, maxCompletionTokens);
        this.denseTwoPassEnabled = denseTwoPassEnabled;
        this.denseTwoPassMinClasses = Math.max(2, denseTwoPassMinClasses);
        this.relationshipMaxCompletionTokens = Math.max(256, relationshipMaxCompletionTokens);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    LlamaCppVisionModelGateway(
            JsonMapper jsonMapper,
            VisionPromptBuilder promptBuilder,
            String url,
            String model,
            long requestTimeoutSeconds,
            int maxCompletionTokens
    ) {
        this(
                jsonMapper,
                promptBuilder,
                url,
                model,
                requestTimeoutSeconds,
                maxCompletionTokens,
                false,
                4,
                1800
        );
    }

    @Override
    public VisionUmlProposal analyze(
            VisionNormalizedImage image,
            VisionProjectContext context
    ) {
        if (image == null || image.bytes() == null || image.bytes().length == 0) {
            throw contract("No hay imagen normalizada para enviar al VLM.");
        }

        VisionUmlProposal firstPass = executeFirstPass(image, context);
        if (!shouldRunRelationshipPass(firstPass)) {
            return firstPass;
        }

        VisionRelationshipPassProposal relationshipPass = executeRelationshipPass(
                image,
                firstPass
        );
        return mergeRelationshipPass(firstPass, relationshipPass);
    }

    private VisionUmlProposal executeFirstPass(
            VisionNormalizedImage image,
            VisionProjectContext context
    ) {
        try {
            String content = completionContent(
                    requestPayload(image, context),
                    "principal"
            );
            return jsonMapper.readValue(content, VisionUmlProposal.class);
        } catch (AssistantPlanningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw outputContract(
                    "La salida del VLM no cumple VisionUmlProposal; se rechazo sin reparacion heuristica.",
                    exception
            );
        }
    }

    private VisionRelationshipPassProposal executeRelationshipPass(
            VisionNormalizedImage image,
            VisionUmlProposal firstPass
    ) {
        try {
            String content = completionContent(
                    relationshipRequestPayload(image, firstPass),
                    "relationships-only"
            );
            return jsonMapper.readValue(content, VisionRelationshipPassProposal.class);
        } catch (AssistantPlanningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw outputContract(
                    "La segunda pasada visual no cumple VisionRelationshipPassProposal; se rechazo sin reparacion heuristica.",
                    exception
            );
        }
    }

    private String completionContent(
            Map<String, Object> payload,
            String passName
    ) {
        try {
            String requestBody = jsonMapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(url + "/v1/chat/completions")
                    )
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw transport(
                        "El VLM local rechazo la pasada " + passName + ": HTTP "
                                + response.statusCode()
                                + responseSuffix(response.body()),
                        null
                );
            }

            JsonNode root = jsonMapper.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                throw contract("llama.cpp no devolvio choices en la pasada " + passName + ".");
            }

            JsonNode first = choices.get(0);
            JsonNode finishReason = first.get("finish_reason");
            if (finishReason != null && "length".equalsIgnoreCase(finishReason.asString())) {
                throw contract(
                        "La respuesta visual de la pasada " + passName
                                + " quedo truncada por max_tokens; no se intenta reparar JSON."
                );
            }

            JsonNode message = first.get("message");
            JsonNode content = message == null ? null : message.get("content");
            if (content == null || !content.isString() || content.asString().isBlank()) {
                throw contract(
                        "llama.cpp no devolvio contenido JSON textual en la pasada " + passName + "."
                );
            }
            return content.asString();
        } catch (HttpTimeoutException exception) {
            throw transport(
                    "El VLM local excedio el timeout de " + requestTimeout.toSeconds()
                            + " s durante la pasada " + passName + ".",
                    exception
            );
        } catch (AssistantPlanningException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw transport("La inferencia visual fue interrumpida durante la pasada " + passName + ".", exception);
        } catch (Exception exception) {
            throw transport(
                    "No pudimos completar la pasada " + passName + " contra llama.cpp.",
                    exception
            );
        }
    }

    Map<String, Object> requestPayload(
            VisionNormalizedImage image,
            VisionProjectContext context
    ) throws Exception {
        return requestPayload(
                image,
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(context, image.width(), image.height()),
                VisionUmlProposalJsonSchema.json(),
                maxCompletionTokens
        );
    }

    Map<String, Object> relationshipRequestPayload(
            VisionNormalizedImage image,
            VisionUmlProposal firstPass
    ) throws Exception {
        return requestPayload(
                image,
                promptBuilder.relationshipSystemPrompt(),
                promptBuilder.relationshipUserPrompt(firstPass, image.width(), image.height()),
                VisionRelationshipPassJsonSchema.json(),
                relationshipMaxCompletionTokens
        );
    }

    private Map<String, Object> requestPayload(
            VisionNormalizedImage image,
            String systemPrompt,
            String userPrompt,
            String schema,
            int completionTokens
    ) throws Exception {
        Map<String, Object> dataUrl = Map.of(
                "url",
                "data:" + image.mediaType() + ";base64,"
                        + Base64.getEncoder().encodeToString(image.bytes())
        );

        List<Map<String, Object>> userContent = List.of(
                Map.of(
                        "type", "text",
                        "text", userPrompt
                ),
                Map.of(
                        "type", "image_url",
                        "image_url", dataUrl
                )
        );

        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_object");
        responseFormat.put("schema", jsonMapper.readTree(schema));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("stream", false);
        payload.put("temperature", 0.0);
        payload.put("max_tokens", completionTokens);
        payload.put("messages", List.of(
                Map.of(
                        "role", "system",
                        "content", systemPrompt
                ),
                Map.of(
                        "role", "user",
                        "content", userContent
                )
        ));
        payload.put("response_format", responseFormat);
        payload.put("reasoning_effort", "none");
        payload.put("chat_template_kwargs", Map.of("enable_thinking", false));
        return payload;
    }

    private boolean shouldRunRelationshipPass(VisionUmlProposal firstPass) {
        return denseTwoPassEnabled
                && firstPass != null
                && firstPass.safeClasses().size() >= denseTwoPassMinClasses;
    }

    private VisionUmlProposal mergeRelationshipPass(
            VisionUmlProposal firstPass,
            VisionRelationshipPassProposal relationshipPass
    ) {
        if (relationshipPass == null) {
            throw contract("La segunda pasada visual no devolvio propuesta de relaciones.");
        }

        Set<String> allowedRefs = new LinkedHashSet<>();
        for (VisionClassProposal umlClass : firstPass.safeClasses()) {
            allowedRefs.add(normalizeRef(requiredProposal(umlClass.ref(), "class.ref")));
        }
        if (allowedRefs.isEmpty()) {
            throw contract("La segunda pasada visual no puede ejecutarse sin refs de clases confirmadas.");
        }

        Set<String> seenRelationships = new LinkedHashSet<>();
        for (VisionRelationshipProposal relationship : relationshipPass.safeRelationships()) {
            String source = normalizeRef(requiredProposal(relationship.sourceRef(), "relationship.sourceRef"));
            String target = normalizeRef(requiredProposal(relationship.targetRef(), "relationship.targetRef"));
            if (!allowedRefs.contains(source) || !allowedRefs.contains(target)) {
                throw contract(
                        "La segunda pasada visual intento usar un ref de clase no confirmado: "
                                + relationship.sourceRef() + " -> " + relationship.targetRef() + "."
                );
            }

            String type = requiredProposal(relationship.type(), "relationship.type")
                    .toUpperCase(Locale.ROOT);
            String duplicateKey;
            if ("ASSOCIATION".equals(type)) {
                duplicateKey = source.compareTo(target) <= 0
                        ? type + "|" + source + "|" + target
                        : type + "|" + target + "|" + source;
            } else {
                duplicateKey = type + "|" + source + "|" + target;
            }
            if (!seenRelationships.add(duplicateKey)) {
                throw contract("La segunda pasada visual repitio una relacion: " + duplicateKey + ".");
            }
        }

        List<String> warnings = new ArrayList<>();
        warnings.addAll(firstPass.safeWarnings());
        warnings.addAll(relationshipPass.safeWarnings());

        Double confidence = combinedConfidence(
                firstPass.confidence(),
                relationshipPass.confidence()
        );

        return new VisionUmlProposal(
                firstPass.summary(),
                firstPass.safeClasses(),
                relationshipPass.safeRelationships(),
                List.copyOf(new LinkedHashSet<>(warnings)),
                confidence
        );
    }

    private Double combinedConfidence(Double first, Double second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return Math.min(first, second);
    }

    private String normalizeRef(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String requiredProposal(String value, String field) {
        if (value == null || value.isBlank()) {
            throw contract("La segunda pasada visual no incluye " + field + ".");
        }
        return value.trim();
    }

    private VisionModelGatewayException contract(String message) {
        return new VisionModelGatewayException(
                VisionModelGatewayException.Reason.OUTPUT_CONTRACT,
                message
        );
    }

    private VisionModelGatewayException outputContract(String message, Throwable cause) {
        return new VisionModelGatewayException(
                VisionModelGatewayException.Reason.OUTPUT_CONTRACT,
                message,
                cause
        );
    }

    private VisionModelGatewayException transport(String message, Throwable cause) {
        return new VisionModelGatewayException(
                VisionModelGatewayException.Reason.TRANSPORT,
                message,
                cause
        );
    }

    private String responseSuffix(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        if (compact.length() > 220) {
            compact = compact.substring(0, 220) + "...";
        }
        return " · " + compact;
    }

    private String normalizeBaseUrl(String value) {
        String normalized = required(value, "Vision URL");
        return normalized.endsWith("/")
                ? normalized.substring(0, normalized.length() - 1)
                : normalized;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
