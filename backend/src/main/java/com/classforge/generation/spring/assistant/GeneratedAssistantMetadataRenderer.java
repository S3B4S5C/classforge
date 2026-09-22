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
                    public record RouteDecision(Intent intent, String entity) { }
                    public record RelationValue(String relation, Map<String, Object> selector, String spokenValue) {
                        public RelationValue {
                            selector = Map.copyOf(selector == null ? Map.of() : selector);
                            spokenValue = spokenValue == null ? "" : spokenValue;
                        }
                    }

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
                            Map<String, RelationValue> relationValues,
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
                import java.time.Instant;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.time.OffsetDateTime;
                import java.time.ZoneId;
                import java.time.format.DateTimeFormatter;
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
                        ResolvedValues resolvedValues = values(entity, raw.values(), intent);
                        Map<String, Object> values = resolvedValues.scalars();
                        Map<String, RelationValue> relationValues = resolvedValues.relations();
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
                        if ((intent == Intent.CREATE || intent == Intent.UPDATE) && values.isEmpty() && relationValues.isEmpty()) {
                            throw new IllegalArgumentException("La operacion necesita valores a escribir.");
                        }
                        if (intent == Intent.CREATE) validateCreateRequired(entity, values, relationValues);

                        return new Command(intent, entity.codeName(), trim(raw.query()), copy(filters), copy(selector),
                                copy(values), copy(relationValues), relation == null ? null : relation.name(), copy(targetSelector));
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

                    private record ResolvedValues(Map<String, Object> scalars, Map<String, RelationValue> relations) { }

                    private static ResolvedValues values(EntityMeta entity, List<FieldValue> inputs, Intent intent) {
                        Map<String, Object> scalars = new LinkedHashMap<>();
                        Map<String, RelationValue> relations = new LinkedHashMap<>();
                        for (FieldValue input : inputs == null ? List.<FieldValue>of() : inputs) {
                            FieldMeta scalar = findField(entity, input.field());
                            if (scalar != null) {
                                boolean writable = intent == Intent.CREATE ? scalar.createWritable() : scalar.updateWritable();
                                if (!writable) throw new IllegalArgumentException("El campo " + scalar.apiName() + " no es escribible para " + intent + ".");
                                scalars.put(scalar.apiName(), parse(scalar.type(), input.value(), scalar.nullable()));
                                continue;
                            }
                            RelationMeta relation = findRelation(entity, input.field());
                            if (relation == null) throw new IllegalArgumentException("Campo/relacion desconocido: " + input.field());
                            if (relation.many()) {
                                throw new IllegalArgumentException("Las relaciones many-to-many se modifican con ADD_RELATION/REMOVE_RELATION.");
                            }
                            if (isNull(input.value())) {
                                if (!relation.optional()) throw new IllegalArgumentException("La relacion " + relation.name() + " es obligatoria y no admite null.");
                                scalars.put(relation.requestField(), null);
                                continue;
                            }
                            EntityMeta target = entityByCode(relation.targetEntity());
                            FieldMeta humanField = preferredSelectorField(target);
                            if (shouldUseDirectIdentifier(relation.targetIdentifier(), input.value(), humanField == null)) {
                                scalars.put(relation.requestField(), parseRelationId(relation.targetIdentifier(), input.value(), relation.optional()));
                                continue;
                            }
                            if (humanField == null) {
                                throw new IllegalArgumentException("La relacion " + relation.name()
                                        + " necesita el identificador de " + target.logicalName() + ".");
                            }
                            Object parsed = parse(humanField.type(), input.value(), false);
                            relations.put(relation.requestField(), new RelationValue(
                                    relation.name(), Map.of(humanField.apiName(), parsed), trim(input.value())));
                        }
                        return new ResolvedValues(scalars, relations);
                    }

                    private static boolean shouldUseDirectIdentifier(List<IdField> fields, String raw, boolean noHumanField) {
                        if (fields.size() > 1) return raw != null && raw.contains("=");
                        if (fields.isEmpty()) return false;
                        String type = fields.getFirst().type();
                        if ("STRING".equals(type)) return noHumanField;
                        String value = raw == null ? "" : raw.trim();
                        return switch (type) {
                            case "UUID" -> {
                                try { UUID.fromString(value); yield true; }
                                catch (RuntimeException ignored) { yield false; }
                            }
                            case "INTEGER", "LONG" -> value.matches("[-+]?\\\\d+");
                            case "DECIMAL" -> value.matches("[-+]?(?:\\\\d+(?:\\\\.\\\\d*)?|\\\\.\\\\d+)");
                            default -> noHumanField;
                        };
                    }

                    private static FieldMeta preferredSelectorField(EntityMeta entity) {
                        return entity.fields().stream()
                                .filter(field -> !field.sensitive() && !field.identifier() && field.filterable())
                                .sorted(java.util.Comparator.comparingInt(GeneratedAssistantMetadata::selectorPriority)
                                        .thenComparing(FieldMeta::apiName))
                                .findFirst().orElse(null);
                    }

                    private static int selectorPriority(FieldMeta field) {
                        String name = normalize(field.logicalName() + " " + field.apiName());
                        if (name.contains("nombrecompleto") || name.contains("fullname")) return 0;
                        if (name.equals("nombre") || name.equals("name") || name.endsWith("nombre") || name.endsWith("name")) return 1;
                        if (name.contains("titulo") || name.contains("title")) return 2;
                        if (name.contains("razonsocial") || name.contains("displayname")) return 3;
                        if (name.contains("codigo") || name.contains("code")) return 4;
                        if (name.contains("username") || name.contains("usuario")) return 5;
                        if (name.contains("email") || name.contains("correo")) return 6;
                        if (name.contains("descripcion") || name.contains("description")) return 7;
                        if ("STRING".equals(field.type())) return 20;
                        return 50;
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

                    private static void validateCreateRequired(EntityMeta entity, Map<String, Object> values, Map<String, RelationValue> relationValues) {
                        for (FieldMeta field : entity.fields()) {
                            if (field.requiredOnCreate() && field.createWritable() && !values.containsKey(field.apiName())) {
                                throw new IllegalArgumentException("Falta el campo obligatorio " + field.apiName() + " para crear " + entity.logicalName() + ".");
                            }
                        }
                        for (RelationMeta relation : entity.relations()) {
                            if (!relation.many() && !relation.optional()
                                    && !values.containsKey(relation.requestField()) && !relationValues.containsKey(relation.requestField())) {
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
                        if (!command.relationValues().isEmpty()) {
                            Map<String, String> human = new LinkedHashMap<>();
                            command.relationValues().forEach((key, value) -> {
                                RelationMeta relationValue = findRelation(entity, key);
                                human.put(relationValue == null ? key : relationValue.name(), value.spokenValue());
                            });
                            text.append(" | relaciones: ").append(human);
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

                    public static String catalogPrompt(String entityCode, Intent intent) {
                        EntityMeta entity = entityByCode(entityCode);
                        StringBuilder out = new StringBuilder();
                        out.append("\\n- ").append(entity.logicalName()).append(" [code=").append(entity.codeName()).append("] capacidades=").append(entity.capabilities());
                        out.append("\\n  campos escalares: ");
                        for (FieldMeta field : entity.fields()) {
                            if (field.sensitive()) continue;
                            boolean writable = intent == Intent.CREATE ? field.createWritable() : intent == Intent.UPDATE && field.updateWritable();
                            out.append(field.apiName()).append('(').append(field.type());
                            if (writable) out.append(",writable");
                            if (field.filterable()) out.append(",filterable");
                            if ("DATE".equals(field.type()) || "DATETIME".equals(field.type())) out.append(",acepta-expresion-temporal");
                            out.append("), ");
                        }
                        if (!entity.relations().isEmpty()) {
                            out.append("\\n  relaciones: ");
                            for (RelationMeta relation : entity.relations()) {
                                EntityMeta target = entityByCode(relation.targetEntity());
                                FieldMeta preferred = preferredSelectorField(target);
                                out.append(relation.name()).append(" [requestField=").append(relation.requestField())
                                        .append(", ").append(relation.kind()).append(" -> ").append(target.logicalName());
                                if (preferred != null) out.append(", referencia humana por ").append(preferred.apiName());
                                out.append("], ");
                            }
                        }
                        return out.toString();
                    }

                    public static List<String> filterFieldNames(String entityCode) {
                        return entityByCode(entityCode).fields().stream()
                                .filter(field -> !field.sensitive() && (field.filterable() || field.searchable()))
                                .map(FieldMeta::apiName).distinct().toList();
                    }

                    public static List<String> selectorFieldNames(String entityCode) {
                        return entityByCode(entityCode).fields().stream()
                                .filter(field -> !field.sensitive() && (field.filterable() || field.identifier()))
                                .map(FieldMeta::apiName).distinct().toList();
                    }

                    public static List<String> valueFieldNames(String entityCode, Intent intent) {
                        if (intent != Intent.CREATE && intent != Intent.UPDATE) return List.of();
                        EntityMeta entity = entityByCode(entityCode);
                        Set<String> names = new LinkedHashSet<>();
                        entity.fields().stream()
                                .filter(field -> !field.sensitive())
                                .filter(field -> intent == Intent.CREATE ? field.createWritable() : field.updateWritable())
                                .map(FieldMeta::apiName).forEach(names::add);
                        entity.relations().stream().filter(relation -> !relation.many())
                                .map(RelationMeta::name).forEach(names::add);
                        return List.copyOf(names);
                    }

                    public static List<String> relationNames(String entityCode) {
                        return entityByCode(entityCode).relations().stream().map(RelationMeta::name).distinct().toList();
                    }

                    public static List<String> targetSelectorFieldNames(String entityCode) {
                        Set<String> names = new LinkedHashSet<>();
                        for (RelationMeta relation : entityByCode(entityCode).relations()) {
                            for (FieldMeta field : entityByCode(relation.targetEntity()).fields()) {
                                if (!field.sensitive() && (field.filterable() || field.identifier())) names.add(field.apiName());
                            }
                        }
                        return List.copyOf(names);
                    }

                    public static String speechPrompt() {
                        LinkedHashSet<String> terms = new LinkedHashSet<>(List.of(
                                "crear", "crea", "listar", "buscar", "ver", "contar", "actualizar", "editar", "eliminar",
                                "relacion", "asignar", "agregar", "quitar", "nombre", "codigo", "fecha", "hora",
                                "hoy", "ahora", "ayer", "manana", "anteayer", "pasado manana", "hace", "dentro de",
                                "dia", "dias", "semana", "semanas", "mes", "meses", "ano", "anos", "nacido", "nacida", "creado", "creada"));
                        for (EntityMeta entity : ENTITIES) {
                            terms.add(entity.logicalName());
                            terms.add(entity.codeName());
                            entity.aliases().forEach(terms::add);
                            entity.fields().stream().filter(field -> !field.sensitive()).forEach(field -> {
                                terms.add(field.logicalName());
                                terms.add(field.apiName());
                            });
                            entity.relations().forEach(relation -> terms.add(relation.name()));
                        }
                        StringBuilder prompt = new StringBuilder();
                        for (String term : terms) {
                            if (term == null || term.isBlank()) continue;
                            String next = term.trim();
                            int extra = (prompt.isEmpty() ? 0 : 2) + next.length();
                            if (prompt.length() + extra > 800) break;
                            if (!prompt.isEmpty()) prompt.append(", ");
                            prompt.append(next);
                        }
                        return prompt.toString();
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
                                case "DATE" -> parseDate(value).toString();
                                case "DATETIME" -> parseDateTime(value).withNano(0).toString();
                                case "UUID" -> UUID.fromString(value).toString();
                                default -> throw new IllegalArgumentException("Tipo no soportado: " + type);
                            };
                        } catch (RuntimeException exception) {
                            if (exception instanceof IllegalArgumentException && exception.getMessage() != null && exception.getMessage().startsWith("Booleano")) throw exception;
                            throw new IllegalArgumentException("Valor invalido para " + type + ": " + value, exception);
                        }
                    }

                    private static LocalDate parseDate(String value) {
                        LocalDateTime now = LocalDateTime.now().withNano(0);
                        LocalDateTime relative = relativeDateTime(value, now);
                        if (relative != null) return relative.toLocalDate();
                        LocalDate absolute = absoluteDate(value);
                        if (absolute != null) return absolute;
                        LocalDateTime dateTime = absoluteDateTime(value);
                        if (dateTime != null) return dateTime.toLocalDate();
                        throw new IllegalArgumentException("Fecha invalida: " + value);
                    }

                    private static LocalDateTime parseDateTime(String value) {
                        LocalDateTime now = LocalDateTime.now().withNano(0);
                        LocalDateTime relative = relativeDateTime(value, now);
                        if (relative != null) return relative;
                        LocalDateTime absolute = absoluteDateTime(value);
                        if (absolute != null) return absolute;
                        LocalDate date = absoluteDate(value);
                        if (date != null) return date.equals(now.toLocalDate()) ? now : date.atStartOfDay();
                        throw new IllegalArgumentException("Fecha/hora invalida: " + value);
                    }

                    private static LocalDate absoluteDate(String value) {
                        for (DateTimeFormatter formatter : List.of(
                                DateTimeFormatter.ISO_LOCAL_DATE,
                                DateTimeFormatter.ofPattern("d/M/uuuu"),
                                DateTimeFormatter.ofPattern("d-M-uuuu"))) {
                            try { return LocalDate.parse(value, formatter); }
                            catch (RuntimeException ignored) { }
                        }
                        return null;
                    }

                    private static LocalDateTime absoluteDateTime(String value) {
                        for (DateTimeFormatter formatter : List.of(
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME,
                                DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"),
                                DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"),
                                DateTimeFormatter.ofPattern("d/M/uuuu H:mm"),
                                DateTimeFormatter.ofPattern("d/M/uuuu H:mm:ss"),
                                DateTimeFormatter.ofPattern("d-M-uuuu H:mm"),
                                DateTimeFormatter.ofPattern("d-M-uuuu H:mm:ss"))) {
                            try { return LocalDateTime.parse(value, formatter); }
                            catch (RuntimeException ignored) { }
                        }
                        try { return OffsetDateTime.parse(value).toLocalDateTime(); }
                        catch (RuntimeException ignored) { }
                        try { return LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault()); }
                        catch (RuntimeException ignored) { }
                        return null;
                    }

                    private static LocalDateTime relativeDateTime(String raw, LocalDateTime now) {
                        String phrase = temporalText(raw);
                        if (phrase.isBlank()) return null;
                        if (phrase.equals("ahora") || phrase.equals("now") || phrase.equals("hoy") || phrase.equals("today")
                                || phrase.contains("fecha de hoy") || phrase.contains("dia de hoy")) return now;
                        if (phrase.equals("anteayer") || phrase.equals("antes de ayer")) return now.minusDays(2);
                        if (phrase.equals("ayer") || phrase.equals("yesterday")) return now.minusDays(1);
                        if (phrase.equals("pasado manana") || phrase.equals("day after tomorrow")) return now.plusDays(2);
                        if (phrase.equals("manana") || phrase.equals("tomorrow")) return now.plusDays(1);

                        int ago = phrase.indexOf("hace ");
                        if (ago >= 0) {
                            LocalDateTime shifted = shiftRelative(now, phrase.substring(ago + 5), -1);
                            if (shifted != null) return shifted;
                        }
                        int future = phrase.indexOf("dentro de ");
                        if (future >= 0) {
                            LocalDateTime shifted = shiftRelative(now, phrase.substring(future + 9), 1);
                            if (shifted != null) return shifted;
                        }
                        if (phrase.startsWith("en ")) {
                            LocalDateTime shifted = shiftRelative(now, phrase.substring(3), 1);
                            if (shifted != null) return shifted;
                        }
                        if (phrase.endsWith(" atras")) {
                            LocalDateTime shifted = shiftRelative(now, phrase.substring(0, phrase.length() - 6), -1);
                            if (shifted != null) return shifted;
                        }
                        return null;
                    }

                    private static LocalDateTime shiftRelative(LocalDateTime now, String expression, int direction) {
                        String[] parts = temporalText(expression).split(" ");
                        if (parts.length < 2) return null;
                        Integer amount = temporalAmount(parts[0]);
                        if (amount == null) return null;
                        long signed = (long) amount * direction;
                        String unit = parts[1];
                        if (unit.startsWith("ano") || unit.startsWith("year")) return now.plusYears(signed);
                        if (unit.startsWith("mes") || unit.startsWith("month")) return now.plusMonths(signed);
                        if (unit.startsWith("semana") || unit.startsWith("week")) return now.plusWeeks(signed);
                        if (unit.startsWith("dia") || unit.startsWith("day")) return now.plusDays(signed);
                        if (unit.startsWith("hora") || unit.startsWith("hour")) return now.plusHours(signed);
                        if (unit.startsWith("minuto") || unit.startsWith("minute")) return now.plusMinutes(signed);
                        return null;
                    }

                    private static Integer temporalAmount(String value) {
                        try { return Integer.valueOf(value); }
                        catch (RuntimeException ignored) { }
                        return switch (value) {
                            case "un", "una", "uno", "one" -> 1;
                            case "dos", "two" -> 2;
                            case "tres", "three" -> 3;
                            case "cuatro", "four" -> 4;
                            case "cinco", "five" -> 5;
                            case "seis", "six" -> 6;
                            case "siete", "seven" -> 7;
                            case "ocho", "eight" -> 8;
                            case "nueve", "nine" -> 9;
                            case "diez", "ten" -> 10;
                            case "once", "eleven" -> 11;
                            case "doce", "twelve" -> 12;
                            default -> null;
                        };
                    }

                    private static String temporalText(String value) {
                        if (value == null) return "";
                        String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\\\p{M}+", "");
                        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\\\s+", " ");
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
