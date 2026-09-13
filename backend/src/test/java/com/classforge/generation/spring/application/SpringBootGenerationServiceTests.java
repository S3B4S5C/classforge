package com.classforge.generation.spring.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.classforge.generation.relational.*;
import com.classforge.generation.spring.archive.*;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.model.SpringGenerationConfig;
import com.classforge.generation.spring.planning.*;
import com.classforge.generation.spring.rendering.*;
import com.classforge.project.application.ProjectRevisionConflictException;
import com.classforge.project.domain.Project;
import com.classforge.project.domain.document.*;
import com.classforge.project.persistence.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;

class SpringBootGenerationServiceTests {
    @Test
    void generatesReadOnlyDeterministicArtifactFromSingleProjectSnapshot() throws Exception {
        Fixture fixture = fixture(7, document(true));
        SpringBootGenerationArtifact first = fixture.service.generate(fixture.project.id(), 7, config());
        SpringBootGenerationArtifact second = fixture.service.generate(fixture.project.id(), 7, config());

        assertEquals(7, first.projectRevision());
        assertEquals("biblioteca-backend.zip", first.fileName());
        assertEquals("application/zip", first.mediaType());
        assertTrue(first.content().length > 0);
        assertArrayEquals(first.content(), second.content());
        assertArrayEquals(sha(first.content()), sha(second.content()));
        assertEquals(7, fixture.project.revision());
        assertEquals(fixture.document, fixture.project.document());
        verify(fixture.repository, times(2)).findById(fixture.project.id());
        verifyNoMoreInteractions(fixture.repository);
    }

    @Test
    void rejectsBothStaleAndFutureRevisionsBeforeArchive() {
        Fixture fixture = fixture(12, document(true));
        assertThrows(
                ProjectRevisionConflictException.class,
                () -> fixture.service.generate(fixture.project.id(), 11, config())
        );
        assertThrows(
                ProjectRevisionConflictException.class,
                () -> fixture.service.generate(fixture.project.id(), 13, config())
        );
    }

    @Test
    void offersFallbackWhenMissingIdentifiersAreTheOnlyProblemAndEveryAffectedClassHasAttributes() {
        ProjectDocument document = document(false);
        Fixture fixture = fixture(7, document);

        SpringBootGenerationReadinessException exception = assertThrows(
                SpringBootGenerationReadinessException.class,
                () -> fixture.service.generate(fixture.project.id(), 7, config())
        );

        assertEquals(1, exception.diagnostics().size());
        assertEquals(
                RelationalMappingDiagnosticCode.CLASS_IDENTIFIER_REQUIRED,
                exception.diagnostics().get(0).code()
        );
        assertEquals(1, exception.primaryKeyFallbacks().size());
        assertEquals("Cliente", exception.primaryKeyFallbacks().get(0).className());
        assertEquals("id", exception.primaryKeyFallbacks().get(0).attributeName());
        assertFalse(document.umlModel().classes().get(0).attributes().get(0).identifier());
    }

    @Test
    void explicitFallbackUsesFirstAttributeAsPrimaryKeyWithoutMutatingCanonicalUml() throws Exception {
        ProjectDocument document = document(false);
        Fixture fixture = fixture(7, document);

        SpringBootGenerationArtifact artifact = fixture.service.generate(
                fixture.project.id(),
                7,
                config(),
                new SpringBootGenerationOptions(true)
        );

        String source = zipEntry(
                artifact.content(),
                "biblioteca/src/main/java/com/example/biblioteca/entity/Cliente.java"
        );
        assertTrue(source.contains("@Id\n    @Column(name = \"id\", nullable = false)"));
        assertTrue(source.contains("private UUID id;"));
        assertFalse(document.umlModel().classes().get(0).attributes().get(0).identifier());
        assertEquals(7, fixture.project.revision());
        assertEquals(document, fixture.project.document());
    }

    @Test
    void doesNotOfferFallbackWhenIdentifierlessClassHasNoAttributes() {
        UmlClass empty = new UmlClass(UUID.randomUUID(), "Vacia", List.of());
        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(List.of(empty), List.of()),
                DiagramLayout.empty()
        );
        Fixture fixture = fixture(7, document);

        RelationalMappingException exception = assertThrows(
                RelationalMappingException.class,
                () -> fixture.service.generate(fixture.project.id(), 7, config())
        );
        assertTrue(exception.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == RelationalMappingDiagnosticCode.CLASS_IDENTIFIER_REQUIRED));
    }

    @Test
    void doesNotOfferFallbackWhenAnyOtherRelationalProblemExists() {
        UmlAttribute custom = new UmlAttribute(
                UUID.randomUUID(),
                "valor",
                UmlDataType.CUSTOM,
                "Money",
                UmlVisibility.PRIVATE,
                true,
                false
        );
        UmlClass invalid = new UmlClass(UUID.randomUUID(), "Pago", List.of(custom));
        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(List.of(invalid), List.of()),
                DiagramLayout.empty()
        );
        Fixture fixture = fixture(7, document);

        RelationalMappingException exception = assertThrows(
                RelationalMappingException.class,
                () -> fixture.service.generate(fixture.project.id(), 7, config())
        );
        assertTrue(exception.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == RelationalMappingDiagnosticCode.CUSTOM_TYPE_UNSUPPORTED));
    }

    @Test
    void explicitFallbackStillFailsClosedForOtherRelationalProblems() {
        UmlAttribute custom = new UmlAttribute(
                UUID.randomUUID(),
                "valor",
                UmlDataType.CUSTOM,
                "Money",
                UmlVisibility.PRIVATE,
                true,
                false
        );
        UmlClass invalid = new UmlClass(UUID.randomUUID(), "Pago", List.of(custom));
        ProjectDocument document = new ProjectDocument(
                "1.0",
                new UmlModel(List.of(invalid), List.of()),
                DiagramLayout.empty()
        );
        Fixture fixture = fixture(7, document);

        RelationalMappingException exception = assertThrows(
                RelationalMappingException.class,
                () -> fixture.service.generate(
                        fixture.project.id(),
                        7,
                        config(),
                        new SpringBootGenerationOptions(true)
                )
        );
        assertTrue(exception.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code() == RelationalMappingDiagnosticCode.CUSTOM_TYPE_UNSUPPORTED));
    }

    @Test
    void keepsZipContentIndependentOfProjectRevision() throws Exception {
        ProjectDocument document = document(true);
        Fixture revisionSeven = fixture(7, document);
        Fixture revisionEight = fixture(8, document);
        SpringBootGenerationArtifact first = revisionSeven.service.generate(
                revisionSeven.project.id(),
                7,
                config()
        );
        SpringBootGenerationArtifact second = revisionEight.service.generate(
                revisionEight.project.id(),
                8,
                config()
        );
        assertEquals(7, first.projectRevision());
        assertEquals(8, second.projectRevision());
        assertArrayEquals(first.content(), second.content());
        assertArrayEquals(sha(first.content()), sha(second.content()));
    }

    private Fixture fixture(long revision, ProjectDocument document) {
        ProjectRepository repository = mock(ProjectRepository.class);
        ProjectMapper mapper = mock(ProjectMapper.class);
        UUID id = UUID.randomUUID();
        Project project = new Project(
                id,
                UUID.randomUUID(),
                "Project",
                revision,
                document,
                Instant.EPOCH,
                Instant.EPOCH
        );
        ProjectEntity entity = mock(ProjectEntity.class);
        when(repository.findById(id)).thenReturn(Optional.of(entity));
        when(mapper.toDomain(entity)).thenReturn(project);

        RelationalModelMapper relational = new RelationalModelMapper(
                new RelationalNamingStrategy(),
                new RelationalModelValidator()
        );
        SpringGenerationPlanner planner = new SpringGenerationPlanner(
                new SpringJavaNamingStrategy(),
                new com.classforge.generation.spring.validation.SpringGenerationModelValidator()
        );
        GeneratedProjectValidator validator = new GeneratedProjectValidator();
        SpringProjectRenderer renderer = new SpringProjectRenderer(
                new SpringFreeMarkerRenderer(),
                validator
        );
        return new Fixture(
                new SpringBootGenerationService(
                        repository,
                        mapper,
                        relational,
                        planner,
                        renderer,
                        new DeterministicZipWriter(validator)
                ),
                repository,
                project,
                document
        );
    }

    private ProjectDocument document(boolean identifier) {
        UmlAttribute id = new UmlAttribute(
                UUID.randomUUID(),
                "id",
                UmlDataType.UUID,
                null,
                UmlVisibility.PRIVATE,
                false,
                identifier
        );
        UmlAttribute name = new UmlAttribute(
                UUID.randomUUID(),
                "nombre",
                UmlDataType.STRING,
                null,
                UmlVisibility.PRIVATE,
                true,
                false
        );
        return new ProjectDocument(
                "1.0",
                new UmlModel(
                        List.of(new UmlClass(UUID.randomUUID(), "Cliente", List.of(id, name))),
                        List.of()
                ),
                DiagramLayout.empty()
        );
    }

    private SpringGenerationConfig config() {
        return new SpringGenerationConfig("biblioteca", "com.example.biblioteca");
    }

    private byte[] sha(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    private String zipEntry(byte[] bytes, String expectedName) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(expectedName)) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        fail("Missing ZIP entry: " + expectedName);
        return "";
    }

    private record Fixture(
            SpringBootGenerationService service,
            ProjectRepository repository,
            Project project,
            ProjectDocument document
    ) { }
}
