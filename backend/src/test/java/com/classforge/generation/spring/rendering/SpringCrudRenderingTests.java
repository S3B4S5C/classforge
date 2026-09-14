package com.classforge.generation.spring.rendering;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedProject;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.model.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpringCrudRenderingTests {
    @Test
    void rendersSimpleCrudWithoutSecurityAndAuthWithoutPasswordLeak() {
        Fixture fixture = fixture();
        SpringProjectRenderer renderer = new SpringProjectRenderer(new SpringFreeMarkerRenderer(), new GeneratedProjectValidator());

        GeneratedProject simple = renderer.render(fixture.model, SpringBootGenerationOptions.simpleCrud(false));
        assertNotNull(file(simple, "src/main/java/com/example/demo/controller/UsuarioController.java"));
        assertNotNull(file(simple, "src/main/java/com/example/demo/service/UsuarioService.java"));
        assertNull(file(simple, "src/main/java/com/example/demo/security/SecurityConfig.java"));
        String simpleController = text(simple, "src/main/java/com/example/demo/controller/UsuarioController.java");
        String simpleService = text(simple, "src/main/java/com/example/demo/service/UsuarioService.java");
        assertFalse(simpleController.contains(".entity.*"));
        assertFalse(simpleService.contains(".entity.*"));
        assertFalse(simpleService.contains(".repository.*"));
        assertTrue(simpleController.contains("import java.util.UUID;"));
        assertTrue(simpleService.contains("import java.util.UUID;"));
        String simpleRequest = text(simple, "src/main/java/com/example/demo/dto/UsuarioRequest.java");
        assertFalse(simpleRequest.contains("UUID id"));
        assertFalse(simpleService.contains("request.id()"));
        String simpleEntity = text(simple, "src/main/java/com/example/demo/entity/Usuario.java");
        assertTrue(simpleEntity.contains("@GeneratedValue(strategy = GenerationType.UUID)"));

        GeneratedProject auth = renderer.render(
                fixture.model,
                SpringBootGenerationOptions.authenticated(false, fixture.classId, fixture.usernameId, fixture.passwordId)
        );
        assertNotNull(file(auth, "src/main/java/com/example/demo/security/SecurityConfig.java"));
        assertNotNull(file(auth, "src/main/java/com/example/demo/controller/AuthController.java"));
        String response = text(auth, "src/main/java/com/example/demo/dto/UsuarioResponse.java");
        assertTrue(response.contains("String username"));
        assertFalse(response.contains("password"));
        String service = text(auth, "src/main/java/com/example/demo/service/UsuarioService.java");
        assertTrue(service.contains("passwordEncoder.encode"));
        assertTrue(service.contains("requireUniqueUsername"));
    }

    private GeneratedFile file(GeneratedProject project, String path) {
        return project.files().stream().filter(candidate -> candidate.path().equals(path)).findFirst().orElse(null);
    }

    private String text(GeneratedProject project, String path) {
        GeneratedFile file = file(project, path);
        assertNotNull(file, path);
        return new String(file.content(), StandardCharsets.UTF_8);
    }

    private Fixture fixture() {
        UUID classId = UUID.randomUUID();
        UUID idId = UUID.randomUUID();
        UUID usernameId = UUID.randomUUID();
        UUID passwordId = UUID.randomUUID();
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
                "1.0", "demo", "com.example.demo", "DemoApplication", "21", "4.0.8", "9.2.0",
                List.of(entity),
                List.of(new SpringRepositoryModel("UsuarioRepository", "Usuario", "UUID", "java.util.UUID", false))
        );
        return new Fixture(model, classId, usernameId, passwordId);
    }

    private record Fixture(SpringGenerationModel model, UUID classId, UUID usernameId, UUID passwordId) { }
}
