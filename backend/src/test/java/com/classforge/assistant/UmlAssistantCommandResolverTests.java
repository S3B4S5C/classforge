package com.classforge.assistant;

import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.collaboration.protocol.UmlCommandType;
import com.classforge.project.domain.document.AssociationClassSupport;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.Multiplicity;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlDataType;
import com.classforge.project.domain.document.UmlModel;
import com.classforge.project.domain.document.UmlRelationship;
import com.classforge.project.domain.document.UmlRelationshipType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UmlAssistantCommandResolverTests {

    private final AssistantPlanNormalizer normalizer =
            new AssistantPlanNormalizer();

    private final UmlAssistantCommandResolver resolver =
            new UmlAssistantCommandResolver(
                    new ProjectCommandExecutor(),
                    normalizer
            );


    @Test
    void associationClassIntentConvertsExistingRelationshipAndDeleteRestoresIt() {
        UmlClass pedido = new UmlClass(UUID.randomUUID(), "Pedido", List.of());
        UmlClass producto = new UmlClass(UUID.randomUUID(), "Producto", List.of());
        UmlRelationship original = new UmlRelationship(
                UUID.randomUUID(), pedido.id(), producto.id(), UmlRelationshipType.COMPOSITION,
                new Multiplicity(1, null), new Multiplicity(1, null)
        );
        ProjectDocument document = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(pedido, producto), List.of(original)),
                DiagramLayout.empty()
        );
        AssistantSemanticPlan create = new AssistantSemanticPlan(
                "Crear DetallePedido como clase de asociacion",
                List.of(new AssistantPlanAction(
                        AssistantActionType.CREATE_ASSOCIATION_CLASS,
                        "DetallePedido", null,
                        List.of(new AssistantAttributePlan(
                                "cantidad", UmlDataType.INTEGER, null, null,
                                false, false, AssistantTypeSource.EXPLICIT
                        )),
                        null, null, null, null, null, null, null,
                        "Pedido", "Producto", UmlRelationshipType.COMPOSITION,
                        1, -1, 1, -1,
                        original.id()
                ))
        );

        ProjectDocument created = resolver.preview(document, resolver.resolve(create, document));
        UmlClass detalle = created.umlModel().classes().stream()
                .filter(item -> item.name().equals("DetallePedido"))
                .findFirst().orElseThrow();
        UmlRelationship restoredMetadata = AssociationClassSupport.metadata(detalle)
                .orElseThrow().relationship();

        assertEquals(UmlRelationshipType.COMPOSITION, restoredMetadata.type());
        assertEquals(3, created.umlModel().classes().size());
        assertEquals(2, created.umlModel().relationships().size());
        assertEquals(2, AssociationClassSupport.auxiliaryRelationshipIds(created).size());

        AssistantSemanticPlan delete = new AssistantSemanticPlan(
                "Eliminar DetallePedido",
                List.of(new AssistantPlanAction(
                        AssistantActionType.DELETE_CLASS,
                        "DetallePedido", null, List.of(),
                        null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null
                ))
        );
        ProjectDocument deleted = resolver.preview(created, resolver.resolve(delete, created));

        assertEquals(2, deleted.umlModel().classes().size());
        assertEquals(1, deleted.umlModel().relationships().size());
        UmlRelationship restored = deleted.umlModel().relationships().getFirst();
        assertEquals(original.id(), restored.id());
        assertEquals(UmlRelationshipType.COMPOSITION, restored.type());
        assertEquals(original.sourceMultiplicity(), restored.sourceMultiplicity());
        assertEquals(original.targetMultiplicity(), restored.targetMultiplicity());
    }

    @Test
    void richCreateClassIntentExpandsToCreatePlusAttributes() {
        AssistantSemanticPlan plan =
                new AssistantSemanticPlan(
                        "Crear Veterinario con id y nombre",
                        List.of(
                                new AssistantPlanAction(
                                        AssistantActionType.CREATE_CLASS,
                                        "Veterinario",
                                        null,
                                        List.of(
                                                new AssistantAttributePlan(
                                                        "id",
                                                        UmlDataType.UUID,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        AssistantTypeSource.EXPLICIT
                                                ),
                                                new AssistantAttributePlan(
                                                        "nombre",
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        AssistantTypeSource.DEFAULT
                                                )
                                        ),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                );

        UmlCommandPayload batch =
                resolver.resolve(
                        plan,
                        ProjectDocument.empty()
                );

        assertEquals(
                UmlCommandType.BATCH,
                batch.type()
        );

        assertEquals(
                3,
                batch.safeCommands()
                        .size()
        );

        assertEquals(
                UmlCommandType.CREATE_CLASS,
                batch.safeCommands()
                        .get(0)
                        .type()
        );

        assertEquals(
                UmlCommandType.ADD_ATTRIBUTE,
                batch.safeCommands()
                        .get(1)
                        .type()
        );

        assertEquals(
                UmlCommandType.ADD_ATTRIBUTE,
                batch.safeCommands()
                        .get(2)
                        .type()
        );

        ProjectDocument preview =
                resolver.preview(
                        ProjectDocument.empty(),
                        batch
                );

        assertEquals(
                2,
                preview.umlModel()
                        .classes()
                        .getFirst()
                        .attributes()
                        .size()
        );

        assertEquals(
                UmlDataType.STRING,
                preview.umlModel()
                        .classes()
                        .getFirst()
                        .attributes()
                        .get(1)
                        .dataType()
        );
    }

    @Test
    void addAttributesIntentExpandsToMultipleCommands() {
        AssistantSemanticPlan createPlan =
                new AssistantSemanticPlan(
                        "Crear Veterinario",
                        List.of(
                                new AssistantPlanAction(
                                        AssistantActionType.CREATE_CLASS,
                                        "Veterinario",
                                        null,
                                        List.of(),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                );

        UmlCommandPayload createBatch =
                resolver.resolve(
                        createPlan,
                        ProjectDocument.empty()
                );

        ProjectDocument current =
                resolver.preview(
                        ProjectDocument.empty(),
                        createBatch
                );

        AssistantSemanticPlan addPlan =
                new AssistantSemanticPlan(
                        "Agregar telefono y correo",
                        List.of(
                                new AssistantPlanAction(
                                        AssistantActionType.ADD_ATTRIBUTES,
                                        "Veterinario",
                                        null,
                                        List.of(
                                                new AssistantAttributePlan(
                                                        "telefono",
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        AssistantTypeSource.DEFAULT
                                                ),
                                                new AssistantAttributePlan(
                                                        "correo",
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        null,
                                                        AssistantTypeSource.DEFAULT
                                                )
                                        ),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null
                                )
                        )
                );

        UmlCommandPayload addBatch =
                resolver.resolve(
                        addPlan,
                        current
                );

        assertEquals(
                2,
                addBatch.safeCommands()
                        .size()
        );
    }
}