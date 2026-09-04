package com.classforge.assistant.vision;

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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "classforge.assistant.vision.provider", havingValue = "llama-cpp")
public class LlamaCppVisionHybridModelGateway implements VisionHybridModelGateway {

    private final JsonMapper jsonMapper;
    private final VisionHybridPromptBuilder promptBuilder;
    private final HttpClient httpClient;
    private final String url;
    private final String model;
    private final Duration timeout;
    private final int localizationTokens;
    private final int relationshipTokens;

    @Autowired
    public LlamaCppVisionHybridModelGateway(
            JsonMapper jsonMapper,
            VisionHybridPromptBuilder promptBuilder,
            @Value("${classforge.assistant.vision.url:http://127.0.0.1:8094}") String url,
            @Value("${classforge.assistant.vision.model:vision-model}") String model,
            @Value("${classforge.assistant.vision.request-timeout-seconds:180}") long timeoutSeconds,
            @Value("${classforge.assistant.vision.dense-hybrid.localization-max-completion-tokens:1200}") int localizationTokens,
            @Value("${classforge.assistant.vision.dense-hybrid.relationship-max-completion-tokens:1800}") int relationshipTokens
    ) {
        this.jsonMapper = jsonMapper;
        this.promptBuilder = promptBuilder;
        this.url = normalizeBaseUrl(url);
        this.model = required(model, "Vision model");
        this.timeout = Duration.ofSeconds(Math.max(1L, timeoutSeconds));
        this.localizationTokens = Math.max(256, localizationTokens);
        this.relationshipTokens = Math.max(256, relationshipTokens);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public VisionGeometryClassMappingProposal mapClassRegions(
            VisionNormalizedImage labeledRegionsImage,
            List<VisionGeometryClassRegion> regions,
            List<VisionClassProposal> classes
    ) {
        try {
            String content = completion(
                    labeledRegionsImage,
                    promptBuilder.mappingSystemPrompt(),
                    promptBuilder.mappingUserPrompt(regions, classes),
                    VisionGeometryClassMappingJsonSchema.json(),
                    localizationTokens,
                    "mapeo-cerrado-de-cajas"
            );
            return jsonMapper.readValue(content, VisionGeometryClassMappingProposal.class);
        } catch (VisionModelGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw outputContract("El mapeo cerrado Bx->classRef no cumple su contrato JSON.", exception);
        }
    }

    @Override
    public VisionHybridRelationshipAnnotationProposal annotateRelationships(
            VisionNormalizedImage evidenceSheet,
            List<VisionGeometryEdgeCandidate> candidates,
            List<VisionClassProposal> classes
    ) {
        try {
            String content = completion(
                    evidenceSheet,
                    promptBuilder.relationshipSystemPrompt(),
                    promptBuilder.relationshipUserPrompt(candidates, classes),
                    VisionHybridRelationshipJsonSchema.json(),
                    relationshipTokens,
                    "anotacion-local-de-relaciones"
            );
            return jsonMapper.readValue(content, VisionHybridRelationshipAnnotationProposal.class);
        } catch (VisionModelGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw outputContract("La anotacion local de relaciones no cumple su contrato JSON.", exception);
        }
    }

    private String completion(
            VisionNormalizedImage image,
            String systemPrompt,
            String userPrompt,
            String schema,
            int maxTokens,
            String stage
    ) throws Exception {
        Map<String, Object> imageUrl = Map.of(
                "url", "data:" + image.mediaType() + ";base64," + Base64.getEncoder().encodeToString(image.bytes())
        );
        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", userPrompt),
                Map.of("type", "image_url", "image_url", imageUrl)
        );
        Map<String, Object> responseFormat = new LinkedHashMap<>();
        responseFormat.put("type", "json_object");
        responseFormat.put("schema", jsonMapper.readTree(schema));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("stream", false);
        payload.put("temperature", 0.0);
        payload.put("max_tokens", maxTokens);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", content)
        ));
        payload.put("response_format", responseFormat);
        payload.put("reasoning_effort", "none");
        payload.put("chat_template_kwargs", Map.of("enable_thinking", false));

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/v1/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw transport("llama.cpp rechazo " + stage + ": HTTP " + response.statusCode(), null);
            }
            JsonNode root = jsonMapper.readTree(response.body());
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                throw outputContract("llama.cpp no devolvio choices en " + stage + ".", null);
            }
            JsonNode first = choices.get(0);
            JsonNode finish = first.get("finish_reason");
            if (finish != null && "length".equalsIgnoreCase(finish.asString())) {
                throw outputContract("La respuesta de " + stage + " quedo truncada por max_tokens.", null);
            }
            JsonNode message = first.get("message");
            JsonNode text = message == null ? null : message.get("content");
            if (text == null || !text.isString() || text.asString().isBlank()) {
                throw outputContract("llama.cpp no devolvio JSON textual en " + stage + ".", null);
            }
            return text.asString();
        } catch (HttpTimeoutException exception) {
            throw transport("El VLM excedio " + timeout.toSeconds() + " s durante " + stage + ".", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw transport("La inferencia fue interrumpida durante " + stage + ".", exception);
        }
    }

    private VisionModelGatewayException outputContract(String message, Throwable cause) {
        return new VisionModelGatewayException(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, message, cause);
    }

    private VisionModelGatewayException transport(String message, Throwable cause) {
        return new VisionModelGatewayException(VisionModelGatewayException.Reason.TRANSPORT, message, cause);
    }

    private String normalizeBaseUrl(String value) {
        String normalized = required(value, "Vision URL");
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
