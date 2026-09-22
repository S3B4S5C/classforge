package com.classforge.assistant.bedrock;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BedrockConverseSupportTests {

    @Test
    void sendsImageAndForcedStructuredToolThroughConverse() {
        BedrockRuntimeClient client = mock(BedrockRuntimeClient.class);
        JsonMapper mapper = JsonMapper.builder().build();
        ToolUseBlock toolUse = ToolUseBlock.builder()
                .toolUseId("tool-1")
                .name("vision_uml_proposal")
                .input(Document.mapBuilder().putString("summary", "ok").build())
                .build();
        Message responseMessage = Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(ContentBlock.builder().toolUse(toolUse).build())
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(
                ConverseResponse.builder()
                        .output(ConverseOutput.builder().message(responseMessage).build())
                        .build()
        );

        BedrockConverseSupport support = new BedrockConverseSupport(client, mapper);
        BedrockConverseSupport.ToolUseResult result = support.invokeStructuredImage(
                "us.amazon.nova-2-lite-v1:0",
                "system",
                "user",
                new byte[]{1, 2, 3},
                "image/png",
                "{\"title\":\"StrictVision\",\"type\":\"object\",\"additionalProperties\":false,"
                        + "\"properties\":{\"summary\":{\"type\":\"string\"}},\"required\":[\"summary\"]}",
                "vision_uml_proposal",
                512
        );

        assertEquals("vision_uml_proposal", result.name());
        assertEquals("ok", result.input().get("summary").asString());

        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(captor.capture());
        ConverseRequest request = captor.getValue();
        assertEquals("us.amazon.nova-2-lite-v1:0", request.modelId());
        assertEquals(512, request.inferenceConfig().maxTokens());
        assertNotNull(request.toolConfig().toolChoice().tool());
        assertEquals("vision_uml_proposal", request.toolConfig().toolChoice().tool().name());
        assertEquals(1, request.messages().size());
        assertTrue(request.messages().getFirst().content().stream().anyMatch(block -> block.image() != null));

        Document toolSchema = request.toolConfig().tools().getFirst().toolSpec().inputSchema().json();
        assertEquals("object", toolSchema.asMap().get("type").asString());
        assertTrue(toolSchema.asMap().containsKey("properties"));
        assertTrue(toolSchema.asMap().containsKey("required"));
        assertFalse(toolSchema.asMap().containsKey("title"));
        assertFalse(toolSchema.asMap().containsKey("additionalProperties"));
    }
}
