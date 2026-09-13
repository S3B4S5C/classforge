package com.classforge.generation.spring.rendering;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedProject;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.generated.GeneratedProjectException;
import com.classforge.generation.spring.generated.GeneratedFileType;
import com.classforge.generation.spring.model.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class DomainManifestRenderingTests {
    @Test
    void rendersSchemaV1DeterministicallyWithCanonicalOperationParity() throws Exception {
        UUID classId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        UUID idId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");
        UUID nameId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa3");
        SpringEntityIdModel id = new SpringEntityIdModel(
                SpringIdKind.SIMPLE, SpringJavaType.UUID, null,
                List.of(new SpringIdFieldModel(idId, "id", "id", SpringJavaType.UUID)), true
        );
        SpringEntityModel entity = new SpringEntityModel(
                classId, "Cliente", "Cliente", "cliente",
                new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()), id,
                List.of(
                        new SpringScalarFieldModel(idId, "id", "id", "id", SpringJavaType.UUID, false, true),
                        new SpringScalarFieldModel(nameId, "nombre", "nombre", "nombre", SpringJavaType.STRING, false, false)
                ), List.of(), List.of(), List.of(), List.of()
        );
        SpringGenerationModel model = new SpringGenerationModel(
                "1.0", "manifest-render", "com.example.render", "RenderApplication", "21", "4.0.8", "9.2.0",
                List.of(entity), List.of(new SpringRepositoryModel("ClienteRepository", "Cliente", "UUID", "java.util.UUID", false))
        );
        SpringProjectRenderer renderer = new SpringProjectRenderer(new SpringFreeMarkerRenderer(), new GeneratedProjectValidator());
        GeneratedProject first = renderer.render(model, SpringBootGenerationOptions.simpleCrud(false));
        GeneratedProject second = renderer.render(model, SpringBootGenerationOptions.simpleCrud(false));

        String manifest = text(first, "domain-manifest.json");
        assertEquals(manifest, text(second, "domain-manifest.json"));
        JsonNode root = JsonMapper.builder().build().readTree(manifest);
        assertEquals("1.0", root.get("schemaVersion").asText());
        assertEquals("SIMPLE_CRUD", root.get("generationMode").asText());
        assertFalse(root.get("authentication").get("enabled").asBoolean());
        assertEquals(classId.toString(), root.get("entities").get(0).get("id").asText());
        assertEquals("Cliente", root.get("entities").get(0).get("displayName").asText());
        assertEquals(0, root.get("entities").get(0).get("aliases").size());
        assertEquals(6, root.get("operations").size());
        assertTrue(first.files().stream().anyMatch(file -> file.path().equals("openapi.yaml")));
        assertTrue(first.files().stream().anyMatch(file -> file.path().equals("postman_collection.json")));
    }

    @Test
    void validatorFailsClosedWhenManifestOperationsDrift() {
        UUID classId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb1");
        UUID idId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbb2");
        SpringEntityIdModel id = new SpringEntityIdModel(
                SpringIdKind.SIMPLE, SpringJavaType.UUID, null,
                List.of(new SpringIdFieldModel(idId, "id", "id", SpringJavaType.UUID)), true
        );
        SpringEntityModel entity = new SpringEntityModel(
                classId, "Item", "Item", "item", new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()), id,
                List.of(new SpringScalarFieldModel(idId, "id", "id", "id", SpringJavaType.UUID, false, true)),
                List.of(), List.of(), List.of(), List.of()
        );
        SpringGenerationModel model = new SpringGenerationModel(
                "1.0", "manifest-validator", "com.example.validator", "ValidatorApplication", "21", "4.0.8", "9.2.0",
                List.of(entity), List.of(new SpringRepositoryModel("ItemRepository", "Item", "UUID", "java.util.UUID", false))
        );
        GeneratedProjectValidator validator = new GeneratedProjectValidator();
        SpringProjectRenderer renderer = new SpringProjectRenderer(new SpringFreeMarkerRenderer(), validator);
        GeneratedProject project = renderer.render(model, SpringBootGenerationOptions.simpleCrud(false));
        List<GeneratedFile> tampered = project.files().stream().map(file -> {
            if (!file.path().equals("domain-manifest.json")) return file;
            String json = new String(file.content(), StandardCharsets.UTF_8)
                    .replace("\"listItem\"", "\"listItemTampered\"");
            return new GeneratedFile(file.path(), GeneratedFileType.TEXT, json.getBytes(StandardCharsets.UTF_8));
        }).toList();

        GeneratedProjectException failure = assertThrows(GeneratedProjectException.class,
                () -> validator.validate(new GeneratedProject(project.artifactName(), tampered)));
        assertTrue(failure.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().name().equals("DOMAIN_MANIFEST_OPERATION_MISMATCH")));
    }

    private String text(GeneratedProject project, String path) {
        GeneratedFile file = project.files().stream().filter(candidate -> candidate.path().equals(path)).findFirst().orElseThrow();
        return new String(file.content(), StandardCharsets.UTF_8);
    }
}
