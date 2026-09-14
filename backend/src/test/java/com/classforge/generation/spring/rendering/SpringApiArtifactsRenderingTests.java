package com.classforge.generation.spring.rendering;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedProject;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.generated.GeneratedProjectException;
import com.classforge.generation.spring.generated.GeneratedFileType;
import com.classforge.generation.spring.model.SpringEntityIdModel;
import com.classforge.generation.spring.model.SpringEntityModel;
import com.classforge.generation.spring.model.SpringGenerationModel;
import com.classforge.generation.spring.model.SpringIdFieldModel;
import com.classforge.generation.spring.model.SpringIdKind;
import com.classforge.generation.spring.model.SpringInheritanceKind;
import com.classforge.generation.spring.model.SpringInheritanceModel;
import com.classforge.generation.spring.model.SpringJavaType;
import com.classforge.generation.spring.model.SpringRepositoryModel;
import com.classforge.generation.spring.model.SpringScalarFieldModel;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class SpringApiArtifactsRenderingTests {
    @Test
    void rendersDeterministicOpenApiAndPostmanForSimpleAndAuthModes() throws Exception {
        Fixture fixture = fixture();
        SpringProjectRenderer renderer = new SpringProjectRenderer(
                new SpringFreeMarkerRenderer(),
                new GeneratedProjectValidator()
        );

        GeneratedProject simple = renderer.render(fixture.model, SpringBootGenerationOptions.simpleCrud(false));
        String simpleOpenApi = text(simple, "openapi.yaml");
        String simplePostman = text(simple, "postman_collection.json");
        assertTrue(simpleOpenApi.startsWith("openapi: 3.0.3\n"));
        assertTrue(simpleOpenApi.contains("operationId: listUsuario"));
        assertFalse(simpleOpenApi.contains("securitySchemes:"));
        assertFalse(simpleOpenApi.contains("/api/auth/login"));
        JsonNode simpleCollection = JsonMapper.builder().build().readTree(simplePostman);
        assertEquals("https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
                simpleCollection.get("info").get("schema").asText());
        assertFalse(simplePostman.contains("{{jwt}}"));

        SpringBootGenerationOptions authOptions = SpringBootGenerationOptions.authenticated(
                false, fixture.classId, fixture.usernameId, fixture.passwordId
        );
        GeneratedProject auth = renderer.render(fixture.model, authOptions);
        GeneratedProject authAgain = renderer.render(fixture.model, authOptions);
        String authOpenApi = text(auth, "openapi.yaml");
        int requestStart = authOpenApi.indexOf("  UsuarioRequest:");
        int responseStart = authOpenApi.indexOf("  UsuarioResponse:");
        assertTrue(requestStart >= 0 && responseStart > requestStart);
        String requestSchema = authOpenApi.substring(requestStart, responseStart);
        assertFalse(requestSchema.contains("    id:"), "UsuarioRequest must not expose generated id");
        String authPostman = text(auth, "postman_collection.json");
        assertEquals(authOpenApi, text(authAgain, "openapi.yaml"));
        assertEquals(authPostman, text(authAgain, "postman_collection.json"));
        assertTrue(authOpenApi.contains("bearerAuth:"));
        assertTrue(authOpenApi.contains("'/api/auth/bootstrap':"));
        assertTrue(authOpenApi.contains("'/api/auth/login':"));
        assertTrue(authOpenApi.contains("writeOnly: true"));
        String responseSection = between(authOpenApi, "  UsuarioResponse:", "  UsuarioPageResponse:");
        assertFalse(responseSection.contains("password:"));
        assertTrue(authPostman.contains("bootstrapAuthentication"));
        assertTrue(authPostman.contains("loginAuthentication"));
        assertTrue(authPostman.contains("pm.collectionVariables.set('jwt'"));
        assertTrue(authPostman.contains("{{jwt}}"));
    }


    @Test
    void generatedProjectValidatorFailsClosedWhenOpenApiAndPostmanOperationsDrift() {
        Fixture fixture = fixture();
        GeneratedProjectValidator validator = new GeneratedProjectValidator();
        SpringProjectRenderer renderer = new SpringProjectRenderer(new SpringFreeMarkerRenderer(), validator);
        GeneratedProject project = renderer.render(fixture.model, SpringBootGenerationOptions.simpleCrud(false));
        List<GeneratedFile> tampered = project.files().stream().map(file -> {
            if (!file.path().equals("postman_collection.json")) return file;
            String json = new String(file.content(), StandardCharsets.UTF_8)
                    .replace("\"listUsuario\"", "\"listUsuarioTampered\"");
            return new GeneratedFile(file.path(), GeneratedFileType.TEXT, json.getBytes(StandardCharsets.UTF_8));
        }).toList();

        GeneratedProjectException failure = assertThrows(GeneratedProjectException.class,
                () -> validator.validate(new GeneratedProject(project.artifactName(), tampered)));
        assertTrue(failure.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.code().name().equals("API_CONTRACT_OPERATION_MISMATCH")));
    }

    private String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from + start.length());
        assertTrue(from >= 0 && to > from, "Expected generated OpenAPI section");
        return source.substring(from, to);
    }

    private String text(GeneratedProject project, String path) {
        GeneratedFile file = project.files().stream()
                .filter(candidate -> candidate.path().equals(path))
                .findFirst().orElseThrow();
        return new String(file.content(), StandardCharsets.UTF_8);
    }

    private Fixture fixture() {
        UUID classId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID idId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID usernameId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID passwordId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        SpringIdFieldModel idField = new SpringIdFieldModel(idId, "id", "id", SpringJavaType.UUID);
        SpringEntityIdModel id = new SpringEntityIdModel(SpringIdKind.SIMPLE, SpringJavaType.UUID, null, List.of(idField), true);
        SpringEntityModel entity = new SpringEntityModel(
                classId, "Usuario", "Usuario", "usuario",
                new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()),
                id,
                List.of(
                        new SpringScalarFieldModel(idId, "id", "id", "id", SpringJavaType.UUID, false, true),
                        new SpringScalarFieldModel(usernameId, "username", "username", "username", SpringJavaType.STRING, false, false),
                        new SpringScalarFieldModel(passwordId, "password", "password", "password", SpringJavaType.STRING, false, false)
                ),
                List.of(), List.of(), List.of(), List.of()
        );
        SpringGenerationModel model = new SpringGenerationModel(
                "1.0", "cu15-rendering", "com.example.cu15", "Cu15Application", "21", "4.0.8", "9.2.0",
                List.of(entity),
                List.of(new SpringRepositoryModel("UsuarioRepository", "Usuario", "UUID", "java.util.UUID", false))
        );
        return new Fixture(model, classId, usernameId, passwordId);
    }

    private record Fixture(SpringGenerationModel model, UUID classId, UUID usernameId, UUID passwordId) { }
}
