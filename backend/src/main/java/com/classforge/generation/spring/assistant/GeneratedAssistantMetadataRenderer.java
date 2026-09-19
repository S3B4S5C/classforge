package com.classforge.generation.spring.assistant;

import com.classforge.generation.spring.domain.DomainManifestPlan;
import java.util.*;
import java.util.stream.Collectors;

final class GeneratedAssistantMetadataRenderer {

    String types(String pkg) {
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

    String metadata(String pkg, DomainManifestPlan manifest) {
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

    String template(String text, String pkg) { return text.replace("__PACKAGE__", pkg); }

    String j(String value) { return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
}
