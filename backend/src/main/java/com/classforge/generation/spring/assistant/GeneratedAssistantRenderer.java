package com.classforge.generation.spring.assistant;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedFileType;
import com.classforge.generation.spring.model.SpringGenerationModel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/** Generates the CU-19 local natural-language/voice assistant inside exported Spring projects. */
public final class GeneratedAssistantRenderer {
    private static final String DIR = "/assistant/";

    public List<GeneratedFile> render(SpringGenerationModel model, DomainManifestPlan manifest) {
        Objects.requireNonNull(model, "model is required");
        Objects.requireNonNull(manifest, "manifest is required");
        String pkg = model.basePackage() + ".assistant";
        String path = "src/main/java/" + model.basePackage().replace('.', '/') + "/assistant/";
        List<GeneratedFile> files = new ArrayList<>();
        add(files, path + "GeneratedAssistantTypes.java", types(pkg));
        add(files, path + "GeneratedAssistantMetadata.java", metadata(pkg, manifest));
        add(files, path + "GeneratedAssistantLlamaGateway.java", llama(pkg));
        add(files, path + "GeneratedAssistantWhisperGateway.java", whisper(pkg));
        add(files, path + "GeneratedAssistantHttpExecutor.java", executor(pkg));
        add(files, path + "GeneratedAssistantService.java", service(pkg));
        add(files, path + "GeneratedAssistantController.java", controller(pkg));
        return List.copyOf(files);
    }

    private void add(List<GeneratedFile> files, String path, String text) {
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        if (!normalized.endsWith("\n")) normalized += "\n";
        files.add(new GeneratedFile(path, GeneratedFileType.TEXT, normalized.getBytes(StandardCharsets.UTF_8)));
    }

    private String types(String pkg) {
        return template("""
                package __PACKAGE__;

                import java.util.List;
                import java.util.Map;
                import tools.jackson.databind.JsonNode;

                public final class GeneratedAssistantTypes {
                    private GeneratedAssistantTypes() { }

                    public enum Intent {
                        QUERY, COUNT, GET, CREATE, UPDATE, DELETE,
                        SET_RELATION, ADD_RELATION, REMOVE_RELATION;

                        public boolean mutating() {
                            return switch (this) {
                                case QUERY, COUNT, GET -> false;
                                default -> true;
                            };
                        }
                    }

                    public record FilterInput(String field, String value) { }
                    public record FieldValue(String field, String value) { }

                    public record RawCommand(
                            String entity,
                            String query,
                            List<FilterInput> filters,
                            List<FieldValue> selector,
                            List<FieldValue> values,
                            String relation,
                            List<FieldValue> targetSelector
                    ) {
                        public RawCommand {
                            filters = List.copyOf(filters == null ? List.of() : filters);
                            selector = List.copyOf(selector == null ? List.of() : selector);
                            values = List.copyOf(values == null ? List.of() : values);
                            targetSelector = List.copyOf(targetSelector == null ? List.of() : targetSelector);
                        }
                    }

                    public record Command(
                            Intent intent,
                            String entity,
                            String query,
                            Map<String, String> filters,
                            Map<String, Object> selector,
                            Map<String, Object> values,
                            String relation,
                            Map<String, Object> targetSelector
                    ) { }

                    public record PlanRequest(String text) { }
                    public record ApplyRequest(String previewToken) { }

                    public record PlanResponse(
                            String source,
                            String transcript,
                            String intent,
                            String summary,
                            boolean requiresConfirmation,
                            String previewToken,
                            JsonNode result
                    ) { }
                }
                """, pkg);
    }

    private String metadata(String pkg, DomainManifestPlan manifest) {
        Map<UUID, DomainManifestPlan.Entity> byId = manifest.entities().stream()
                .collect(Collectors.toMap(DomainManifestPlan.Entity::id, e -> e));
        StringBuilder init = new StringBuilder();
        init.append("        List<EntityMeta> entities = new ArrayList<>();\n");
        for (DomainManifestPlan.Entity entity : manifest.entities()) {
            String fields = entity.attributes().stream().map(a ->
                    "new FieldMeta(\"%s\", \"%s\", \"%s\", %s, %s, %s, %s, %s, %s, %s, %s, %s)".formatted(
                            j(a.logicalName()), j(a.apiName()), a.type().name(), a.nullable(), a.identifier(),
                            a.createWritable(), a.updateWritable(), a.filterable(), a.searchable(), a.sensitive(),
                            a.validation().requiredOnCreate(), a.validation().requiredOnUpdate()))
                    .collect(Collectors.joining(",\n                        "));
            String relations = entity.relations().stream().map(r -> {
                DomainManifestPlan.Entity target = byId.get(r.targetEntityId());
                String ids = r.targetIdentifier().fields().stream()
                        .map(f -> "new IdField(\"%s\", \"%s\")".formatted(j(f.name()), f.type().name()))
                        .collect(Collectors.joining(", "));
                return "new RelationMeta(\"%s\", \"%s\", \"%s\", \"%s\", List.of(%s), %s)".formatted(
                        j(r.name()), j(r.requestField()), j(r.kind()), j(target == null ? r.targetEntityName() : target.codeName()), ids,
                        r.optional() == null ? "true" : r.optional().toString());
            }).collect(Collectors.joining(",\n                        "));
            String idFields = entity.identifier().fields().stream()
                    .map(f -> "new IdField(\"%s\", \"%s\")".formatted(j(f.name()), f.type().name()))
                    .collect(Collectors.joining(", "));
            String capabilities = entity.capabilities().stream().map(c -> "\"" + j(c) + "\"").collect(Collectors.joining(", "));
            List<String> aliasesList = new ArrayList<>();
            aliasesList.add(entity.logicalName());
            aliasesList.add(entity.codeName());
            aliasesList.add(entity.tableName());
            aliasesList.add(entity.displayName());
            aliasesList.addAll(entity.aliases());
            String aliases = aliasesList.stream().filter(Objects::nonNull).distinct().map(a -> "\"" + j(a) + "\"").collect(Collectors.joining(", "));
            init.append("        entities.add(new EntityMeta(\"").append(j(entity.logicalName())).append("\", \"")
                    .append(j(entity.codeName())).append("\", \"").append(j(entity.tableName())).append("\", \"")
                    .append(j(entity.endpoint())).append("\", List.of(").append(aliases).append("), List.of(")
                    .append(capabilities).append("), List.of(\n                        ").append(fields).append("\n                ), List.of(")
                    .append(idFields).append("), List.of(\n                        ").append(relations).append("\n                )));\n");
        }
        init.append("        ENTITIES = List.copyOf(entities);\n");

        String source = """
                package __PACKAGE__;

                import static __PACKAGE__.GeneratedAssistantTypes.*;

                import java.math.BigDecimal;
                import java.text.Normalizer;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.util.ArrayList;
                import java.util.LinkedHashMap;
                import java.util.LinkedHashSet;
                import java.util.List;
                import java.util.Locale;
                import java.util.Map;
                import java.util.Set;
                import java.util.UUID;

                public final class GeneratedAssistantMetadata {
                    private GeneratedAssistantMetadata() { }

                    public record IdField(String name, String type) { }
                    public record FieldMeta(
                            String logicalName,
                            String apiName,
                            String type,
                            boolean nullable,
                            boolean identifier,
                            boolean createWritable,
                            boolean updateWritable,
                            boolean filterable,
                            boolean searchable,
                            boolean sensitive,
                            boolean requiredOnCreate,
                            boolean requiredOnUpdate
                    ) { }
                    public record RelationMeta(
                            String name,
                            String requestField,
                            String kind,
                            String targetEntity,
                            List<IdField> targetIdentifier,
                            boolean optional
                    ) {
                        public boolean many() { return "MANY_TO_MANY".equals(kind); }
                    }
                    public record EntityMeta(
                            String logicalName,
                            String codeName,
                            String tableName,
                            String endpoint,
                            List<String> aliases,
                            List<String> capabilities,
                            List<FieldMeta> fields,
                            List<IdField> identifier,
                            List<RelationMeta> relations
                    ) { }

                    private static final List<EntityMeta> ENTITIES;
                    static {
                __INIT__
                    }

                    public static List<EntityMeta> entities() { return ENTITIES; }

                    public static EntityMeta requireEntity(String reference) {
                        String wanted = normalize(reference);
                        return ENTITIES.stream()
                                .filter(entity -> entity.aliases().stream().map(GeneratedAssistantMetadata::normalize).anyMatch(wanted::equals))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Entidad desconocida: " + reference));
                    }

                    public static EntityMeta entityByCode(String codeName) {
                        return ENTITIES.stream().filter(e -> e.codeName().equals(codeName)).findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Entidad desconocida: " + codeName));
                    }

                    public static FieldMeta requireField(EntityMeta entity, String reference) {
                        String wanted = normalize(reference);
                        return entity.fields().stream()
                                .filter(field -> wanted.equals(normalize(field.logicalName())) || wanted.equals(normalize(field.apiName())))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Campo desconocido para " + entity.logicalName() + ": " + reference));
                    }

                    public static RelationMeta requireRelation(EntityMeta entity, String reference) {
                        String wanted = normalize(reference);
                        return entity.relations().stream()
                                .filter(relation -> wanted.equals(normalize(relation.name())) || wanted.equals(normalize(relation.requestField())))
                                .findFirst()
                                .orElseThrow(() -> new IllegalArgumentException("Relacion desconocida para " + entity.logicalName() + ": " + reference));
                    }

                    public static Command resolve(Intent intent, RawCommand raw) {
                        if (raw == null) throw new IllegalArgumentException("llama.cpp no produjo un comando de datos.");
                        EntityMeta entity = requireEntity(raw.entity());
                        requireCapability(entity, intent);

                        Map<String, String> filters = new LinkedHashMap<>();
                        for (FilterInput input : raw.filters()) {
                            FieldMeta field = requireField(entity, input.field());
                            if (!field.filterable() && !field.searchable()) {
                                throw new IllegalArgumentException("El campo " + field.apiName() + " no admite busqueda/filtro.");
                            }
                            if (field.sensitive()) throw new IllegalArgumentException("Los campos sensibles no pueden usarse como filtro de voz/chat.");
                            Object parsed = parse(field.type(), input.value(), field.nullable());
                            filters.put(field.apiName(), parsed == null ? "" : String.valueOf(parsed));
                        }

                        Map<String, Object> selector = selector(entity, raw.selector());
                        Map<String, Object> values = values(entity, raw.values(), intent);
                        RelationMeta relation = null;
                        Map<String, Object> targetSelector = Map.of();
                        if (intent == Intent.SET_RELATION || intent == Intent.ADD_RELATION || intent == Intent.REMOVE_RELATION) {
                            relation = requireRelation(entity, raw.relation());
                            if (intent == Intent.SET_RELATION && relation.many()) {
                                throw new IllegalArgumentException("SET_RELATION solo se admite para relaciones to-one.");
                            }
                            if ((intent == Intent.ADD_RELATION || intent == Intent.REMOVE_RELATION) && !relation.many()) {
                                throw new IllegalArgumentException(intent + " solo se admite para relaciones many-to-many.");
                            }
                            targetSelector = selector(entityByCode(relation.targetEntity()), raw.targetSelector());
                        }

                        if ((intent == Intent.GET || intent == Intent.UPDATE || intent == Intent.DELETE
                                || intent == Intent.SET_RELATION || intent == Intent.ADD_RELATION || intent == Intent.REMOVE_RELATION)
                                && selector.isEmpty()) {
                            throw new IllegalArgumentException("La operacion necesita un selector de registro.");
                        }
                        if ((intent == Intent.CREATE || intent == Intent.UPDATE) && values.isEmpty()) {
                            throw new IllegalArgumentException("La operacion necesita valores a escribir.");
                        }
                        if (intent == Intent.CREATE) validateCreateRequired(entity, values);

                        return new Command(intent, entity.codeName(), trim(raw.query()), copy(filters), copy(selector),
                                copy(values), relation == null ? null : relation.name(), copy(targetSelector));
                    }

                    private static Map<String, Object> selector(EntityMeta entity, List<FieldValue> inputs) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        for (FieldValue input : inputs == null ? List.<FieldValue>of() : inputs) {
                            FieldMeta field = requireField(entity, input.field());
                            if (field.sensitive()) throw new IllegalArgumentException("No se puede seleccionar un registro por un campo sensible.");
                            result.put(field.apiName(), parse(field.type(), input.value(), field.nullable()));
                        }
                        return result;
                    }

                    private static Map<String, Object> values(EntityMeta entity, List<FieldValue> inputs, Intent intent) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        for (FieldValue input : inputs == null ? List.<FieldValue>of() : inputs) {
                            FieldMeta scalar = findField(entity, input.field());
                            if (scalar != null) {
                                boolean writable = intent == Intent.CREATE ? scalar.createWritable() : scalar.updateWritable();
                                if (!writable) throw new IllegalArgumentException("El campo " + scalar.apiName() + " no es escribible para " + intent + ".");
                                result.put(scalar.apiName(), parse(scalar.type(), input.value(), scalar.nullable()));
                                continue;
                            }
                            RelationMeta relation = findRelation(entity, input.field());
                            if (relation == null) throw new IllegalArgumentException("Campo/relacion desconocido: " + input.field());
                            if (relation.many()) {
                                throw new IllegalArgumentException("Las relaciones many-to-many se modifican con ADD_RELATION/REMOVE_RELATION.");
                            }
                            result.put(relation.requestField(), parseRelationId(relation.targetIdentifier(), input.value(), relation.optional()));
                        }
                        return result;
                    }

                    private static Object parseRelationId(List<IdField> fields, String raw, boolean nullable) {
                        if (isNull(raw)) {
                            if (!nullable) throw new IllegalArgumentException("La relacion es obligatoria y no admite null.");
                            return null;
                        }
                        if (fields.size() == 1) return parse(fields.getFirst().type(), raw, false);
                        Map<String, Object> id = new LinkedHashMap<>();
                        for (String part : raw.split(",")) {
                            String[] pair = part.split("=", 2);
                            if (pair.length != 2) continue;
                            IdField field = fields.stream().filter(f -> normalize(f.name()).equals(normalize(pair[0]))).findFirst().orElse(null);
                            if (field != null) id.put(field.name(), parse(field.type(), pair[1], false));
                        }
                        if (id.size() != fields.size()) {
                            throw new IllegalArgumentException("ID compuesto incompleto. Usa campo=valor separado por comas.");
                        }
                        return id;
                    }

                    private static void validateCreateRequired(EntityMeta entity, Map<String, Object> values) {
                        for (FieldMeta field : entity.fields()) {
                            if (field.requiredOnCreate() && field.createWritable() && !values.containsKey(field.apiName())) {
                                throw new IllegalArgumentException("Falta el campo obligatorio " + field.apiName() + " para crear " + entity.logicalName() + ".");
                            }
                        }
                        for (RelationMeta relation : entity.relations()) {
                            if (!relation.many() && !relation.optional() && !values.containsKey(relation.requestField())) {
                                throw new IllegalArgumentException("Falta la relacion obligatoria " + relation.name() + ".");
                            }
                        }
                    }

                    private static void requireCapability(EntityMeta entity, Intent intent) {
                        String capability = switch (intent) {
                            case QUERY -> "LIST";
                            case COUNT -> "COUNT";
                            case GET -> "GET";
                            case CREATE -> "CREATE";
                            case UPDATE, SET_RELATION, ADD_RELATION, REMOVE_RELATION -> "UPDATE";
                            case DELETE -> "DELETE";
                        };
                        if (!entity.capabilities().contains(capability)) {
                            throw new IllegalArgumentException(entity.logicalName() + " no permite la capacidad " + capability + ".");
                        }
                    }

                    public static String summary(Command command) {
                        EntityMeta entity = entityByCode(command.entity());
                        StringBuilder text = new StringBuilder();
                        text.append(switch (command.intent()) {
                            case QUERY -> "Consultar "; case COUNT -> "Contar "; case GET -> "Ver "; case CREATE -> "Crear ";
                            case UPDATE -> "Actualizar "; case DELETE -> "Eliminar "; case SET_RELATION -> "Asignar relacion en ";
                            case ADD_RELATION -> "Agregar relacion en "; case REMOVE_RELATION -> "Quitar relacion en ";
                        }).append(entity.logicalName());
                        if (!command.selector().isEmpty()) text.append(" | selector: ").append(command.selector());
                        if (!command.values().isEmpty()) {
                            Map<String, Object> safe = new LinkedHashMap<>();
                            command.values().forEach((key, value) -> {
                                FieldMeta field = findField(entity, key);
                                safe.put(key, field != null && field.sensitive() ? "***" : value);
                            });
                            text.append(" | valores: ").append(safe);
                        }
                        if (command.relation() != null) text.append(" | relacion: ").append(command.relation());
                        return text.toString();
                    }

                    public static String catalogPrompt() {
                        StringBuilder out = new StringBuilder();
                        for (EntityMeta entity : ENTITIES) {
                            out.append("\\n- ").append(entity.logicalName()).append(" [code=").append(entity.codeName()).append("] capacidades=").append(entity.capabilities());
                            out.append("\\n  campos: ");
                            for (FieldMeta field : entity.fields()) {
                                if (!field.sensitive()) out.append(field.apiName()).append('(').append(field.type()).append("), ");
                                else out.append(field.apiName()).append("(SENSITIVE), ");
                            }
                            if (!entity.relations().isEmpty()) {
                                out.append("\\n  relaciones: ");
                                for (RelationMeta relation : entity.relations()) out.append(relation.name()).append('(').append(relation.kind()).append("->").append(relation.targetEntity()).append("), ");
                            }
                        }
                        return out.toString();
                    }

                    public static List<String> entityCodes() { return ENTITIES.stream().map(EntityMeta::codeName).toList(); }
                    public static List<String> fieldNames() {
                        Set<String> names = new LinkedHashSet<>();
                        ENTITIES.forEach(e -> { e.fields().forEach(f -> names.add(f.apiName())); e.relations().forEach(r -> names.add(r.requestField())); });
                        return List.copyOf(names);
                    }
                    public static List<String> relationNames() {
                        Set<String> names = new LinkedHashSet<>();
                        ENTITIES.forEach(e -> e.relations().forEach(r -> names.add(r.name())));
                        return List.copyOf(names);
                    }

                    private static <K, V> Map<K, V> copy(Map<K, V> source) {
                        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(source));
                    }

                    private static FieldMeta findField(EntityMeta entity, String reference) {
                        String wanted = normalize(reference);
                        return entity.fields().stream().filter(f -> wanted.equals(normalize(f.logicalName())) || wanted.equals(normalize(f.apiName()))).findFirst().orElse(null);
                    }
                    private static RelationMeta findRelation(EntityMeta entity, String reference) {
                        String wanted = normalize(reference);
                        return entity.relations().stream().filter(r -> wanted.equals(normalize(r.name())) || wanted.equals(normalize(r.requestField()))).findFirst().orElse(null);
                    }

                    private static Object parse(String type, String raw, boolean nullable) {
                        if (isNull(raw)) {
                            if (!nullable) throw new IllegalArgumentException("El valor no puede ser null.");
                            return null;
                        }
                        String value = raw == null ? "" : raw.trim();
                        try {
                            return switch (type) {
                                case "STRING" -> value;
                                case "INTEGER" -> Integer.valueOf(value);
                                case "LONG" -> Long.valueOf(value);
                                case "DECIMAL" -> new BigDecimal(value);
                                case "BOOLEAN" -> {
                                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value))
                                        throw new IllegalArgumentException("Booleano invalido: " + value);
                                    yield Boolean.valueOf(value);
                                }
                                case "DATE" -> LocalDate.parse(value).toString();
                                case "DATETIME" -> LocalDateTime.parse(value).toString();
                                case "UUID" -> UUID.fromString(value).toString();
                                default -> throw new IllegalArgumentException("Tipo no soportado: " + type);
                            };
                        } catch (RuntimeException exception) {
                            if (exception instanceof IllegalArgumentException && exception.getMessage() != null && exception.getMessage().startsWith("Booleano")) throw exception;
                            throw new IllegalArgumentException("Valor invalido para " + type + ": " + value, exception);
                        }
                    }
                    private static boolean isNull(String raw) {
                        if (raw == null) return true;
                        String v = normalize(raw);
                        return "null".equals(v) || "nulo".equals(v) || "ninguno".equals(v);
                    }
                    private static String trim(String value) { return value == null ? "" : value.trim(); }
                    private static String normalize(String value) {
                        if (value == null) return "";
                        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\\\p{M}+", "");
                        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
                    }
                }
                """;
        return template(source.replace("__INIT__", init.toString()), pkg);
    }

    private String llama(String pkg) {
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

    private String whisper(String pkg) {
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

    private String executor(String pkg) {
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
                            case CREATE -> request("POST", entity.endpoint(), Map.of(), command.values(), authorization);
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
                        body.putAll(command.values());
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

    private String service(String pkg) {
        return template("""
                package __PACKAGE__;

                import static __PACKAGE__.GeneratedAssistantTypes.*;

                import java.time.Duration;
                import java.time.Instant;
                import java.util.Map;
                import java.util.UUID;
                import java.util.concurrent.ConcurrentHashMap;
                import org.springframework.stereotype.Service;
                import org.springframework.web.multipart.MultipartFile;
                import tools.jackson.databind.JsonNode;

                @Service
                public class GeneratedAssistantService {
                    private static final Duration PREVIEW_TTL = Duration.ofMinutes(10);
                    private final GeneratedAssistantLlamaGateway llama;
                    private final GeneratedAssistantWhisperGateway whisper;
                    private final GeneratedAssistantHttpExecutor executor;
                    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

                    public GeneratedAssistantService(GeneratedAssistantLlamaGateway llama, GeneratedAssistantWhisperGateway whisper,
                            GeneratedAssistantHttpExecutor executor) {
                        this.llama = llama; this.whisper = whisper; this.executor = executor;
                    }

                    public PlanResponse plan(String text, String authorization) { return planText(text, authorization, "TEXT"); }
                    public PlanResponse voice(MultipartFile audio, String authorization) {
                        String transcript = whisper.transcribe(audio);
                        return planText(transcript, authorization, "VOICE");
                    }

                    private PlanResponse planText(String text, String authorization, String source) {
                        String input = text == null ? "" : text.trim();
                        if (input.isBlank()) throw new IllegalArgumentException("Escribe o dicta una instruccion.");
                        if (input.length() > 1000) throw new IllegalArgumentException("La instruccion supera 1000 caracteres.");
                        cleanup();
                        Intent intent = llama.route(input);
                        Command command = GeneratedAssistantMetadata.resolve(intent, llama.command(input, intent));
                        String summary = GeneratedAssistantMetadata.summary(command);
                        if (!intent.mutating()) {
                            JsonNode result = executor.execute(command, authorization);
                            return new PlanResponse(source, input, intent.name(), summary, false, null, result);
                        }
                        String token = UUID.randomUUID().toString();
                        pending.put(token, new Pending(command, binding(authorization), Instant.now().plus(PREVIEW_TTL)));
                        return new PlanResponse(source, input, intent.name(), summary, true, token, null);
                    }

                    public PlanResponse apply(String previewToken, String authorization) {
                        cleanup();
                        if (previewToken == null || previewToken.isBlank()) throw new IllegalArgumentException("previewToken es obligatorio.");
                        Pending item = pending.remove(previewToken);
                        if (item == null || item.expiresAt().isBefore(Instant.now())) throw new IllegalArgumentException("El preview expiro o ya fue consumido.");
                        if (!item.authorizationBinding().equals(binding(authorization))) throw new IllegalArgumentException("El preview pertenece a otra sesion.");
                        JsonNode result = executor.execute(item.command(), authorization);
                        return new PlanResponse("APPLY", "", item.command().intent().name(), GeneratedAssistantMetadata.summary(item.command()), false, null, result);
                    }

                    private void cleanup() { Instant now = Instant.now(); pending.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now)); }
                    private String binding(String authorization) { return authorization == null ? "" : authorization; }
                    private record Pending(Command command, String authorizationBinding, Instant expiresAt) { }
                }
                """, pkg);
    }

    private String controller(String pkg) {
        return template("""
                package __PACKAGE__;

                import static __PACKAGE__.GeneratedAssistantTypes.*;

                import java.util.Map;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.MediaType;
                import org.springframework.web.bind.annotation.ExceptionHandler;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.PostMapping;
                import org.springframework.web.bind.annotation.RequestBody;
                import org.springframework.web.bind.annotation.RequestHeader;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.ResponseStatus;
                import org.springframework.web.bind.annotation.RestController;
                import org.springframework.web.multipart.MultipartFile;

                @RestController
                @RequestMapping("/api/assistant")
                public class GeneratedAssistantController {
                    private final GeneratedAssistantService service;
                    public GeneratedAssistantController(GeneratedAssistantService service) { this.service = service; }

                    @PostMapping("/plan")
                    public PlanResponse plan(@RequestBody PlanRequest request,
                            @RequestHeader(name = "Authorization", required = false) String authorization) {
                        return service.plan(request == null ? null : request.text(), authorization);
                    }

                    @PostMapping(value = "/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
                    public PlanResponse voice(@RequestParam("audio") MultipartFile audio,
                            @RequestHeader(name = "Authorization", required = false) String authorization) {
                        return service.voice(audio, authorization);
                    }

                    @PostMapping("/apply")
                    public PlanResponse apply(@RequestBody ApplyRequest request,
                            @RequestHeader(name = "Authorization", required = false) String authorization) {
                        return service.apply(request == null ? null : request.previewToken(), authorization);
                    }

                    @GetMapping("/capabilities")
                    public Map<String, Object> capabilities() {
                        return Map.of("text", true, "voice", true, "intents", java.util.Arrays.stream(Intent.values()).map(Enum::name).toList());
                    }

                    @ExceptionHandler(IllegalArgumentException.class)
                    @ResponseStatus(HttpStatus.BAD_REQUEST)
                    public Map<String, String> badRequest(IllegalArgumentException exception) {
                        return Map.of("error", "ASSISTANT_PLAN_INVALID", "message", exception.getMessage() == null ? "Comando invalido." : exception.getMessage());
                    }

                    @ExceptionHandler(IllegalStateException.class)
                    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    public Map<String, String> unavailable(IllegalStateException exception) {
                        return Map.of("error", "ASSISTANT_RUNTIME_UNAVAILABLE", "message", exception.getMessage() == null ? "Runtime local no disponible." : exception.getMessage());
                    }
                }
                """, pkg);
    }

    private String template(String text, String pkg) { return text.replace("__PACKAGE__", pkg); }
    private String j(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
