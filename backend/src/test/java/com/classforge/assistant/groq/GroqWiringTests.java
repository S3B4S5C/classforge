package com.classforge.assistant.groq;

import com.classforge.assistant.AssistantRuntimeHealthService;
import com.classforge.assistant.AssistantRuntimeProbe;
import com.classforge.assistant.AssistantVisionRuntimeProbe;
import com.classforge.assistant.SpeechToTextGateway;
import com.classforge.assistant.WhisperCppSpeechToTextGateway;
import com.classforge.assistant.bedrock.BedrockConverseSupport;
import com.classforge.assistant.bedrock.BedrockRuntimeConfiguration;
import com.classforge.assistant.tools.AssistantToolCallingGateway;
import com.classforge.assistant.tools.BedrockToolCallingGateway;
import com.classforge.assistant.tools.GroqToolCallingGateway;
import com.classforge.assistant.tools.LlamaNativeToolCallingGateway;
import com.classforge.assistant.vision.*;
import com.classforge.project.application.ProjectService;
import com.classforge.projectassistant.ProjectAssistantConversationGateway;
import com.classforge.projectassistant.llm.BedrockProjectAssistantGateway;
import com.classforge.projectassistant.llm.GroqProjectAssistantGateway;
import com.classforge.projectassistant.llm.LlamaCppProjectAssistantGateway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises actual provider conditions without application scanning, MongoDB or external model calls. */
class GroqWiringTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Providers.class)
            .withPropertyValues("classforge.assistant.text.provider=groq", "classforge.assistant.vision.provider=groq",
                    "classforge.assistant.voice.provider=groq", "classforge.assistant.bedrock.enabled=false",
                    "classforge.assistant.groq.api-key=");

    @Test
    void groqProvidersSelectExactlyOneAdapterPerContractAndInstantiateNoLegacyProvider() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(AssistantToolCallingGateway.class).values())
                    .singleElement().isExactlyInstanceOf(GroqToolCallingGateway.class);
            assertThat(context.getBeansOfType(ProjectAssistantConversationGateway.class).values())
                    .singleElement().isExactlyInstanceOf(GroqProjectAssistantGateway.class);
            assertThat(context.getBeansOfType(SpeechToTextGateway.class).values())
                    .singleElement().isExactlyInstanceOf(GroqSpeechToTextGateway.class);
            assertThat(context.getBeansOfType(VisionModelGateway.class).values())
                    .singleElement().isExactlyInstanceOf(GroqVisionModelGateway.class);
            assertThat(context.getBeansOfType(VisionHybridModelGateway.class).values())
                    .singleElement().isExactlyInstanceOf(GroqVisionHybridModelGateway.class);
            assertThat(context).doesNotHaveBean(BedrockRuntimeClient.class)
                    .doesNotHaveBean(BedrockConverseSupport.class)
                    .doesNotHaveBean(BedrockRuntimeConfiguration.class)
                    .doesNotHaveBean(WhisperCppSpeechToTextGateway.class)
                    .doesNotHaveBean(LlamaNativeToolCallingGateway.class)
                    .doesNotHaveBean(LlamaCppProjectAssistantGateway.class);
            // Groq vision adapters inherit the validated Llama pipeline; exact class assertions above
            // distinguish pipeline reuse from accidentally instantiating a legacy HTTP provider.
        });
    }

    @Test
    void missingServerKeyStartsNormallyAndHealthReportsAllCapabilitiesNotConfiguredWithoutLocalProbes() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertFalse(context.getBean(GroqClient.class).isConfigured());
            var availability = context.getBean(GroqHealthProbe.class).probe();
            for (var status : java.util.List.of(availability.text(), availability.speech(), availability.vision())) {
                assertFalse(status.available());
                assertEquals("NOT_CONFIGURED", status.state());
            }
            UUID userId = UUID.randomUUID(), projectId = UUID.randomUUID();
            var health = context.getBean(AssistantRuntimeHealthService.class).health(userId, projectId);
            assertFalse(health.readyForText());
            assertFalse(health.readyForVoice());
            assertFalse(health.readyForImage());
            assertEquals("groq-text", health.llama().name());
            assertEquals("groq-speech", health.whisper().name());
            assertEquals("groq-vision", health.vision().name());
            assertEquals("NOT_CONFIGURED", health.llama().state());
            assertEquals("NOT_CONFIGURED", health.whisper().state());
            assertEquals("NOT_CONFIGURED", health.vision().state());
            verify(context.getBean(ProjectService.class)).get(userId, projectId);
            verifyNoInteractions(context.getBean(AssistantRuntimeProbe.class), context.getBean(AssistantVisionRuntimeProbe.class));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({GroqClient.class, GroqHealthProbe.class, AssistantRuntimeHealthService.class,
            GroqToolCallingGateway.class, GroqProjectAssistantGateway.class, GroqSpeechToTextGateway.class,
            GroqVisionModelGateway.class, GroqVisionHybridModelGateway.class,
            VisionPromptBuilder.class, VisionHybridPromptBuilder.class,
            LlamaNativeToolCallingGateway.class, BedrockToolCallingGateway.class,
            LlamaCppProjectAssistantGateway.class, BedrockProjectAssistantGateway.class,
            WhisperCppSpeechToTextGateway.class, LlamaCppVisionModelGateway.class, LlamaCppVisionHybridModelGateway.class,
            BedrockVisionModelGateway.class, BedrockVisionHybridModelGateway.class,
            UnconfiguredVisionModelGateway.class, UnconfiguredVisionHybridModelGateway.class,
            BedrockRuntimeConfiguration.class, BedrockConverseSupport.class})
    static class Providers {
        @Bean JsonMapper jsonMapper() { return JsonMapper.builder().build(); }
        @Bean ProjectService projectService() { return mock(ProjectService.class); }
        @Bean AssistantRuntimeProbe assistantRuntimeProbe() { return mock(AssistantRuntimeProbe.class); }
        @Bean AssistantVisionRuntimeProbe assistantVisionRuntimeProbe() { return mock(AssistantVisionRuntimeProbe.class); }
    }
}
