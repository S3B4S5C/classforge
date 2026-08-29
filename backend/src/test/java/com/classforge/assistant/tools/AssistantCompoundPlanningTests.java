package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantActionType;
import com.classforge.assistant.AssistantEntityReferenceResolver;
import com.classforge.assistant.AssistantIntentHintResolver;
import com.classforge.assistant.AssistantPlanGroundingFilter;
import com.classforge.assistant.AssistantPlanNormalizer;
import com.classforge.assistant.AssistantSemanticCompiler;
import com.classforge.assistant.UmlAssistantCommandResolver;
import com.classforge.collaboration.application.ProjectCommandExecutor;
import com.classforge.project.domain.document.DiagramLayout;
import com.classforge.project.domain.document.DiagramNodeLayout;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.domain.document.UmlClass;
import com.classforge.project.domain.document.UmlModel;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantCompoundPlanningTests {

    @Test
    void createThenAddAttributeThenRelateUsesEphemeralPreviewBetweenRounds() throws Exception {
        JsonMapper jsonMapper = JsonMapper.builder().build();
        AssistantEntityReferenceResolver entityResolver = new AssistantEntityReferenceResolver();
        AssistantIntentHintResolver intentResolver = new AssistantIntentHintResolver();
        AssistantPlanNormalizer normalizer = new AssistantPlanNormalizer();
        UmlAssistantCommandResolver commands = new UmlAssistantCommandResolver(
                new ProjectCommandExecutor(), normalizer
        );
        AtomicInteger round = new AtomicInteger();

        AssistantToolCallingGateway gateway = (userText, document, catalog, history) -> {
            int current = round.incrementAndGet();
            assertTrue(history.isEmpty());
            if (current >= 3) {
                assertTrue(catalog.classIdsByName().containsKey("Cliente"));
            }
            return switch (current) {
                case 1 -> List.of(new AssistantToolInvocation(
                        "route-1", AssistantToolName.ROUTE_REQUEST,
                        jsonMapper.readTree("{\"steps\":[\"create_class\",\"add_attributes\",\"create_association\"]}")
                ));
                case 2 -> List.of(
                        new AssistantToolInvocation(
                                "call-1", AssistantToolName.CREATE_CLASS,
                                jsonMapper.readTree("{\"name\":\"Cliente\"}")
                        ),
                        new AssistantToolInvocation(
                                "call-1-extra", AssistantToolName.CREATE_CLASS,
                                jsonMapper.readTree("{\"name\":\"ClienteDuplicado\"}")
                        )
                );
                case 3 -> List.of(new AssistantToolInvocation(
                        "call-2", AssistantToolName.ADD_ATTRIBUTES,
                        jsonMapper.readTree("{\"existing_class\":\"Cliente\",\"attributes\":[{\"name\":\"email\",\"data_type\":\"STRING\"}]}")
                ));
                default -> List.of(new AssistantToolInvocation(
                        "call-3", AssistantToolName.CREATE_ASSOCIATION,
                        jsonMapper.readTree("{\"source_class\":\"Cliente\",\"target_class\":\"Factura\"}")
                ));
            };
        };

        AssistantNativeToolPlanner planner = new AssistantNativeToolPlanner(
                new DynamicUmlToolCatalog(intentResolver),
                gateway,
                new UmlToolCallResolver(entityResolver, new AssistantLiteralArgumentBinder()),
                new AssistantToolRouteAdjudicator(
                        intentResolver,
                        entityResolver,
                        new AssistantCompoundRequestDetector()
                ),
                commands,
                new AssistantSemanticCompiler(entityResolver),
                new AssistantPlanGroundingFilter(entityResolver),
                normalizer
        );

        UUID facturaId = UUID.randomUUID();
        ProjectDocument original = new ProjectDocument(
                ProjectDocument.CURRENT_SCHEMA_VERSION,
                new UmlModel(List.of(new UmlClass(facturaId, "Factura", List.of())), List.of()),
                new DiagramLayout(Map.of(facturaId, DiagramNodeLayout.defaultForIndex(0)))
        );

        var diagnostics = planner.planWithDiagnostics(
                "Crea Cliente, agregale email STRING y relaciona Cliente con Factura",
                original
        );

        assertEquals(4, diagnostics.rounds());
        assertEquals(3, diagnostics.plan().actions().size());
        assertEquals(AssistantActionType.CREATE_CLASS, diagnostics.plan().actions().get(0).type());
        assertEquals(AssistantActionType.ADD_ATTRIBUTES, diagnostics.plan().actions().get(1).type());
        assertEquals("Cliente", diagnostics.plan().actions().get(1).className());
        assertEquals(AssistantActionType.CREATE_RELATIONSHIP, diagnostics.plan().actions().get(2).type());
        assertEquals("Cliente", diagnostics.plan().actions().get(2).sourceClassName());
        assertEquals("Factura", diagnostics.plan().actions().get(2).targetClassName());

        var finalBatch = commands.resolve(diagnostics.plan(), original);
        var finalPreview = commands.preview(original, finalBatch);
        var cliente = finalPreview.umlModel().classes().stream()
                .filter(c -> c.name().equals("Cliente"))
                .findFirst()
                .orElseThrow();
        assertTrue(cliente.attributes().stream().anyMatch(a -> a.name().equals("email")));
        assertTrue(finalPreview.umlModel().relationships().stream().anyMatch(r ->
                r.sourceClassId().equals(cliente.id()) && r.targetClassId().equals(facturaId)
        ));
        assertTrue(finalBatch.safeCommands().size() >= 3);

        // Planning is pure: only the ephemeral copy evolves.
        assertEquals(1, original.umlModel().classes().size());
        assertTrue(original.umlModel().classes().stream().noneMatch(c -> c.name().equals("Cliente")));
    }
}
