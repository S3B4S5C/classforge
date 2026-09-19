package com.classforge.generation.spring.assistant;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class GeneratedAssistantGatewayRenderer {

    String llama(String pkg) {
        return template("""
                package __PACKAGE__;

                import static __PACKAGE__.GeneratedAssistantTypes.*;

                import java.net.URI;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;
                import java.time.Duration;
                import java.time.Instant;
                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.stereotype.Component;
                import tools.jackson.databind.JsonNode;
                import tools.jackson.databind.json.JsonMapper;

                @Component
                public class GeneratedAssistantLlamaGateway {
                    private final JsonMapper json;
                    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                    private final String url;
                    private final String model;
                    private volatile Instant capabilityCheckedAt = Instant.EPOCH;

                    public GeneratedAssistantLlamaGateway(
                            JsonMapper json,
                            @Value("${app.assistant.llama-url:http://127.0.0.1:8092}") String url,
                            @Value("${app.assistant.llama-model:local-model}") String model
                    ) {
                        this.json = json;
                        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
                        this.model = model;
                    }

                    public Intent route(String text) {
                        Map<String, Object> parameters = objectSchema(Map.of(
                                "intent", Map.of("type", "string", "enum", List.of(
                                        "QUERY", "COUNT", "GET", "CREATE", "UPDATE", "DELETE",
                                        "SET_RELATION", "ADD_RELATION", "REMOVE_RELATION"))
                        ), List.of("intent"));
                        JsonNode args = call("route_data_request", "Clasifica la intencion de datos sin ejecutarla.", parameters,
                                "Eres el router local de la aplicacion generada. Usa una sola tool_call. "
                                        + "QUERY=listar/buscar; COUNT=contar; GET=un registro; CREATE=crear; UPDATE=modificar; DELETE=eliminar; "
                                        + "SET_RELATION=asignar relacion to-one; ADD_RELATION/REMOVE_RELATION=relacion many-to-many.", text);
                        try { return Intent.valueOf(args.path("intent").asString()); }
                        catch (RuntimeException exception) { throw new IllegalArgumentException("llama.cpp devolvio una intencion desconocida.", exception); }
                    }

                    public RawCommand command(String text, Intent intent) {
                        String tool = switch (intent) {
                            case QUERY -> "query_records"; case COUNT -> "count_records"; case GET -> "get_record";
                            case CREATE -> "create_record"; case UPDATE -> "update_record"; case DELETE -> "delete_record";
                            case SET_RELATION -> "set_relation"; case ADD_RELATION -> "add_relation"; case REMOVE_RELATION -> "remove_relation";
                        };
                        Map<String, Object> props = new LinkedHashMap<>();
                        props.put("entity", Map.of("type", "string", "enum", GeneratedAssistantMetadata.entityCodes()));
                        props.put("query", Map.of("type", "string"));
                        props.put("filters", entriesSchema(GeneratedAssistantMetadata.fieldNames()));
                        props.put("selector", entriesSchema(GeneratedAssistantMetadata.fieldNames()));
                        props.put("values", entriesSchema(GeneratedAssistantMetadata.fieldNames()));
                        List<String> relations = GeneratedAssistantMetadata.relationNames();
                        props.put("relation", relations.isEmpty() ? Map.of("type", "string") : Map.of("type", "string", "enum", relations));
                        props.put("targetSelector", entriesSchema(GeneratedAssistantMetadata.fieldNames()));
                        JsonNode args = call(tool, "Prepara un comando grounded para la operacion " + intent,
                                objectSchema(props, List.of("entity")), commandPrompt(intent), text);
                        return parseRaw(args);
                    }

                    private String commandPrompt(Intent intent) {
                        return "Eres el planificador local de datos de una aplicacion generada por ClassForge. "
                                + "Responde exclusivamente con una tool_call. No inventes entidades/campos/relaciones. "
                                + "Usa selector para identificar registros existentes; values solo para datos a escribir; "
                                + "targetSelector identifica el destino de una relacion. Para null escribe literalmente null. "
                                + "Para IDs compuestos usa cada campo del ID como entrada separada. Intent=" + intent + ". Catalogo:"
                                + GeneratedAssistantMetadata.catalogPrompt();
                    }

                    private RawCommand parseRaw(JsonNode node) {
                        return new RawCommand(text(node, "entity"), text(node, "query"), filters(node.get("filters")),
                                values(node.get("selector")), values(node.get("values")), text(node, "relation"), values(node.get("targetSelector")));
                    }
                    private List<FilterInput> filters(JsonNode node) {
                        List<FilterInput> out = new ArrayList<>();
                        if (node != null && node.isArray()) for (JsonNode item : node) out.add(new FilterInput(text(item, "field"), text(item, "value")));
                        return out;
                    }
                    private List<FieldValue> values(JsonNode node) {
                        List<FieldValue> out = new ArrayList<>();
                        if (node != null && node.isArray()) for (JsonNode item : node) out.add(new FieldValue(text(item, "field"), text(item, "value")));
                        return out;
                    }
                    private String text(JsonNode node, String field) {
                        if (node == null || node.get(field) == null || node.get(field).isNull()) return null;
                        return node.get(field).asString();
                    }

                    private Map<String, Object> entriesSchema(List<String> fields) {
                        Map<String, Object> itemProps = new LinkedHashMap<>();
                        itemProps.put("field", fields.isEmpty() ? Map.of("type", "string") : Map.of("type", "string", "enum", fields));
                        itemProps.put("value", Map.of("type", "string"));
                        return Map.of("type", "array", "items", objectSchema(itemProps, List.of("field", "value")));
                    }
                    private Map<String, Object> objectSchema(Map<String, Object> props, List<String> required) {
                        Map<String, Object> schema = new LinkedHashMap<>();
                        schema.put("type", "object"); schema.put("properties", props); schema.put("required", required); schema.put("additionalProperties", false);
                        return schema;
                    }

                    private JsonNode call(String toolName, String description, Map<String, Object> parameters, String system, String user) {
                        ensureNativeTools();
                        try {
                            Map<String, Object> function = new LinkedHashMap<>();
                            function.put("name", toolName); function.put("description", description); function.put("parameters", parameters);
                            Map<String, Object> request = new LinkedHashMap<>();
                            request.put("model", model); request.put("temperature", 0.0); request.put("max_tokens", 512);
                            request.put("stream", false); request.put("parallel_tool_calls", false); request.put("tool_choice", "required");
                            request.put("messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
                            request.put("tools", List.of(Map.of("type", "function", "function", function)));
                            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url + "/v1/chat/completions"))
                                    .timeout(Duration.ofSeconds(90)).header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request))).build();
                            HttpResponse<String> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                            if (response.statusCode() < 200 || response.statusCode() >= 300)
                                throw new IllegalStateException("llama.cpp respondio HTTP " + response.statusCode());
                            JsonNode root = json.readTree(response.body());
                            JsonNode calls = root.path("choices").path(0).path("message").path("tool_calls");
                            if (!calls.isArray() || calls.isEmpty()) throw new IllegalStateException("llama.cpp no devolvio native tool_calls.");
                            for (JsonNode call : calls) {
                                JsonNode fn = call.path("function");
                                if (!toolName.equals(fn.path("name").asString())) continue;
                                JsonNode raw = fn.get("arguments");
                                return raw != null && raw.isTextual() ? json.readTree(raw.asString()) : raw;
                            }
                            throw new IllegalStateException("llama.cpp no uso la tool esperada " + toolName + ".");
                        } catch (RuntimeException exception) { throw exception; }
                        catch (Exception exception) { throw new IllegalStateException("No se pudo ejecutar native tool calling en " + url, exception); }
                    }

                    private synchronized void ensureNativeTools() {
                        if (Duration.between(capabilityCheckedAt, Instant.now()).compareTo(Duration.ofSeconds(60)) < 0) return;
                        try {
                            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url + "/props")).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
                            JsonNode caps = response.statusCode() >= 200 && response.statusCode() < 300 ? json.readTree(response.body()).get("chat_template_caps") : null;
                            if (caps == null || !caps.path("supports_tools").asBoolean(false) || !caps.path("supports_tool_calls").asBoolean(false))
                                throw new IllegalStateException("llama.cpp no reporta supports_tools/supports_tool_calls.");
                            capabilityCheckedAt = Instant.now();
                        } catch (RuntimeException exception) { throw exception; }
                        catch (Exception exception) { throw new IllegalStateException("No se pudieron comprobar las capacidades de llama.cpp en " + url, exception); }
                    }
                }
                """, pkg);
    }

    String whisper(String pkg) {
        return template("""
                package __PACKAGE__;

                import java.io.ByteArrayOutputStream;
                import java.net.URI;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;
                import java.time.Duration;
                import java.util.UUID;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.stereotype.Component;
                import org.springframework.web.multipart.MultipartFile;
                import tools.jackson.databind.JsonNode;
                import tools.jackson.databind.json.JsonMapper;

                @Component
                public class GeneratedAssistantWhisperGateway {
                    public static final int MAX_AUDIO_BYTES = 4 * 1024 * 1024;
                    private final JsonMapper json;
                    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                    private final String url;
                    private final String language;

                    public GeneratedAssistantWhisperGateway(JsonMapper json,
                            @Value("${app.assistant.whisper-url:http://127.0.0.1:8093}") String url,
                            @Value("${app.assistant.whisper-language:es}") String language) {
                        this.json = json;
                        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
                        this.language = language == null || language.isBlank() ? "es" : language.trim();
                    }

                    public String transcribe(MultipartFile audio) {
                        try {
                            if (audio == null || audio.isEmpty()) throw new IllegalArgumentException("No se recibio audio.");
                            if (audio.getSize() > MAX_AUDIO_BYTES) throw new IllegalArgumentException("El audio supera 4 MB.");
                            byte[] bytes = audio.getBytes(); validateWav(bytes);
                            String boundary = "ClassForgeGenerated" + UUID.randomUUID().toString().replace("-", "");
                            byte[] body = multipart(boundary, bytes, audio.getOriginalFilename());
                            HttpRequest request = HttpRequest.newBuilder(URI.create(url + "/inference")).timeout(Duration.ofSeconds(45))
                                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
                            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                            if (response.statusCode() < 200 || response.statusCode() >= 300)
                                throw new IllegalStateException("whisper.cpp respondio HTTP " + response.statusCode());
                            JsonNode root = json.readTree(response.body());
                            String transcript = root.path("text").asString().replaceAll("\\s+", " ").trim();
                            if (transcript.isBlank()) throw new IllegalArgumentException("Whisper no detecto voz suficiente.");
                            if (transcript.length() > 1000) throw new IllegalArgumentException("La transcripcion supera 1000 caracteres.");
                            return transcript;
                        } catch (RuntimeException exception) { throw exception; }
                        catch (Exception exception) { throw new IllegalStateException("No se pudo transcribir con whisper.cpp en " + url, exception); }
                    }

                    private void validateWav(byte[] bytes) {
                        if (bytes == null || bytes.length < 44 || bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F'
                                || bytes[8] != 'W' || bytes[9] != 'A' || bytes[10] != 'V' || bytes[11] != 'E')
                            throw new IllegalArgumentException("Se esperaba audio WAV PCM.");
                    }

                    private byte[] multipart(String boundary, byte[] wav, String filename) throws Exception {
                        String safe = filename == null || filename.isBlank() ? "voice.wav" : filename.replaceAll("[^A-Za-z0-9._-]", "_");
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        write(out, "--" + boundary + "\\r\\nContent-Disposition: form-data; name=\\\"file\\\"; filename=\\\"" + safe + "\\\"\\r\\nContent-Type: audio/wav\\r\\n\\r\\n");
                        out.write(wav); write(out, "\\r\\n--" + boundary + "\\r\\nContent-Disposition: form-data; name=\\\"language\\\"\\r\\n\\r\\n" + language + "\\r\\n");
                        write(out, "--" + boundary + "--\\r\\n");
                        return out.toByteArray();
                    }
                    private void write(ByteArrayOutputStream out, String value) throws Exception { out.write(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
                }
                """, pkg);
    }

    String template(String text, String pkg) { return text.replace("__PACKAGE__", pkg); }
}
