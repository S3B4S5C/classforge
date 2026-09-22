package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantActionType;
import com.classforge.assistant.AssistantIntentHintResolver;
import com.classforge.project.domain.document.AssociationClassSupport;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlAttribute;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlRelationship;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class DynamicUmlToolCatalog {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");

    private final AssistantIntentHintResolver intentHintResolver;

    public DynamicUmlToolCatalog(AssistantIntentHintResolver intentHintResolver) {
        this.intentHintResolver = intentHintResolver;
    }

    public AssistantToolCatalog routingCatalog() {
        return new AssistantToolCatalog(
                List.of(definition(AssistantToolName.ROUTE_REQUEST, List.of(), List.of(), List.of())),
                Map.of(),
                Map.of(),
                Map.of()
        );
    }

    public AssistantToolCatalog buildForTools(
            ProjectDocument document,
            List<AssistantToolName> requestedTools
    ) {
        List<UmlClass> classes = document.umlModel().classes().stream()
                .sorted(Comparator.comparing(UmlClass::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<String, UUID> classIds = new LinkedHashMap<>();
        Map<UUID, String> classNamesById = new LinkedHashMap<>();
        Map<String, AssistantToolCatalog.AttributeReference> attributes = new LinkedHashMap<>();
        Map<String, AssistantToolCatalog.RelationshipReference> relationships = new LinkedHashMap<>();

        for (UmlClass umlClass : classes) {
            classIds.put(umlClass.name(), umlClass.id());
            classNamesById.put(umlClass.id(), umlClass.name());
            for (UmlAttribute attribute : umlClass.attributes()) {
                if (AssociationClassSupport.isMarker(attribute.customTypeName())) continue;
                String label = umlClass.name() + "." + attribute.name();
                attributes.put(label, new AssistantToolCatalog.AttributeReference(
                        label, umlClass.id(), umlClass.name(), attribute.id(), attribute.name()
                ));
            }
        }

        var associationAuxiliaryIds = AssociationClassSupport.auxiliaryRelationshipIds(document);
        int relationIndex = 1;
        for (UmlRelationship relationship : document.umlModel().relationships()) {
            if (associationAuxiliaryIds.contains(relationship.id())) continue;
            String source = classNamesById.get(relationship.sourceClassId());
            String target = classNamesById.get(relationship.targetClassId());
            if (source == null || target == null) continue;
            String label = "R" + relationIndex++ + ": " + source + " -> " + target + " (" + relationship.type() + ")";
            relationships.put(label, new AssistantToolCatalog.RelationshipReference(
                    label, relationship.id(), source, target, relationship.type()
            ));
        }

        List<String> classNames = List.copyOf(classIds.keySet());
        List<String> attributeLabels = List.copyOf(attributes.keySet());
        List<String> relationshipLabels = List.copyOf(relationships.keySet());
        List<AssistantToolDefinition> definitions = requestedTools.stream()
                .filter(tool -> tool != AssistantToolName.ROUTE_REQUEST && tool != AssistantToolName.FINISH_PLAN)
                .distinct()
                .map(tool -> definition(tool, classNames, attributeLabels, relationshipLabels))
                .toList();

        if (definitions.isEmpty()) {
            throw new IllegalArgumentException("La ruta native tools no contiene operaciones UML ejecutables.");
        }

        return new AssistantToolCatalog(
                definitions, Map.copyOf(classIds), Map.copyOf(attributes), Map.copyOf(relationships)
        );
    }

    public AssistantToolCatalog build(String userText, ProjectDocument document) {
        return build(userText, document, false, false);
    }

    public AssistantToolCatalog build(
            String userText,
            ProjectDocument document,
            boolean compound,
            boolean allowFinish
    ) {
        List<UmlClass> classes = document.umlModel().classes().stream()
                .sorted(Comparator.comparing(UmlClass::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        Map<String, UUID> classIds = new LinkedHashMap<>();
        Map<UUID, String> classNamesById = new LinkedHashMap<>();
        Map<String, AssistantToolCatalog.AttributeReference> attributes = new LinkedHashMap<>();
        Map<String, AssistantToolCatalog.RelationshipReference> relationships = new LinkedHashMap<>();

        for (UmlClass umlClass : classes) {
            classIds.put(umlClass.name(), umlClass.id());
            classNamesById.put(umlClass.id(), umlClass.name());
            for (UmlAttribute attribute : umlClass.attributes()) {
                if (AssociationClassSupport.isMarker(attribute.customTypeName())) continue;
                String label = umlClass.name() + "." + attribute.name();
                attributes.put(label, new AssistantToolCatalog.AttributeReference(
                        label,
                        umlClass.id(),
                        umlClass.name(),
                        attribute.id(),
                        attribute.name()
                ));
            }
        }

        var associationAuxiliaryIds = AssociationClassSupport.auxiliaryRelationshipIds(document);
        int relationIndex = 1;
        for (UmlRelationship relationship : document.umlModel().relationships()) {
            if (associationAuxiliaryIds.contains(relationship.id())) continue;
            String source = classNamesById.get(relationship.sourceClassId());
            String target = classNamesById.get(relationship.targetClassId());
            if (source == null || target == null) {
                continue;
            }
            String label = "R" + relationIndex++ + ": " + source + " -> " + target + " (" + relationship.type() + ")";
            relationships.put(label, new AssistantToolCatalog.RelationshipReference(
                    label,
                    relationship.id(),
                    source,
                    target,
                    relationship.type()
            ));
        }

        List<String> classNames = List.copyOf(classIds.keySet());
        List<String> attributeLabels = List.copyOf(attributes.keySet());
        List<String> relationshipLabels = List.copyOf(relationships.keySet());

        List<AssistantToolDefinition> definitions = new ArrayList<>();
        for (AssistantToolName toolName : selectedTools(userText, document, compound, allowFinish)) {
            definitions.add(definition(toolName, classNames, attributeLabels, relationshipLabels));
        }

        return new AssistantToolCatalog(
                List.copyOf(definitions),
                Map.copyOf(classIds),
                Map.copyOf(attributes),
                Map.copyOf(relationships)
        );
    }

    private List<AssistantToolName> selectedTools(
            String userText,
            ProjectDocument document,
            boolean compound,
            boolean allowFinish
    ) {
        if (compound) {
            List<AssistantToolName> result = new ArrayList<>();
            String text = normalize(userText);
            if (isAssociationClassCreationRequest(text)) {
                result.add(AssistantToolName.CREATE_ASSOCIATION_CLASS);
            } else if (containsAny(text, "crea", "crear", "nueva clase", "clase llamada", "necesito una clase")) {
                result.add(AssistantToolName.CREATE_CLASS);
            }
            if (containsAny(text, "agrega", "agregale", "anade", "atributo", "campo", "ponle")) {
                result.add(AssistantToolName.ADD_ATTRIBUTES);
            }
            if (containsAny(text, "relacion", "relaciona", "conecta", "asocia", "hereda", "agrupa", "compuesta")) {
                result.add(createRelationshipTool(userText));
            }
            if (containsAny(text, "renombra", "cambia el nombre", "ahora se llama")) {
                result.add(AssistantToolName.RENAME_CLASS);
            }
            if (result.isEmpty()) {
                result.addAll(List.of(
                        AssistantToolName.CREATE_CLASS,
                        AssistantToolName.CREATE_ASSOCIATION_CLASS,
                        AssistantToolName.ADD_ATTRIBUTES,
                        AssistantToolName.CREATE_ASSOCIATION,
                        AssistantToolName.RENAME_CLASS
                ));
            }
            if (allowFinish) {
                result.add(AssistantToolName.FINISH_PLAN);
            }
            return result.stream().distinct().toList();
        }
        if (isAssociationClassCreationRequest(normalize(userText))) {
            return List.of(AssistantToolName.CREATE_ASSOCIATION_CLASS);
        }

        Optional<AssistantIntentHintResolver.IntentHint> hint = intentHintResolver.resolve(userText, document);
        if (hint.isEmpty()) {
            return java.util.Arrays.stream(AssistantToolName.values())
                    .filter(tool -> tool != AssistantToolName.FINISH_PLAN && tool != AssistantToolName.ROUTE_REQUEST)
                    .toList();
        }

        return switch (hint.get().actionType()) {
            case CREATE_CLASS -> List.of(AssistantToolName.CREATE_CLASS);
            case CREATE_ASSOCIATION_CLASS -> List.of(AssistantToolName.CREATE_ASSOCIATION_CLASS);
            case RENAME_CLASS -> List.of(AssistantToolName.RENAME_CLASS);
            case DELETE_CLASS -> List.of(AssistantToolName.DELETE_CLASS);
            case ADD_ATTRIBUTES -> List.of(AssistantToolName.ADD_ATTRIBUTES);
            case UPDATE_ATTRIBUTE -> isAttributeRename(userText)
                    ? List.of(AssistantToolName.RENAME_ATTRIBUTE)
                    : List.of(AssistantToolName.UPDATE_ATTRIBUTE_PROPERTIES);
            case DELETE_ATTRIBUTE -> List.of(AssistantToolName.DELETE_ATTRIBUTE);
            case CREATE_RELATIONSHIP -> List.of(createRelationshipTool(userText));
            case UPDATE_RELATIONSHIP -> isMultiplicityUpdate(userText)
                    ? List.of(AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY)
                    : List.of(AssistantToolName.CHANGE_RELATIONSHIP_TYPE);
            case DELETE_RELATIONSHIP -> List.of(AssistantToolName.DELETE_RELATIONSHIP);
        };
    }

    private AssistantToolName createRelationshipTool(String userText) {
        String text = normalize(userText);
        if (containsAny(text, "hereda", "heredar", "generalizacion", "generalization")) {
            return AssistantToolName.CREATE_GENERALIZATION;
        }
        if (containsAny(text, "compuesta", "compuesto", "composicion", "composition")) {
            return AssistantToolName.CREATE_COMPOSITION;
        }
        if (containsAny(text, "agrupa", "agrupar", "agregacion", "aggregation")) {
            return AssistantToolName.CREATE_AGGREGATION;
        }
        return AssistantToolName.CREATE_ASSOCIATION;
    }

    private boolean isAssociationClassCreationRequest(String text) {
        String associationClass =
                "(?:clase de asociacion|clase asociativa|clase intermedia|association class|associationclass)";
        if (!Pattern.compile("\\b" + associationClass + "\\b").matcher(text).find()) {
            return false;
        }
        return Pattern.compile(
                "(?:"
                        + "\\b(?:convierte|convertir|transforma|transformar)\\b.*\\b(?:en|como)\\b.*\\b" + associationClass + "\\b"
                        + "|\\b(?:crea|crear|agrega|anade)\\s+(?:una\\s+)?(?:nueva\\s+)?" + associationClass + "\\b"
                        + "|\\bcrea\\b.*\\bcomo\\s+(?:una\\s+)?" + associationClass + "\\b"
                        + "|\\b(?:haz|hacer)\\s+que\\b.*\\b(?:sea|como)\\b.*\\b" + associationClass + "\\b"
                        + "|\\bvuelve\\b.*\\b" + associationClass + "\\b"
                        + ")"
        ).matcher(text).find();
    }

    private boolean isAttributeRename(String userText) {
        String text = normalize(userText);
        return containsAny(text, "renombra", "renombrar", "renonbra", "ahora se llama", "se llama", " por ", " a ")
                && !containsAny(text, "tipo", "visibilidad", "nullable", "identificador");
    }

    private boolean isMultiplicityUpdate(String userText) {
        String text = normalize(userText);
        return text.contains("..")
                || containsAny(text, "multiplicidad", "muchas", "muchos", "cero", "lado");
    }

    private AssistantToolDefinition definition(
            AssistantToolName tool,
            List<String> classNames,
            List<String> attributeLabels,
            List<String> relationshipLabels
    ) {
        return switch (tool) {
            case ROUTE_REQUEST -> new AssistantToolDefinition(
                    tool,
                    "Route the user request to the ordered UML tools required to satisfy it. Return one step per requested semantic operation, in dependency order.",
                    objectSchema(Map.of(
                            "steps", routeStepsSchema()
                    ), List.of("steps"))
            );
            case CREATE_CLASS -> new AssistantToolDefinition(
                    tool,
                    "Create one new UML class. The name is a NEW identifier: copy it literally from the user; never autocorrect spelling.",
                    objectSchema(Map.of(
                            "name", stringSchema("New class name literally requested by the user.")
                    ), List.of("name"))
            );
            case CREATE_ASSOCIATION_CLASS -> new AssistantToolDefinition(
                    tool,
                    "Convert one existing UML association/aggregation/composition into an UML AssociationClass. The selected relationship stays semantically between its two endpoint classes and the new class carries attributes of that relationship.",
                    objectSchema(Map.of(
                            "existing_relationship_ref", existingEnumSchema("Existing relationship that receives the association class.", relationshipLabels),
                            "name", stringSchema("New association-class name literally requested by the user."),
                            "attributes", arraySchema(attributeCreateSchema())
                    ), List.of("existing_relationship_ref", "name"))
            );
            case RENAME_CLASS -> new AssistantToolDefinition(
                    tool,
                    "Rename one existing UML class. existing_class is canonical; new_name is a NEW identifier copied literally from the user.",
                    objectSchema(Map.of(
                            "existing_class", existingEnumSchema("Existing class to rename.", classNames),
                            "new_name", stringSchema("New class name literally requested by the user.")
                    ), List.of("existing_class", "new_name"))
            );
            case DELETE_CLASS -> new AssistantToolDefinition(
                    tool,
                    "Delete one existing UML class explicitly named as the object being deleted.",
                    objectSchema(Map.of(
                            "existing_class", existingEnumSchema("Existing class to delete.", classNames)
                    ), List.of("existing_class"))
            );
            case ADD_ATTRIBUTES -> new AssistantToolDefinition(
                    tool,
                    "Add one or more NEW attributes to one existing UML class.",
                    objectSchema(Map.of(
                            "existing_class", existingEnumSchema("Existing class that receives the attributes.", classNames),
                            "attributes", arraySchema(attributeCreateSchema())
                    ), List.of("existing_class", "attributes"))
            );
            case RENAME_ATTRIBUTE -> new AssistantToolDefinition(
                    tool,
                    "Rename exactly one existing UML attribute. Do not change its type, visibility, nullable or identifier flags.",
                    objectSchema(Map.of(
                            "existing_attribute_ref", existingEnumSchema("Existing attribute in Class.attribute format.", attributeLabels),
                            "new_name", stringSchema("NEW attribute name copied literally from the user; return only the attribute name, never Class.attribute.")
                    ), List.of("existing_attribute_ref", "new_name"))
            );
            case UPDATE_ATTRIBUTE_PROPERTIES -> new AssistantToolDefinition(
                    tool,
                    "Update properties of one existing UML attribute without renaming it. Include only properties explicitly requested.",
                    objectSchema(Map.ofEntries(
                            Map.entry("existing_attribute_ref", existingEnumSchema("Existing attribute in Class.attribute format.", attributeLabels)),
                            Map.entry("data_type", enumSchema("Optional UML data type.", dataTypes())),
                            Map.entry("custom_type_name", stringSchema("Name of CUSTOM data type when data_type=CUSTOM.")),
                            Map.entry("visibility", enumSchema("Optional UML visibility.", visibilities())),
                            Map.entry("nullable", booleanSchema("Optional nullable flag.")),
                            Map.entry("identifier", booleanSchema("Optional identifier flag."))
                    ), List.of("existing_attribute_ref"))
            );
            case DELETE_ATTRIBUTE -> new AssistantToolDefinition(
                    tool,
                    "Delete exactly one existing UML attribute. The selected attribute itself must be mentioned by the user; do not substitute another attribute.",
                    objectSchema(Map.of(
                            "existing_attribute_ref", existingEnumSchema("Existing attribute in Class.attribute format.", attributeLabels)
                    ), List.of("existing_attribute_ref"))
            );
            case CREATE_ASSOCIATION -> relationshipCreateDefinition(
                    tool,
                    "Create an ASSOCIATION between two existing classes.",
                    "source_class",
                    "Association source class explicitly mentioned by the user.",
                    "target_class",
                    "Association target class explicitly mentioned by the user.",
                    classNames
            );
            case CREATE_AGGREGATION -> relationshipCreateDefinition(
                    tool,
                    "Create an AGGREGATION. whole_class is the aggregate/whole; part_class is the grouped part.",
                    "whole_class",
                    "Existing aggregate/whole class.",
                    "part_class",
                    "Existing grouped/part class.",
                    classNames
            );
            case CREATE_COMPOSITION -> relationshipCreateDefinition(
                    tool,
                    "Create a COMPOSITION. whole_class is the composite/whole; part_class is the contained part.",
                    "whole_class",
                    "Existing composite/whole class.",
                    "part_class",
                    "Existing contained/part class.",
                    classNames
            );
            case CREATE_GENERALIZATION -> relationshipCreateDefinition(
                    tool,
                    "Create a GENERALIZATION. subclass is the class that inherits; superclass is the parent class it inherits from.",
                    "subclass",
                    "Existing subclass that inherits.",
                    "superclass",
                    "Existing superclass/parent.",
                    classNames
            );
            case SET_RELATIONSHIP_MULTIPLICITY -> new AssistantToolDefinition(
                    tool,
                    "Change multiplicity on one endpoint of an existing relationship. end_class names the endpoint whose multiplicity changes.",
                    objectSchema(Map.of(
                            "existing_relationship_ref", existingEnumSchema("Existing relationship from the current project.", relationshipLabels),
                            "end_class", existingEnumSchema("Endpoint class whose multiplicity is being changed; it must be one endpoint of the selected relationship.", classNames),
                            "lower", lowerIntegerSchema("Lower multiplicity. Must be 0 or greater. For many/muchas/muchos without an explicit minimum, use 0."),
                            "upper", upperIntegerSchema("Upper multiplicity. Use -1 for * (unbounded).")
                    ), List.of("existing_relationship_ref", "end_class", "lower", "upper"))
            );
            case CHANGE_RELATIONSHIP_TYPE -> new AssistantToolDefinition(
                    tool,
                    "Change only the type of one existing UML relationship.",
                    objectSchema(Map.of(
                            "existing_relationship_ref", existingEnumSchema("Existing relationship from the current project.", relationshipLabels),
                            "relationship_type", enumSchema("New relationship type.", relationshipTypes())
                    ), List.of("existing_relationship_ref", "relationship_type"))
            );
            case DELETE_RELATIONSHIP -> new AssistantToolDefinition(
                    tool,
                    "Delete one existing UML relationship whose endpoints are referenced by the user.",
                    objectSchema(Map.of(
                            "existing_relationship_ref", existingEnumSchema("Existing relationship reference from the current project.", relationshipLabels)
                    ), List.of("existing_relationship_ref"))
            );
            case FINISH_PLAN -> new AssistantToolDefinition(
                    tool,
                    "Finish a compound UML plan only after every requested change has already been planned successfully.",
                    objectSchema(Map.of(), List.of())
            );
        };
    }

    private AssistantToolDefinition relationshipCreateDefinition(
            AssistantToolName tool,
            String description,
            String firstName,
            String firstDescription,
            String secondName,
            String secondDescription,
            List<String> classNames
    ) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(firstName, existingEnumSchema(firstDescription, classNames));
        properties.put(secondName, existingEnumSchema(secondDescription, classNames));
        properties.put("source_lower", lowerIntegerSchema("Optional source/whole/subclass lower multiplicity; omit unless explicitly requested."));
        properties.put("source_upper", upperIntegerSchema("Optional source/whole/subclass upper multiplicity; -1 means *; omit unless explicitly requested."));
        properties.put("target_lower", lowerIntegerSchema("Optional target/part/superclass lower multiplicity; omit unless explicitly requested."));
        properties.put("target_upper", upperIntegerSchema("Optional target/part/superclass upper multiplicity; -1 means *; omit unless explicitly requested."));
        return new AssistantToolDefinition(tool, description, objectSchema(properties, List.of(firstName, secondName)));
    }

    private Map<String, Object> routeStepsSchema() {
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "string");
        items.put("enum", java.util.Arrays.stream(AssistantToolName.values())
                .filter(tool -> tool != AssistantToolName.ROUTE_REQUEST && tool != AssistantToolName.FINISH_PLAN)
                .map(AssistantToolName::wireName)
                .toList());

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("description", "Ordered UML tool names. Use exactly one step for a simple request. For compound requests include each requested operation once and put create_class before operations that reference the newly created class.");
        schema.put("items", items);
        schema.put("minItems", 1);
        schema.put("maxItems", 8);
        return schema;
    }

    private Map<String, Object> attributeCreateSchema() {
        return objectSchema(Map.ofEntries(
                Map.entry("name", stringSchema("New attribute name exactly as requested.")),
                Map.entry("data_type", enumSchema("UML data type when known.", dataTypes())),
                Map.entry("custom_type_name", stringSchema("Custom type name when data_type=CUSTOM.")),
                Map.entry("visibility", enumSchema("UML visibility when requested.", visibilities())),
                Map.entry("nullable", booleanSchema("Nullable flag when requested.")),
                Map.entry("identifier", booleanSchema("Identifier flag when requested.")),
                Map.entry("type_source", enumSchema("How the type was obtained.", List.of("DEFAULT", "INFERRED", "EXPLICIT")))
        ), List.of("name"));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> arraySchema(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private Map<String, Object> stringSchema(String description) {
        return Map.of("type", "string", "description", description, "minLength", 1);
    }

    private Map<String, Object> booleanSchema(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    private Map<String, Object> lowerIntegerSchema(String description) {
        return Map.of("type", "integer", "description", description, "minimum", 0);
    }

    private Map<String, Object> upperIntegerSchema(String description) {
        return Map.of("type", "integer", "description", description, "minimum", -1);
    }

    private Map<String, Object> enumSchema(String description, List<String> values) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "string");
        schema.put("description", description);
        if (!values.isEmpty()) {
            schema.put("enum", values);
        }
        return schema;
    }

    private Map<String, Object> existingEnumSchema(String description, List<String> values) {
        return enumSchema(description + " Select exactly one canonical value supplied by ClassForge.", values);
    }

    private List<String> dataTypes() {
        return List.of("STRING", "INTEGER", "LONG", "DECIMAL", "BOOLEAN", "DATE", "DATETIME", "UUID", "CUSTOM");
    }

    private List<String> visibilities() {
        return List.of("PUBLIC", "PRIVATE", "PROTECTED", "PACKAGE");
    }

    private List<String> relationshipTypes() {
        return List.of("ASSOCIATION", "AGGREGATION", "COMPOSITION", "GENERALIZATION");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        return DIACRITICS.matcher(decomposed)
                .replaceAll("")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
