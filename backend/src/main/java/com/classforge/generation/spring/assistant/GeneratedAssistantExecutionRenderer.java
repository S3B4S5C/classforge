package com.classforge.generation.spring.assistant;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class GeneratedAssistantExecutionRenderer {

    String executor(String pkg) {
        return template("""
                package __PACKAGE__;

                import static __PACKAGE__.GeneratedAssistantTypes.*;

                import java.net.URI;
                import java.net.URLEncoder;
                import java.net.http.HttpClient;
                import java.net.http.HttpRequest;
                import java.net.http.HttpResponse;
                import java.nio.charset.StandardCharsets;
                import java.time.Duration;
                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.List;
                import java.util.Map;
                import org.springframework.beans.factory.annotation.Value;
                import org.springframework.stereotype.Component;
                import tools.jackson.databind.JsonNode;
                import tools.jackson.databind.json.JsonMapper;

                @Component
                public class GeneratedAssistantHttpExecutor {
                    private final JsonMapper json;
                    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
                    private final String baseUrl;

                    public GeneratedAssistantHttpExecutor(JsonMapper json,
                            @Value("${app.assistant.api-base-url:http://127.0.0.1:8080}") String baseUrl) {
                        this.json = json;
                        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
                    }

                    public JsonNode execute(Command command, String authorization) {
                        var entity = GeneratedAssistantMetadata.entityByCode(command.entity());
                        return switch (command.intent()) {
                            case QUERY -> query(entity, command, authorization);
                            case COUNT -> count(entity, command, authorization);
                            case GET -> resolveOne(entity, command.selector(), authorization);
                            case CREATE -> request("POST", entity.endpoint(), Map.of(), materializeValues(entity, command, authorization), authorization);
                            case UPDATE -> update(entity, command, authorization);
                            case DELETE -> delete(entity, command, authorization);
                            case SET_RELATION, ADD_RELATION, REMOVE_RELATION -> relation(entity, command, authorization);
                        };
                    }

                    private JsonNode query(GeneratedAssistantMetadata.EntityMeta entity, Command command, String auth) {
                        Map<String, String> params = listParams(command);
                        return request("GET", entity.endpoint(), params, null, auth);
                    }
                    private JsonNode count(GeneratedAssistantMetadata.EntityMeta entity, Command command, String auth) {
                        if ((command.query() == null || command.query().isBlank()) && command.filters().isEmpty())
                            return request("GET", entity.endpoint() + "/count", Map.of(), null, auth);
                        JsonNode page = request("GET", entity.endpoint(), listParams(command), null, auth);
                        return json.createObjectNode().put("count", page.path("totalElements").asLong());
                    }
                    private Map<String, String> listParams(Command command) {
                        Map<String, String> params = new LinkedHashMap<>();
                        params.put("page", "0"); params.put("size", "100");
                        if (command.query() != null && !command.query().isBlank()) params.put("q", command.query());
                        command.filters().forEach((k, v) -> params.put("filter." + k, v));
                        return params;
                    }

                    private JsonNode update(GeneratedAssistantMetadata.EntityMeta entity, Command command, String auth) {
                        JsonNode current = resolveOne(entity, command.selector(), auth);
                        Map<String, Object> body = requestBody(entity, current);
                        body.putAll(materializeValues(entity, command, auth));
                        return request("PUT", itemPath(entity, current), Map.of(), body, auth);
                    }
                    private JsonNode delete(GeneratedAssistantMetadata.EntityMeta entity, Command command, String auth) {
                        JsonNode current = resolveOne(entity, command.selector(), auth);
                        request("DELETE", itemPath(entity, current), Map.of(), null, auth);
                        return json.createObjectNode().put("deleted", true);
                    }
                    private JsonNode relation(GeneratedAssistantMetadata.EntityMeta entity, Command command, String auth) {
                        JsonNode current = resolveOne(entity, command.selector(), auth);
                        var relation = GeneratedAssistantMetadata.requireRelation(entity, command.relation());
                        var targetEntity = GeneratedAssistantMetadata.entityByCode(relation.targetEntity());
                        JsonNode target = resolveOne(targetEntity, command.targetSelector(), auth);
                        Object targetId = idValue(targetEntity, target);
                        Map<String, Object> body = requestBody(entity, current);
                        if (command.intent() == Intent.SET_RELATION) {
                            body.put(relation.requestField(), targetId);
                        } else {
                            List<Object> ids = new ArrayList<>();
                            JsonNode existing = current.get(relation.requestField());
                            if (existing != null && existing.isArray()) existing.forEach(node -> ids.add(json.convertValue(node, Object.class)));
                            if (command.intent() == Intent.ADD_RELATION) {
                                if (!ids.contains(targetId)) ids.add(targetId);
                            } else ids.removeIf(value -> String.valueOf(value).equals(String.valueOf(targetId)));
                            body.put(relation.requestField(), ids);
                        }
                        return request("PUT", itemPath(entity, current), Map.of(), body, auth);
                    }

                    private Map<String, Object> materializeValues(GeneratedAssistantMetadata.EntityMeta entity, Command command, String auth) {
                        Map<String, Object> values = new LinkedHashMap<>(command.values());
                        for (var entry : command.relationValues().entrySet()) {
                            RelationValue pending = entry.getValue();
                            var relation = GeneratedAssistantMetadata.requireRelation(entity, pending.relation());
                            var targetEntity = GeneratedAssistantMetadata.entityByCode(relation.targetEntity());
                            JsonNode target = resolveOne(targetEntity, pending.selector(), auth);
                            values.put(relation.requestField(), idValue(targetEntity, target));
                        }
                        return values;
                    }

                    private JsonNode resolveOne(GeneratedAssistantMetadata.EntityMeta entity, Map<String, Object> selector, String auth) {
                        Map<String, String> params = new LinkedHashMap<>();
                        params.put("page", "0"); params.put("size", "2");
                        selector.forEach((k, v) -> params.put("filter." + k, String.valueOf(v)));
                        JsonNode page = request("GET", entity.endpoint(), params, null, auth);
                        JsonNode content = page.path("content");
                        long total = page.path("totalElements").asLong(content.isArray() ? content.size() : 0);
                        if (total == 0 || !content.isArray() || content.isEmpty()) throw new IllegalArgumentException("No se encontro un registro unico de " + entity.logicalName() + ".");
                        if (total != 1 || content.size() != 1) throw new IllegalArgumentException("El selector es ambiguo para " + entity.logicalName() + ": " + total + " coincidencias.");
                        return content.get(0);
                    }

                    private Map<String, Object> requestBody(GeneratedAssistantMetadata.EntityMeta entity, JsonNode current) {
                        Map<String, Object> body = new LinkedHashMap<>();
                        for (var field : entity.fields()) {
                            JsonNode node = current.get(field.apiName());
                            if (node != null && !field.sensitive()) body.put(field.apiName(), json.convertValue(node, Object.class));
                            else if (field.sensitive()) body.put(field.apiName(), null);
                        }
                        for (var relation : entity.relations()) {
                            JsonNode node = current.get(relation.requestField());
                            body.put(relation.requestField(), node == null || node.isNull() ? null : json.convertValue(node, Object.class));
                        }
                        return body;
                    }
                    private Object idValue(GeneratedAssistantMetadata.EntityMeta entity, JsonNode record) {
                        if (entity.identifier().size() == 1) return json.convertValue(record.get(entity.identifier().getFirst().name()), Object.class);
                        Map<String, Object> id = new LinkedHashMap<>();
                        for (var field : entity.identifier()) id.put(field.name(), json.convertValue(record.get(field.name()), Object.class));
                        return id;
                    }
                    private String itemPath(GeneratedAssistantMetadata.EntityMeta entity, JsonNode record) {
                        if (entity.identifier().size() == 1) {
                            String value = record.path(entity.identifier().getFirst().name()).asString();
                            return entity.endpoint() + "/" + encode(value);
                        }
                        StringBuilder path = new StringBuilder(entity.endpoint()).append("/by-id?");
                        boolean first = true;
                        for (var field : entity.identifier()) {
                            if (!first) path.append('&'); first = false;
                            path.append(encode(field.name())).append('=').append(encode(record.path(field.name()).asString()));
                        }
                        return path.toString();
                    }

                    private JsonNode request(String method, String path, Map<String, String> query, Object body, String authorization) {
                        try {
                            StringBuilder url = new StringBuilder(baseUrl).append(path);
                            if (query != null && !query.isEmpty()) {
                                url.append(path.contains("?") ? '&' : '?'); boolean first = true;
                                for (var entry : query.entrySet()) { if (!first) url.append('&'); first = false; url.append(encode(entry.getKey())).append('=').append(encode(entry.getValue())); }
                            }
                            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url.toString())).timeout(Duration.ofSeconds(20)).header("Accept", "application/json");
                            if (authorization != null && !authorization.isBlank()) builder.header("Authorization", authorization);
                            String payload = body == null ? null : json.writeValueAsString(body);
                            if (payload != null) builder.header("Content-Type", "application/json");
                            switch (method) {
                                case "GET" -> builder.GET();
                                case "POST" -> builder.POST(HttpRequest.BodyPublishers.ofString(payload == null ? "{}" : payload));
                                case "PUT" -> builder.PUT(HttpRequest.BodyPublishers.ofString(payload == null ? "{}" : payload));
                                case "DELETE" -> builder.DELETE();
                                default -> throw new IllegalArgumentException("Metodo HTTP no soportado: " + method);
                            }
                            HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                            if (response.statusCode() < 200 || response.statusCode() >= 300)
                                throw new IllegalArgumentException("La API genero HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
                            if (response.body() == null || response.body().isBlank()) return json.readTree("null");
                            return json.readTree(response.body());
                        } catch (RuntimeException exception) { throw exception; }
                        catch (Exception exception) { throw new IllegalStateException("No se pudo ejecutar el plan contra la API generada.", exception); }
                    }
                    private String encode(String value) { return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8); }
                    private String abbreviate(String value) { return value == null || value.length() <= 400 ? value : value.substring(0, 400) + "..."; }
                }
                """, pkg);
    }

    String template(String text, String pkg) { return text.replace("__PACKAGE__", pkg); }
}
