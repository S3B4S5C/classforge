package com.classforge.generation.spring.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.classforge.auth.security.CurrentUser;
import com.classforge.generation.relational.RelationalMappingDiagnostic;
import com.classforge.generation.relational.RelationalMappingDiagnosticCode;
import com.classforge.generation.relational.RelationalMappingException;
import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.application.SpringBootGenerationPrimaryKeyFallback;
import com.classforge.generation.spring.application.SpringBootGenerationReadinessException;
import com.classforge.generation.spring.application.SpringBootGenerationService;
import com.classforge.generation.spring.archive.SpringArchiveException;
import com.classforge.generation.spring.archive.SpringBootGenerationArtifact;
import com.classforge.generation.spring.generated.GeneratedProjectDiagnostic;
import com.classforge.generation.spring.generated.GeneratedProjectDiagnosticCode;
import com.classforge.generation.spring.generated.GeneratedProjectException;
import com.classforge.generation.spring.model.SpringGenerationConfig;
import com.classforge.generation.spring.validation.SpringGenerationDiagnostic;
import com.classforge.generation.spring.validation.SpringGenerationDiagnosticCode;
import com.classforge.generation.spring.validation.SpringGenerationException;
import com.classforge.project.access.ProjectAccessService;
import com.classforge.project.application.ProjectNotFoundException;
import com.classforge.project.application.ProjectRevisionConflictException;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SpringBootGenerationControllerTests {
    @Test
    void authorizesBeforeGeneratingAndReturnsExactArtifact() throws Exception {
        Fixture fixture = fixture();
        byte[] bytes = {0, 13, 10, -1};
        when(fixture.service.generate(eq(fixture.projectId), eq(7L), any(), any()))
                .thenReturn(new SpringBootGenerationArtifact(
                        "biblioteca-backend.zip",
                        "application/zip",
                        7,
                        bytes
                ));

        fixture.mvc.perform(validRequest(fixture))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"biblioteca-backend.zip\""
                ))
                .andExpect(content().bytes(bytes));

        var order = inOrder(fixture.access, fixture.service);
        order.verify(fixture.access).requireEdit(fixture.userId, fixture.projectId);
        order.verify(fixture.service).generate(
                fixture.projectId,
                7L,
                new SpringGenerationConfig("biblioteca", "com.example.biblioteca"),
                SpringBootGenerationOptions.simpleCrud(false)
        );
    }

    @Test
    void forwardsExplicitFirstAttributeIdentifierOptIn() throws Exception {
        Fixture fixture = fixture();
        when(fixture.service.generate(eq(fixture.projectId), eq(7L), any(), any()))
                .thenReturn(new SpringBootGenerationArtifact(
                        "biblioteca-backend.zip",
                        "application/zip",
                        7,
                        new byte[] {1}
                ));

        fixture.mvc.perform(post(path(fixture))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevision\":7,\"artifactName\":\"biblioteca\",\"basePackage\":\"com.example.biblioteca\",\"useFirstAttributeAsIdentifier\":true}"))
                .andExpect(status().isOk());

        verify(fixture.service).generate(
                fixture.projectId,
                7L,
                new SpringGenerationConfig("biblioteca", "com.example.biblioteca"),
                SpringBootGenerationOptions.simpleCrud(true)
        );
    }

    @Test
    void exposesPrimaryKeyFallbackAsExplicitConfirmationWithObservations() throws Exception {
        Fixture fixture = fixture();
        UUID classId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        when(fixture.service.generate(eq(fixture.projectId), eq(7L), any(), any()))
                .thenThrow(new SpringBootGenerationReadinessException(
                        List.of(new RelationalMappingDiagnostic(
                                RelationalMappingDiagnosticCode.CLASS_IDENTIFIER_REQUIRED,
                                classId,
                                "classes",
                                "A root class requires an identifier."
                        )),
                        List.of(new SpringBootGenerationPrimaryKeyFallback(
                                classId,
                                "Cliente",
                                attributeId,
                                "codigo"
                        ))
                ));

        fixture.mvc.perform(validRequest(fixture))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PRIMARY_KEY_FALLBACK_CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.diagnostics[0].code").value("CLASS_IDENTIFIER_REQUIRED"))
                .andExpect(jsonPath("$.primaryKeyFallbacks[0].className").value("Cliente"))
                .andExpect(jsonPath("$.primaryKeyFallbacks[0].attributeName").value("codigo"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void deniesBeforeGeneration() throws Exception {
        Fixture fixture = fixture();
        doThrow(new ProjectNotFoundException(fixture.projectId))
                .when(fixture.access)
                .requireEdit(fixture.userId, fixture.projectId);

        fixture.mvc.perform(validRequest(fixture))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROJECT_NOT_FOUND"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));

        verifyNoInteractions(fixture.service);
    }

    @Test
    void mapsRevisionConflictToStableCategory() throws Exception {
        Fixture fixture = fixture();
        when(fixture.service.generate(eq(fixture.projectId), eq(7L), any(), any()))
                .thenThrow(new ProjectRevisionConflictException(7, 8));

        fixture.mvc.perform(validRequest(fixture))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("STALE_PROJECT_REVISION"))
                .andExpect(jsonPath("$.requestedRevision").value(7))
                .andExpect(jsonPath("$.currentRevision").value(8))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void rejectsInvalidConfigWithoutCallingGeneration() throws Exception {
        Fixture fixture = fixture();

        fixture.mvc.perform(post(path(fixture))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseRevision\":7,\"artifactName\":\"Biblioteca\",\"basePackage\":\"com.example.biblioteca\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_GENERATION_CONFIGURATION"))
                .andExpect(jsonPath("$.diagnostics[0].code").value("INVALID_ARTIFACT_NAME"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));

        verifyNoInteractions(fixture.service);
    }

    @Test
    void mapsRelationalMappingDiagnosticsWithoutPartialZip() throws Exception {
        Fixture fixture = fixture();
        UUID elementId = UUID.randomUUID();
        when(fixture.service.generate(eq(fixture.projectId), eq(7L), any(), any()))
                .thenThrow(new RelationalMappingException(List.of(
                        new RelationalMappingDiagnostic(
                                RelationalMappingDiagnosticCode.CLASS_IDENTIFIER_REQUIRED,
                                elementId,
                                "classes[Cliente]",
                                "Cliente requires an identifier."
                        )
                )));

        fixture.mvc.perform(validRequest(fixture))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("RELATIONAL_MAPPING_REJECTED"))
                .andExpect(jsonPath("$.diagnostics[0].code").value("CLASS_IDENTIFIER_REQUIRED"))
                .andExpect(jsonPath("$.diagnostics[0].elementId").value(elementId.toString()))
                .andExpect(jsonPath("$.diagnostics[0].path").value("classes[Cliente]"))
                .andExpect(jsonPath("$.diagnostics[0].message").value("Cliente requires an identifier."))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void classifiesNonConfigurationSpringDiagnosticsAsModelRejected() throws Exception {
        Fixture fixture = fixture();
        UUID elementId = UUID.randomUUID();
        when(fixture.service.generate(eq(fixture.projectId), eq(7L), any(), any()))
                .thenThrow(new SpringGenerationException(List.of(
                        new SpringGenerationDiagnostic(
                                SpringGenerationDiagnosticCode.JAVA_TYPE_NAME_COLLISION,
                                elementId,
                                "entities",
                                "Duplicate generated Java type."
                        )
                )));

        fixture.mvc.perform(validRequest(fixture))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SPRING_MODEL_REJECTED"))
                .andExpect(jsonPath("$.diagnostics[0].code").value("JAVA_TYPE_NAME_COLLISION"))
                .andExpect(jsonPath("$.diagnostics[0].elementId").value(elementId.toString()))
                .andExpect(jsonPath("$.diagnostics[0].path").value("entities"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void sanitizesTemplateRenderFailures() throws Exception {
        Fixture fixture = fixture();
        when(fixture.service.generate(eq(fixture.projectId), eq(7L), any(), any()))
                .thenThrow(new GeneratedProjectException(
                        List.of(new GeneratedProjectDiagnostic(
                                GeneratedProjectDiagnosticCode.TEMPLATE_RENDER_FAILED,
                                "jpa/entity.java.ftl",
                                "SECRET_INTERNAL_FREEMARKER_DETAIL"
                        )),
                        new IllegalStateException("SECRET_INTERNAL_CAUSE")
                ));

        fixture.mvc.perform(validRequest(fixture))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("TEMPLATE_RENDER_FAILED"))
                .andExpect(jsonPath("$.diagnostics.length()").value(0))
                .andExpect(content().string(not(containsString("SECRET_INTERNAL"))))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void sanitizesGeneratedProjectValidationFailures() throws Exception {
        Fixture fixture = fixture();
        when(fixture.service.generate(eq(fixture.projectId), eq(7L), any(), any()))
                .thenThrow(new GeneratedProjectException(List.of(
                        new GeneratedProjectDiagnostic(
                                GeneratedProjectDiagnosticCode.INVALID_PATH,
                                "../secret",
                                "SECRET_INTERNAL_PATH_DETAIL"
                        )
                )));

        fixture.mvc.perform(validRequest(fixture))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("GENERATED_PROJECT_INVALID"))
                .andExpect(jsonPath("$.diagnostics.length()").value(0))
                .andExpect(content().string(not(containsString("SECRET_INTERNAL"))))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    @Test
    void sanitizesArchiveFailures() throws Exception {
        Fixture fixture = fixture();
        when(fixture.service.generate(eq(fixture.projectId), eq(7L), any(), any()))
                .thenThrow(new SpringArchiveException(
                        "Could not write deterministic Spring Boot archive",
                        new IOException("SECRET_ARCHIVE_DETAIL")
                ));

        fixture.mvc.perform(validRequest(fixture))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("ARCHIVE_FAILED"))
                .andExpect(jsonPath("$.diagnostics.length()").value(0))
                .andExpect(content().string(not(containsString("SECRET_ARCHIVE_DETAIL"))))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validRequest(Fixture fixture) {
        return post(path(fixture))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"baseRevision\":7,\"artifactName\":\"biblioteca\",\"basePackage\":\"com.example.biblioteca\"}");
    }

    private Fixture fixture() {
        SpringBootGenerationService service = mock(SpringBootGenerationService.class);
        ProjectAccessService access = mock(ProjectAccessService.class);
        CurrentUser user = mock(CurrentUser.class);
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(user.id()).thenReturn(userId);

        return new Fixture(
                MockMvcBuilders.standaloneSetup(
                        new SpringBootGenerationController(service, access, user)
                ).build(),
                service,
                access,
                userId,
                projectId
        );
    }

    private String path(Fixture fixture) {
        return "/api/projects/" + fixture.projectId + "/generation/spring-boot";
    }

    private record Fixture(
            MockMvc mvc,
            SpringBootGenerationService service,
            ProjectAccessService access,
            UUID userId,
            UUID projectId
    ) {
    }
}
