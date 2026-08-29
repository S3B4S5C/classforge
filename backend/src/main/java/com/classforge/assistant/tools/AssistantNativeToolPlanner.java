package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantPlanAction;
import com.classforge.assistant.AssistantPlanGroundingFilter;
import com.classforge.assistant.AssistantPlanNormalizer;
import com.classforge.assistant.AssistantPlanningException;
import com.classforge.assistant.AssistantPlanningStage;
import com.classforge.assistant.AssistantSemanticCompiler;
import com.classforge.assistant.AssistantSemanticPlan;
import com.classforge.assistant.UmlAssistantCommandResolver;
import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.domain.document.ProjectDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
public class AssistantNativeToolPlanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssistantNativeToolPlanner.class);
    private static final int MAX_ROUTED_STEPS = 8;

    private final DynamicUmlToolCatalog toolCatalog;
    private final AssistantToolCallingGateway toolCallingGateway;
    private final UmlToolCallResolver toolCallResolver;
    private final AssistantToolRouteAdjudicator routeAdjudicator;
    private final UmlAssistantCommandResolver commandResolver;
    private final AssistantSemanticCompiler semanticCompiler;
    private final AssistantPlanGroundingFilter groundingFilter;
    private final AssistantPlanNormalizer normalizer;

    public AssistantNativeToolPlanner(
            DynamicUmlToolCatalog toolCatalog,
            AssistantToolCallingGateway toolCallingGateway,
            UmlToolCallResolver toolCallResolver,
            AssistantToolRouteAdjudicator routeAdjudicator,
            UmlAssistantCommandResolver commandResolver,
            AssistantSemanticCompiler semanticCompiler,
            AssistantPlanGroundingFilter groundingFilter,
            AssistantPlanNormalizer normalizer
    ) {
        this.toolCatalog = toolCatalog;
        this.toolCallingGateway = toolCallingGateway;
        this.toolCallResolver = toolCallResolver;
        this.routeAdjudicator = routeAdjudicator;
        this.commandResolver = commandResolver;
        this.semanticCompiler = semanticCompiler;
        this.groundingFilter = groundingFilter;
        this.normalizer = normalizer;
    }

    public AssistantSemanticPlan plan(String userText, ProjectDocument document) {
        return planWithDiagnostics(userText, document).plan();
    }

    public AssistantToolPlanningDiagnostics planWithDiagnostics(String userText, ProjectDocument document) {
        List<AssistantToolName> routedSteps = route(userText, document);
        return executeRoutedPlan(userText, document, routedSteps);
    }

    private List<AssistantToolName> route(String userText, ProjectDocument document) {
        AssistantToolCatalog routingCatalog = toolCatalog.routingCatalog();
        List<AssistantToolInvocation> invocations = toolCallingGateway.call(userText, document, routingCatalog);
        if (invocations.isEmpty()) {
            throw toolResolution("El router native tools no devolvio ninguna ruta.", userText);
        }

        AssistantToolInvocation routeCall = invocations.getFirst();
        if (routeCall.name() != AssistantToolName.ROUTE_REQUEST) {
            throw toolResolution("El router native tools devolvio una tool inesperada: " + routeCall.name().wireName(), userText);
        }

        JsonNode steps = routeCall.arguments() == null ? null : routeCall.arguments().get("steps");
        if (steps == null || !steps.isArray() || steps.isEmpty()) {
            throw toolResolution("route_uml_request debe devolver al menos un step.", userText);
        }
        List<AssistantToolName> proposed = new ArrayList<>();
        for (JsonNode step : steps) {
            AssistantToolName name;
            try {
                name = AssistantToolName.fromWireName(step.asString());
            } catch (RuntimeException exception) {
                throw toolResolution("El router devolvio una tool UML desconocida: " + step.asString(), userText);
            }
            if (name == AssistantToolName.ROUTE_REQUEST || name == AssistantToolName.FINISH_PLAN) {
                throw toolResolution("El router devolvio una tool de control como paso ejecutable: " + name.wireName(), userText);
            }
            proposed.add(name);
        }

        List<AssistantToolName> adjudicated = routeAdjudicator.adjudicate(userText, document, proposed);
        if (adjudicated.isEmpty()) {
            throw toolResolution("No se pudo adjudicar una ruta UML segura para la peticion.", userText);
        }
        if (adjudicated.size() > MAX_ROUTED_STEPS) {
            throw toolResolution("La ruta adjudicada requiere demasiados pasos UML: " + adjudicated.size(), userText);
        }
        return adjudicated;
    }

    private AssistantToolPlanningDiagnostics executeRoutedPlan(
            String userText,
            ProjectDocument original,
            List<AssistantToolName> routedSteps
    ) {
        ProjectDocument working = original;
        List<AssistantPlanAction> actions = new ArrayList<>();
        List<AssistantResolvedReference> references = new ArrayList<>();
        List<AssistantToolInvocation> executedInvocations = new ArrayList<>();
        List<String> summaries = new ArrayList<>();
        AssistantToolCatalog lastCatalog = toolCatalog.routingCatalog();

        for (int stepIndex = 0; stepIndex < routedSteps.size(); stepIndex++) {
            AssistantToolName expected = routedSteps.get(stepIndex);
            lastCatalog = toolCatalog.buildForTools(working, List.of(expected));
            // Each routed step is intentionally stateless at the chat-protocol
            // level. The evolving ProjectDocument is the source of truth between
            // steps and rebuilds the next dynamic catalog (including newly-created
            // UUIDs). Replaying prior assistant/tool messages is redundant and can
            // confuse small native-tool models or their chat template.
            List<AssistantToolInvocation> invocations = toolCallingGateway.call(
                    userText, working, lastCatalog
            );
            if (invocations.isEmpty()) {
                throw toolResolution("Qwen no devolvio tool_call para el paso " + expected.wireName() + ".", userText);
            }
            AssistantToolInvocation selected = invocations.stream()
                    .filter(invocation -> invocation.name() == expected)
                    .findFirst()
                    .orElseThrow(() -> toolResolution(
                            "El router esperaba " + expected.wireName()
                                    + " pero Qwen no devolvio ninguna llamada a esa tool.",
                            userText
                    ));

            if (invocations.size() > 1) {
                LOGGER.debug(
                        "Qwen devolvio {} tool_calls para un paso routed {}; ClassForge usa solo la primera llamada compatible y descarta el resto.",
                        invocations.size(),
                        expected.wireName()
                );
            }

            AssistantToolResolution resolution = resolve(userText, List.of(selected), lastCatalog, working);
            AssistantSemanticPlan prepared = prepare(userText, resolution.plan(), working);
            debug(lastCatalog, invocations, resolution);

            UmlCommandPayload stepBatch = commandResolver.resolve(prepared, working);
            working = commandResolver.preview(working, stepBatch);

            actions.addAll(prepared.actions());
            references.addAll(resolution.references());
            executedInvocations.add(selected);
            summaries.add(prepared.summary());

        }

        AssistantSemanticPlan plan = new AssistantSemanticPlan(
                String.join("; ", summaries), List.copyOf(actions)
        );
        return new AssistantToolPlanningDiagnostics(
                plan,
                lastCatalog,
                List.copyOf(executedInvocations),
                List.copyOf(references),
                routedSteps.size() + 1
        );
    }

    private AssistantSemanticPlan prepare(
            String userText,
            AssistantSemanticPlan rawPlan,
            ProjectDocument document
    ) {
        AssistantSemanticPlan compiled;
        try {
            compiled = semanticCompiler.compile(userText, rawPlan, document);
        } catch (AssistantPlanningException exception) {
            throw exception.withDiagnostic(AssistantPlanningStage.GROUNDING, null, userText, rawPlan);
        }

        AssistantSemanticPlan grounded;
        try {
            grounded = groundingFilter.sanitize(userText, compiled, document);
        } catch (AssistantPlanningException exception) {
            throw exception.withDiagnostic(AssistantPlanningStage.GROUNDING, null, userText, rawPlan);
        }

        try {
            return normalizer.normalize(grounded);
        } catch (AssistantPlanningException exception) {
            throw exception.withDiagnostic(AssistantPlanningStage.NORMALIZATION, null, userText, rawPlan);
        }
    }

    private AssistantToolResolution resolve(
            String userText,
            List<AssistantToolInvocation> invocations,
            AssistantToolCatalog catalog,
            ProjectDocument document
    ) {
        try {
            return toolCallResolver.resolve(userText, invocations, catalog, document);
        } catch (AssistantPlanningException exception) {
            throw exception.withDiagnostic(AssistantPlanningStage.TOOL_RESOLUTION, null, userText, null);
        }
    }

    private AssistantPlanningException toolResolution(String message, String userText) {
        return new AssistantPlanningException(message).withDiagnostic(
                AssistantPlanningStage.TOOL_RESOLUTION, null, userText, null
        );
    }

    private void debug(
            AssistantToolCatalog catalog,
            List<AssistantToolInvocation> invocations,
            AssistantToolResolution resolution
    ) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "Assistant native tools: exposed={} calls={} references={}",
                    catalog.definitions().stream().map(def -> def.name().wireName()).toList(),
                    invocations, resolution.references()
            );
        }
    }

    public record AssistantToolPlanningDiagnostics(
            AssistantSemanticPlan plan,
            AssistantToolCatalog catalog,
            List<AssistantToolInvocation> invocations,
            List<AssistantResolvedReference> references,
            int rounds
    ) {
    }
}
