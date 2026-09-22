package com.classforge.assistant.tools;

import com.classforge.assistant.bedrock.BedrockConverseSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.document.Document;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockToolCallingGatewayTests {

    @Test
    void mapsAForcedBedrockToolUseBackToTheExistingInvocationContract() throws Exception {
        BedrockConverseSupport bedrock = mock(BedrockConverseSupport.class);
        AssistantToolDefinition definition = new AssistantToolDefinition(
                AssistantToolName.CREATE_CLASS,
                "Crea una clase",
                Map.of("type", "object", "properties", Map.of("name", Map.of("type", "string")))
        );
        AssistantToolCatalog catalog = new AssistantToolCatalog(List.of(definition), Map.of(), Map.of(), Map.of());
        BedrockConverseSupport.ToolSpec spec = new BedrockConverseSupport.ToolSpec(
                "create_class",
                "Crea una clase",
                Document.mapBuilder().putString("type", "object").build()
        );
        when(bedrock.userText("Crea Cliente")).thenReturn(
                software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                        .role(software.amazon.awssdk.services.bedrockruntime.model.ConversationRole.USER)
                        .content(software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.fromText("Crea Cliente"))
                        .build()
        );
        when(bedrock.toolSpec(eq("create_class"), anyString(), anyMap())).thenReturn(spec);
        when(bedrock.invokeToolChoice(anyString(), anyString(), anyList(), anyList(), eq("create_class"), anyInt()))
                .thenReturn(new BedrockConverseSupport.ToolUseResult(
                        "tool-1",
                        "create_class",
                        JsonMapper.builder().build().readTree("{\"name\":\"Cliente\"}")
                ));

        BedrockToolCallingGateway gateway = new BedrockToolCallingGateway(
                bedrock,
                "us.amazon.nova-2-lite-v1:0",
                1024
        );

        List<AssistantToolInvocation> result = gateway.call("Crea Cliente", null, catalog, List.of());

        assertEquals(1, result.size());
        assertEquals(AssistantToolName.CREATE_CLASS, result.getFirst().name());
        assertEquals("Cliente", result.getFirst().arguments().get("name").asString());
        verify(bedrock).invokeToolChoice(
                eq("us.amazon.nova-2-lite-v1:0"),
                anyString(),
                anyList(),
                anyList(),
                eq("create_class"),
                eq(1024)
        );
    }
}
