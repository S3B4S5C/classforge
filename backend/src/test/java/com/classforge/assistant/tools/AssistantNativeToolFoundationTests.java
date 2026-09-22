package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantActionType;
import com.classforge.assistant.AssistantEntityReferenceResolver;
import com.classforge.assistant.AssistantIntentHintResolver;
import com.classforge.assistant.AssistantPlanningException;
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
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantNativeToolFoundationTests {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AssistantEntityReferenceResolver entityResolver = new AssistantEntityReferenceResolver();
    private final AssistantIntentHintResolver intentHintResolver = new AssistantIntentHintResolver();
    private final AssistantLiteralArgumentBinder literalBinder = new AssistantLiteralArgumentBinder();
    private final DynamicUmlToolCatalog catalogBuilder = new DynamicUmlToolCatalog(intentHintResolver);
    private final UmlToolCallResolver resolver = new UmlToolCallResolver(entityResolver, literalBinder);

    @Test
    void classRenamePhraseWinsOverAttributeNamedNombre() {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.build(
                "A VETERINARIO cambiale el nombre a MedicoVeterinario",
                document
        );

        assertEquals(1, catalog.definitions().size());
        assertEquals(AssistantToolName.RENAME_CLASS, catalog.definitions().getFirst().name());
    }

    @Test
    void unaryClassToolsRebindWrongSiblingToUniqueGroundedMention() throws Exception {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.build(
                "Cambia veternario por MedicoVeterinario",
                document
        );
        assertEquals(AssistantToolName.RENAME_CLASS, catalog.definitions().getFirst().name());

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-1",
                AssistantToolName.RENAME_CLASS,
                jsonMapper.readTree("{\"existing_class\":\"Veterinaria\",\"new_name\":\"MedicoVeterinario\"}")
        );

        AssistantToolResolution resolution = resolver.resolve(
                "Cambia veternario por MedicoVeterinario",
                List.of(invocation),
                catalog,
                document
        );

        assertEquals("Veterinario", resolution.plan().actions().getFirst().className());
        assertEquals("MedicoVeterinario", resolution.plan().actions().getFirst().newName());
    }

    @Test
    void encliticSpanishVerbsKeepSimpleRequestsOnOneTool() {
        ProjectDocument document = fixture();

        AssistantToolCatalog deleteCatalog = catalogBuilder.build(
                "Ya no necesito la clase tratmiento, eliminála",
                document
        );
        assertEquals(1, deleteCatalog.definitions().size());
        assertEquals(AssistantToolName.DELETE_CLASS, deleteCatalog.definitions().getFirst().name());

        AssistantToolCatalog addCatalog = catalogBuilder.build(
                "A Propetario agregale telefono de tipo STRING",
                document
        );
        assertEquals(1, addCatalog.definitions().size());
        assertEquals(AssistantToolName.ADD_ATTRIBUTES, addCatalog.definitions().getFirst().name());
    }

    @Test
    void createClassRebindsAutocorrectedNewNameToUserLiteral() throws Exception {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.build("Crea la clase HistorialClinco", document);

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-1",
                AssistantToolName.CREATE_CLASS,
                jsonMapper.readTree("{\"name\":\"HistorialClinico\"}")
        );

        AssistantToolResolution resolution = resolver.resolve(
                "Crea la clase HistorialClinco",
                List.of(invocation),
                catalog,
                document
        );

        assertEquals("HistorialClinco", resolution.plan().actions().getFirst().className());
    }

    @Test
    void updateAttributeRenameUsesDedicatedToolAndStripsClassPrefixFromNewName() throws Exception {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.build(
                "En Mascota renombra el atributo peso a pesoKg",
                document
        );
        assertEquals(AssistantToolName.RENAME_ATTRIBUTE, catalog.definitions().getFirst().name());

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-1",
                AssistantToolName.RENAME_ATTRIBUTE,
                jsonMapper.readTree("{\"existing_attribute_ref\":\"Mascota.peso\",\"new_name\":\"Mascota.pesoKg\"}")
        );

        AssistantToolResolution resolution = resolver.resolve(
                "En Mascota renombra el atributo peso a pesoKg",
                List.of(invocation),
                catalog,
                document
        );

        var action = resolution.plan().actions().getFirst();
        assertEquals(AssistantActionType.UPDATE_ATTRIBUTE, action.type());
        assertEquals("Mascota", action.className());
        assertEquals("peso", action.attributeName());
        assertEquals("pesoKg", action.newAttributeName());
        assertEquals(null, action.dataType());
    }

    @Test
    void deleteClassFollowedByDelModeloDoesNotBecomeAttributeIntent() {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.build(
                "Borra Veterinaria del modelo",
                document
        );
        assertEquals(AssistantToolName.DELETE_CLASS, catalog.definitions().getFirst().name());
    }

    @Test
    void scopedUnknownDeleteIsAttributeIntentAndSelectedExistingAttributeIsRejectedByProvenance() throws Exception {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.build(
                "Elimina codigoSecreto de Veterinaria",
                document
        );
        assertEquals(AssistantToolName.DELETE_ATTRIBUTE, catalog.definitions().getFirst().name());

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-1",
                AssistantToolName.DELETE_ATTRIBUTE,
                jsonMapper.readTree("{\"existing_attribute_ref\":\"Veterinaria.nombre\"}")
        );

        assertThrows(
                AssistantPlanningException.class,
                () -> resolver.resolve(
                        "Elimina codigoSecreto de Veterinaria",
                        List.of(invocation),
                        catalog,
                        document
                )
        );
    }


    @Test
    void associationClassToolBindsExistingRelationshipAndNewClassName() throws Exception {
        ProjectDocument document = fixture();
        String prompt = "Convierte la relacion entre Propietario y Mascota en la clase intermedia Tenencia";
        AssistantToolCatalog catalog = catalogBuilder.build(prompt, document);

        assertEquals(1, catalog.definitions().size());
        assertEquals(AssistantToolName.CREATE_ASSOCIATION_CLASS, catalog.definitions().getFirst().name());
        String relationshipRef = catalog.relationshipsByLabel().entrySet().stream()
                .filter(entry -> entry.getValue().sourceClassName().equals("Propietario")
                        && entry.getValue().targetClassName().equals("Mascota"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-association-class",
                AssistantToolName.CREATE_ASSOCIATION_CLASS,
                jsonMapper.readTree(
                        "{\"existing_relationship_ref\":"
                                + jsonMapper.writeValueAsString(relationshipRef)
                                + ",\"name\":\"Tenencia\"}"
                )
        );

        AssistantToolResolution resolution = resolver.resolve(
                prompt,
                List.of(invocation),
                catalog,
                document
        );

        var action = resolution.plan().actions().getFirst();
        assertEquals(AssistantActionType.CREATE_ASSOCIATION_CLASS, action.type());
        assertEquals("Tenencia", action.className());
        assertEquals("Propietario", action.sourceClassName());
        assertEquals("Mascota", action.targetClassName());
        assertTrue(action.relationshipId() != null);
    }

    @Test
    void generalizationUsesSubclassAndSuperclassRoles() throws Exception {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.build(
                "AnimalDomestcio hereda de 4nimal",
                document
        );
        assertEquals(AssistantToolName.CREATE_GENERALIZATION, catalog.definitions().getFirst().name());

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-1",
                AssistantToolName.CREATE_GENERALIZATION,
                jsonMapper.readTree("{\"subclass\":\"AnimalDomestico\",\"superclass\":\"Animal\"}")
        );

        AssistantToolResolution resolution = resolver.resolve(
                "AnimalDomestcio hereda de 4nimal",
                List.of(invocation),
                catalog,
                document
        );
        var action = resolution.plan().actions().getFirst();
        assertEquals("AnimalDomestico", action.sourceClassName());
        assertEquals("Animal", action.targetClassName());
        assertEquals(UmlRelationshipType.GENERALIZATION, action.relationshipType());
    }

    @Test
    void relationshipLowerMultiplicitySchemaCannotGenerateMinusOne() {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.build("Veterinaria agrupa Vacuna", document);
        assertEquals(AssistantToolName.CREATE_AGGREGATION, catalog.definitions().getFirst().name());

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) catalog.definitions().getFirst().parameters().get("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceLower = (Map<String, Object>) properties.get("source_lower");
        @SuppressWarnings("unchecked")
        Map<String, Object> sourceUpper = (Map<String, Object>) properties.get("source_upper");

        assertEquals(0, sourceLower.get("minimum"));
        assertEquals(-1, sourceUpper.get("minimum"));
    }

    @Test
    void relationshipMultiplicityUsesSemanticEndpointInsteadOfSourceTargetGuessing() throws Exception {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.build(
                "En la relacion entre Propietario y Mascota cambia la multiplicidad de Mascota a 0..*",
                document
        );
        assertEquals(AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY, catalog.definitions().getFirst().name());
        String relationshipRef = catalog.relationshipsByLabel().entrySet().stream()
                .filter(entry -> entry.getValue().sourceClassName().equals("Propietario")
                        && entry.getValue().targetClassName().equals("Mascota"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-1",
                AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY,
                jsonMapper.readTree(
                        "{\"existing_relationship_ref\":"
                                + jsonMapper.writeValueAsString(relationshipRef)
                                + ",\"end_class\":\"Mascota\",\"lower\":0,\"upper\":-1}"
                )
        );

        AssistantToolResolution resolution = resolver.resolve(
                "En la relacion entre Propietario y Mascota cambia la multiplicidad de Mascota a 0..*",
                List.of(invocation),
                catalog,
                document
        );

        var action = resolution.plan().actions().getFirst();
        assertEquals(null, action.sourceLower());
        assertEquals(null, action.sourceUpper());
        assertEquals(0, action.targetLower());
        assertEquals(-1, action.targetUpper());
    }


    @Test
    void conversationalZeroOrSeveralMultiplicityBindsMascotaEndpoint() throws Exception {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.buildForTools(
                document,
                List.of(AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY)
        );
        String relationshipRef = catalog.relationshipsByLabel().entrySet().stream()
                .filter(entry -> entry.getValue().sourceClassName().equals("Propietario")
                        && entry.getValue().targetClassName().equals("Mascota"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-natural-many",
                AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY,
                jsonMapper.readTree(
                        "{\"existing_relationship_ref\":\""
                                + relationshipRef.replace("\\", "\\\\").replace("\"", "\\\"")
                                + "\",\"end_class\":\"Propietario\",\"lower\":1,\"upper\":1}"
                )
        );

        AssistantToolResolution resolution = resolver.resolve(
                "Un propietario puede tener ninguna o varias mascotas",
                List.of(invocation),
                catalog,
                document
        );

        var action = resolution.plan().actions().getFirst();
        assertEquals(null, action.sourceLower());
        assertEquals(null, action.sourceUpper());
        assertEquals(0, action.targetLower());
        assertEquals(-1, action.targetUpper());
    }

    @Test
    void deleteRelationshipRebindsWrongLlmChoiceFromGroundedEndpointPair() throws Exception {
        ProjectDocument document = fixture();
        AssistantToolCatalog catalog = catalogBuilder.build(
                "Elimina la relacion entre Mascota y Vacuna",
                document
        );
        String wrongRelationship = catalog.relationshipsByLabel().entrySet().stream()
                .filter(entry -> entry.getValue().sourceClassName().equals("Propietario"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-1",
                AssistantToolName.DELETE_RELATIONSHIP,
                jsonMapper.readTree(
                        "{\"existing_relationship_ref\":"
                                + jsonMapper.writeValueAsString(wrongRelationship)
                                + "}"
                )
        );

        AssistantToolResolution resolution = resolver.resolve(
                "Elimina la relacion entre Mascota y Vacuna",
                List.of(invocation),
                catalog,
                document
        );

        var action = resolution.plan().actions().getFirst();
        assertEquals("Mascota", action.sourceClassName());
        assertEquals("Vacuna", action.targetClassName());
    }

    @Test
    void multiplicityEndpointIsBoundFromQuantityPhraseEvenWhenLlmChoosesOtherEnd() throws Exception {
        ProjectDocument document = fixture();
        String prompt = "Haz que un propietario pueda relacionarse con cero o muchas mascotas en la relacion existente";
        AssistantToolCatalog catalog = catalogBuilder.build(prompt, document);
        String relationshipRef = catalog.relationshipsByLabel().entrySet().stream()
                .filter(entry -> entry.getValue().sourceClassName().equals("Propietario")
                        && entry.getValue().targetClassName().equals("Mascota"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-1",
                AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY,
                jsonMapper.readTree(
                        "{\"existing_relationship_ref\":"
                                + jsonMapper.writeValueAsString(relationshipRef)
                                + ",\"end_class\":\"Propietario\",\"lower\":0,\"upper\":-1}"
                )
        );

        AssistantToolResolution resolution = resolver.resolve(prompt, List.of(invocation), catalog, document);
        var action = resolution.plan().actions().getFirst();
        assertEquals(null, action.sourceLower());
        assertEquals(null, action.sourceUpper());
        assertEquals(0, action.targetLower());
        assertEquals(-1, action.targetUpper());
    }

    @Test
    void explicitMultiplicityInUserTextOverridesWrongLlmUpperBound() throws Exception {
        ProjectDocument document = fixture();
        String prompt = "Pon 0..* del lado Masctoa en su relacion con Propietario";
        AssistantToolCatalog catalog = catalogBuilder.buildForTools(
                document,
                List.of(AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY)
        );
        String relationshipRef = catalog.relationshipsByLabel().entrySet().stream()
                .filter(entry -> entry.getValue().sourceClassName().equals("Propietario")
                        && entry.getValue().targetClassName().equals("Mascota"))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow();

        AssistantToolInvocation invocation = new AssistantToolInvocation(
                "call-1",
                AssistantToolName.SET_RELATIONSHIP_MULTIPLICITY,
                jsonMapper.readTree(
                        "{\"existing_relationship_ref\":"
                                + jsonMapper.writeValueAsString(relationshipRef)
                                + ",\"end_class\":\"Mascota\",\"lower\":0,\"upper\":1}"
                )
        );

        AssistantToolResolution resolution = resolver.resolve(prompt, List.of(invocation), catalog, document);
        var action = resolution.plan().actions().getFirst();
        assertEquals(0, action.targetLower());
        assertEquals(-1, action.targetUpper());
    }

    @Test
    void routingCatalogNeverEmbedsProjectEnums() {
        AssistantToolCatalog routing = catalogBuilder.routingCatalog();
        assertEquals(1, routing.definitions().size());
        assertEquals(AssistantToolName.ROUTE_REQUEST, routing.definitions().getFirst().name());
        assertTrue(routing.classIdsByName().isEmpty());
        assertTrue(routing.attributesByLabel().isEmpty());
        assertTrue(routing.relationshipsByLabel().isEmpty());
    }

    @Test
    void pluralPlusTranspositionStillGroundsMascota() {
        ProjectDocument document = fixture();
        assertTrue(entityResolver.fuzzyMentions("Un Propietario puede tener muchas Masctoas", "Mascota"));
        assertTrue(entityResolver.resolveExistingClass("Masctoas", document).isPresent());
        assertEquals("Mascota", entityResolver.resolveExistingClass("Masctoas", document).orElseThrow().canonicalName());
    }


    @Test
    void existingAssociationClassMentionDoesNotBecomeCreateAssociationClassIntent() {
        AssistantCompoundRequestDetector detector = new AssistantCompoundRequestDetector();
        String prompt = "Agrega el atributo descuento Decimal a la clase intermedia Mascota";

        assertEquals(Set.of(AssistantActionType.ADD_ATTRIBUTES), detector.requiredFamilies(prompt));

        AssistantToolCatalog catalog = catalogBuilder.build(prompt, fixture());
        assertEquals(1, catalog.definitions().size());
        assertEquals(AssistantToolName.ADD_ATTRIBUTES, catalog.definitions().getFirst().name());
    }

    @Test
    void compoundDetectorRequiresCreateAttributeAndRelationshipFamilies() {
        AssistantCompoundRequestDetector detector = new AssistantCompoundRequestDetector();
        String prompt = "Crea Cliente, agregale email STRING y relaciona Cliente con Factura";
        assertTrue(detector.isCompound(prompt));
        assertTrue(detector.requiredFamilies(prompt).contains(AssistantActionType.CREATE_CLASS));
        assertTrue(detector.requiredFamilies(prompt).contains(AssistantActionType.ADD_ATTRIBUTES));
        assertTrue(detector.requiredFamilies(prompt).contains(AssistantActionType.CREATE_RELATIONSHIP));
    }

    private ProjectDocument fixture() {
        UUID animalId = UUID.randomUUID();
        UUID animalDomesticoId = UUID.randomUUID();
        UUID mascotaId = UUID.randomUUID();
        UUID propietarioId = UUID.randomUUID();
        UUID veterinariaId = UUID.randomUUID();
        UUID veterinarioId = UUID.randomUUID();
        UUID tratamientoId = UUID.randomUUID();
        UUID vacunaId = UUID.randomUUID();
        UUID pesoId = UUID.randomUUID();
        UUID veterinariaNombreId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();

        UmlClass animal = new UmlClass(animalId, "Animal", List.of());
        UmlClass animalDomestico = new UmlClass(animalDomesticoId, "AnimalDomestico", List.of());
        UmlClass mascota = new UmlClass(
                mascotaId,
                "Mascota",
                List.of(new UmlAttribute(
                        pesoId,
                        "peso",
                        UmlDataType.DECIMAL,
                        null,
                        UmlVisibility.PRIVATE,
                        true,
                        false
                ))
        );
        UmlClass propietario = new UmlClass(propietarioId, "Propietario", List.of());
        UmlClass veterinaria = new UmlClass(
                veterinariaId,
                "Veterinaria",
                List.of(new UmlAttribute(
                        veterinariaNombreId,
                        "nombre",
                        UmlDataType.STRING,
                        null,
                        UmlVisibility.PRIVATE,
                        false,
                        false
                ))
        );
        UmlClass veterinario = new UmlClass(veterinarioId, "Veterinario", List.of());
        UmlClass tratamiento = new UmlClass(tratamientoId, "Tratamiento", List.of());
        UmlClass vacuna = new UmlClass(vacunaId, "Vacuna", List.of());

        UmlRelationship relationship = new UmlRelationship(
                relationshipId,
                propietarioId,
                mascotaId,
                UmlRelationshipType.ASSOCIATION,
                Multiplicity.one(),
                Multiplicity.one()
        );

        UmlRelationship mascotaVacuna = new UmlRelationship(
                UUID.randomUUID(),
                mascotaId,
                vacunaId,
                UmlRelationshipType.ASSOCIATION,
                Multiplicity.many(),
                Multiplicity.many()
        );

        Map<UUID, DiagramNodeLayout> nodes = new LinkedHashMap<>();
        List<UmlClass> classes = List.of(
                animal,
                animalDomestico,
                mascota,
                propietario,
                veterinaria,
                veterinario,
                tratamiento,
                vacuna
        );
        for (int index = 0; index < classes.size(); index++) {
            nodes.put(classes.get(index).id(), DiagramNodeLayout.defaultForIndex(index));
        }

        return new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(classes, List.of(relationship, mascotaVacuna)),
                new DiagramLayout(nodes)
        );
    }
}
