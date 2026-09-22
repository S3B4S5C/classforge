package com.classforge.assistant.tools;

import com.classforge.assistant.AssistantPlanningException;
import com.classforge.assistant.bedrock.BedrockConverseSupport;
import com.classforge.project.domain.document.ProjectDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "classforge.assistant.text.provider", havingValue = "bedrock")
public class BedrockToolCallingGateway implements AssistantToolCallingGateway {

    private final BedrockConverseSupport bedrock;
    private final String modelId;
    private final int maxTokens;

    public BedrockToolCallingGateway(
            BedrockConverseSupport bedrock,
            @Value("${classforge.assistant.bedrock.text-model:us.amazon.nova-2-lite-v1:0}") String modelId,
            @Value("${classforge.assistant.bedrock.text-max-completion-tokens:1024}") int maxTokens
    ) {
        this.bedrock = bedrock;
        this.modelId = required(modelId, "Bedrock text model");
        this.maxTokens = Math.max(128, maxTokens);
    }

    @Override
    public List<AssistantToolInvocation> call(
            String userText,
            ProjectDocument document,
            AssistantToolCatalog catalog,
            List<AssistantToolConversationTurn> history
    ) {
        if (catalog.definitions().isEmpty()) {
            throw new AssistantPlanningException("No hay herramientas UML disponibles para esta peticion.");
        }

        try {
            List<Message> messages = new ArrayList<>();
            // Preserve the same message ordering used by the llama.cpp adapter: the
            // active user request is followed by any prior tool-use/result context.
            messages.add(bedrock.userText(userText));
            if (history != null) {
                for (AssistantToolConversationTurn turn : history) {
                    for (int i = 0; i < turn.invocations().size(); i++) {
                        AssistantToolInvocation invocation = turn.invocations().get(i);
                        messages.add(bedrock.assistantToolUse(
                                invocation.id(),
                                invocation.name().wireName(),
                                invocation.arguments()
                        ));
                        messages.add(bedrock.userToolResult(invocation.id(), turn.results().get(i)));
                    }
                }
            }

            List<BedrockConverseSupport.ToolSpec> tools = catalog.definitions().stream()
                    .map(definition -> bedrock.toolSpec(
                            definition.name().wireName(),
                            definition.description(),
                            definition.parameters()
                    ))
                    .toList();
            String requiredTool = catalog.definitions().size() == 1
                    ? catalog.definitions().getFirst().name().wireName()
                    : null;

            BedrockConverseSupport.ToolUseResult result = bedrock.invokeToolChoice(
                    modelId,
                    systemPrompt(catalog),
                    messages,
                    tools,
                    requiredTool,
                    maxTokens
            );

            AssistantToolName name = AssistantToolName.fromWireName(result.name());
            boolean exposed = catalog.definitions().stream().anyMatch(definition -> definition.name() == name);
            if (!exposed) {
                throw new AssistantPlanningException(
                        "Bedrock devolvio una tool no expuesta por ClassForge: " + result.name()
                );
            }
            JsonNode arguments = result.input();
            if (arguments == null || !arguments.isObject()) {
                throw new AssistantPlanningException(
                        "Bedrock devolvio argumentos invalidos para " + result.name() + "."
                );
            }
            return List.of(new AssistantToolInvocation(
                    result.id() == null || result.id().isBlank() ? "bedrock-tool-1" : result.id(),
                    name,
                    arguments
            ));
        } catch (AssistantPlanningException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssistantPlanningException(
                    "No pudimos ejecutar tool calling contra Amazon Bedrock con " + modelId + ".",
                    exception
            );
        }
    }

    private String systemPrompt(AssistantToolCatalog catalog) {
        if (catalog.definitions().size() == 1
                && catalog.definitions().getFirst().name() == AssistantToolName.ROUTE_REQUEST) {
            return routingSystemPrompt();
        }
        return """
                Eres el planificador UML de ClassForge.
                Debes responder exclusivamente mediante exactamente una tool_call de la herramienta proporcionada para este paso.
                No escribas una respuesta conversacional.

                REGLAS:
                - El proyecto actual esta representado por enums dinamicos dentro de las tools.
                - Para elementos existentes selecciona exactamente un valor canonico de esos enums.
                - No inventes clases, atributos ni relaciones existentes.
                - Los nombres nuevos son texto libre: preserva lo que pidio el usuario, incluso si parece un typo.
                - No conviertas el estado actual del proyecto en acciones no solicitadas.
                - Usa la herramienta semanticamente correcta para la peticion.
                - Para renombrar atributos usa rename_attribute y devuelve new_name sin prefijo de clase.
                - Para multiplicidad usa set_relationship_multiplicity y end_class debe ser el extremo cuya multiplicidad cambia.
                - Para herencia usa create_generalization: subclass hereda de superclass.
                - Para agregacion/composicion: whole_class es el todo y part_class es la parte.
                - Para upper infinito usa -1; lower nunca puede ser negativo.
                - "muchas/muchos" sin minimo explicito significa 0..*; "cero o muchas" significa 0..*.
                - Si una propiedad opcional no fue solicitada, omitela.
                - ClassForge ya enruto el paso actual: emite exactamente una llamada a la unica tool UML expuesta para ese paso.
                - En peticiones compuestas ClassForge te invoca de nuevo con el siguiente paso y un ProjectDocument efimero actualizado.
                - Si una clase fue creada en una ronda previa, ya aparece en el catalogo actual y puede usarse como referencia existente.
                """;
    }

    private String routingSystemPrompt() {
        return """
                Eres el router de operaciones UML de ClassForge.
                Responde exclusivamente con exactamente una llamada a route_uml_request.

                Debes convertir la peticion en una lista ORDENADA de herramientas semanticas.
                No resuelvas nombres de clases ni atributos y no inventes cambios: solo elige operaciones.
                Usa un unico step para una peticion simple y varios steps solo si el usuario pide varios cambios.
                Si una clase nueva se usa despues, create_class debe ir antes de add_attributes o relaciones que la referencien.

                Ejemplos de significado:
                - crear clase -> create_class
                - renombrar clase -> rename_class
                - borrar clase -> delete_class
                - agregar campo/atributo -> add_attributes
                - renombrar atributo -> rename_attribute
                - cambiar propiedades de atributo -> update_attribute_properties
                - borrar atributo -> delete_attribute
                - asociar/conectar -> create_association
                - agregacion -> create_aggregation
                - composicion/contiene como parte fuerte -> create_composition
                - herencia/especializacion -> create_generalization
                - cambiar 0..*, 1..*, muchas, ninguna o varias -> set_relationship_multiplicity
                - cambiar tipo de relacion -> change_relationship_type
                - desconectar/quitar vinculo/relacion existente -> delete_relationship

                Para "crea Cliente, agregale email y relacionala con Factura" devuelve:
                [create_class, add_attributes, create_association].
                """;
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }
}
