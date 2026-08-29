package com.classforge.assistant;

import com.classforge.collaboration.protocol.UmlCommandPayload;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.application.ProjectService;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.ProjectDocument;
import com.classforge.project.persistence.ProjectMembershipEntity;
import com.classforge.project.persistence.ProjectMembershipRepository;
import com.classforge.project.validation.ProjectDocumentValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:classforge-cu31-assistant-access-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AssistantMembershipAccessIntegrationTests {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectMembershipRepository projectMembershipRepository;

    @Test
    void editorCanPlanTextButNoneIsRejectedBeforeCallingTheLlm() {
        UUID ownerId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        UUID noneId = UUID.randomUUID();

        Project project = projectService.create(ownerId, "Assistant CU31");
        projectMembershipRepository.save(
                ProjectMembershipEntity.editor(project.id(), editorId)
        );

        LanguageModelGateway gateway = mock(LanguageModelGateway.class);
        AssistantSemanticCompiler semanticCompiler = mock(AssistantSemanticCompiler.class);
        AssistantPlanGroundingFilter grounding = mock(AssistantPlanGroundingFilter.class);
        AssistantPlanNormalizer normalizer = mock(AssistantPlanNormalizer.class);
        UmlAssistantCommandResolver resolver = mock(UmlAssistantCommandResolver.class);
        ProjectDocumentValidator validator = mock(ProjectDocumentValidator.class);

        AssistantSemanticPlan plan =
                new AssistantSemanticPlan("Sin cambios", List.of());
        UmlCommandPayload batch =
                UmlCommandPayload.batch("Sin cambios", List.of());

        when(gateway.plan(anyString(), any(ProjectDocument.class))).thenReturn(plan);
        when(semanticCompiler.compile(anyString(), any(AssistantSemanticPlan.class), any(ProjectDocument.class)))
                .thenReturn(plan);
        when(grounding.sanitize(anyString(), any(AssistantSemanticPlan.class), any(ProjectDocument.class)))
                .thenReturn(plan);
        when(normalizer.normalize(any(AssistantSemanticPlan.class))).thenReturn(plan);
        when(resolver.resolve(any(AssistantSemanticPlan.class), any(ProjectDocument.class))).thenReturn(batch);
        when(resolver.preview(any(ProjectDocument.class), any(UmlCommandPayload.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AssistantPlanService service =
                new AssistantPlanService(
                        projectService,
                        gateway,
                        semanticCompiler,
                        grounding,
                        normalizer,
                        resolver,
                        validator
                );

        AssistantPlanResponse response =
                service.plan(editorId, project.id(), "No cambies nada");

        assertEquals(0L, response.baseRevision());
        assertEquals("TEXT", response.source());

        clearInvocations(gateway);

        assertThrows(
                ProjectNotFoundException.class,
                () -> service.plan(noneId, project.id(), "No cambies nada")
        );

        verifyNoInteractions(gateway);
    }

    @Test
    void editorCanReachVoicePipelineButNoneIsRejectedBeforeStt() {
        UUID ownerId = UUID.randomUUID();
        UUID editorId = UUID.randomUUID();
        UUID noneId = UUID.randomUUID();

        Project project = projectService.create(ownerId, "Voice CU31");
        projectMembershipRepository.save(
                ProjectMembershipEntity.editor(project.id(), editorId)
        );

        SpeechToTextGateway speechToTextGateway = mock(SpeechToTextGateway.class);
        AssistantPlanService planService = mock(AssistantPlanService.class);
        AssistantVoiceService voiceService =
                new AssistantVoiceService(
                        projectService,
                        speechToTextGateway,
                        planService
                );

        MockMultipartFile audio =
                new MockMultipartFile(
                        "audio",
                        "cu31.wav",
                        "audio/wav",
                        new byte[] {1, 2, 3, 4}
                );

        AssistantPlanResponse expected =
                new AssistantPlanResponse(
                        "VOICE",
                        "crea animal",
                        0L,
                        "Crear Animal",
                        new AssistantSemanticPlan("Crear Animal", List.of()),
                        UmlCommandPayload.batch("Crear Animal", List.of())
                );

        when(speechToTextGateway.transcribe(any(byte[].class), anyString()))
                .thenReturn("crea animal");
        when(planService.planVoice(editorId, project.id(), "crea animal"))
                .thenReturn(expected);

        AssistantPlanResponse actual =
                voiceService.plan(editorId, project.id(), audio);

        assertEquals(expected, actual);

        clearInvocations(speechToTextGateway);

        assertThrows(
                ProjectNotFoundException.class,
                () -> voiceService.plan(noneId, project.id(), audio)
        );

        verifyNoInteractions(speechToTextGateway);
    }
}
