package com.classforge.generation.spring.mobile;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedProject;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.model.*;
import com.classforge.generation.spring.rendering.SpringFreeMarkerRenderer;
import com.classforge.generation.spring.rendering.SpringProjectRenderer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlutterMobileRenderingTests {
    @Test
    void rendersEntitySpecificFlutterDashboardAndroidAndSecureAuth() {
        Fixture fixture = fixture();
        SpringProjectRenderer renderer = new SpringProjectRenderer(
                new SpringFreeMarkerRenderer(),
                new GeneratedProjectValidator()
        );

        GeneratedProject simple = renderer.render(
                fixture.model(),
                SpringBootGenerationOptions.simpleCrud(false, "#7C3AED")
        );
        assertNotNull(file(simple, "mobile/pubspec.yaml"));
        assertNotNull(file(simple, "mobile/lib/dashboard/dashboard_page.dart"));
        assertNotNull(file(simple, "mobile/lib/entities/usuario/usuario_list_page.dart"));
        assertNotNull(file(simple, "mobile/lib/entities/usuario/usuario_detail_page.dart"));
        assertNotNull(file(simple, "mobile/lib/entities/usuario/usuario_form_page.dart"));
        assertNotNull(file(simple, "mobile/lib/entities/usuario/usuario_api.dart"));
        assertNotNull(file(simple, "mobile/lib/entities/usuario/usuario_model.dart"));
        assertNotNull(file(simple, "mobile/android/app/src/main/AndroidManifest.xml"));
        String wrapper = text(simple, "mobile/android/gradle/wrapper/gradle-wrapper.properties");
        assertTrue(wrapper.contains("gradle-9.3.1-all.zip"));
        assertFalse(wrapper.contains("gradle-9.1.0-all.zip"));
        assertNull(file(simple, "mobile/lib/core/auth/token_store.dart"));
        assertFalse(text(simple, "mobile/pubspec.yaml").contains("flutter_secure_storage:"));
        assertTrue(text(simple, "mobile/lib/core/theme/app_theme.dart").contains("Color(0xFF7C3AED)"));
        String dashboard = text(simple, "mobile/lib/dashboard/dashboard_page.dart");
        assertTrue(dashboard.contains("/api/usuario"));
        assertTrue(dashboard.contains("${entity.endpoint}/count"));
        assertTrue(text(simple, "mobile/README.md").contains("plataforma formalmente aceptada: Android"));
        assertFalse(text(simple, "mobile/lib/entities/usuario/usuario_form_page.dart").contains("idController"));

        GeneratedProject auth = renderer.render(
                fixture.model(),
                SpringBootGenerationOptions.authenticated(
                        false,
                        fixture.classId(),
                        fixture.usernameId(),
                        fixture.passwordId(),
                        "#0F766E"
                )
        );
        assertNotNull(file(auth, "mobile/lib/auth/login_page.dart"));
        assertNotNull(file(auth, "mobile/lib/auth/bootstrap_page.dart"));
        assertNotNull(file(auth, "mobile/lib/core/auth/auth_gate.dart"));
        assertTrue(text(auth, "mobile/lib/core/auth/token_store.dart").contains("FlutterSecureStorage"));
        assertTrue(text(auth, "mobile/pubspec.yaml").contains("flutter_secure_storage: ^11.1.1"));
        assertTrue(text(auth, "mobile/android/app/build.gradle.kts").contains("minSdk = 23"));
        assertTrue(text(auth, "mobile/lib/auth/login_page.dart").contains("obscureText: true"));
        assertFalse(text(auth, "mobile/lib/entities/usuario/usuario_form_page.dart").contains("idController"));
        assertFalse(text(auth, "mobile/lib/auth/bootstrap_page.dart").contains("'id'"));
        assertTrue(text(auth, "mobile/lib/core/theme/app_theme.dart").contains("Color(0xFF0F766E)"));
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
        UUID classId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID idId = UUID.fromString("11111111-1111-1111-1111-111111111112");
        UUID usernameId = UUID.fromString("11111111-1111-1111-1111-111111111113");
        UUID passwordId = UUID.fromString("11111111-1111-1111-1111-111111111114");
        SpringEntityIdModel id = new SpringEntityIdModel(
                SpringIdKind.SIMPLE,
                SpringJavaType.UUID,
                null,
                List.of(new SpringIdFieldModel(idId, "id", "id", SpringJavaType.UUID)),
                true
        );
        SpringEntityModel entity = new SpringEntityModel(
                classId,
                "Usuario",
                "Usuario",
                "usuario",
                new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()),
                id,
                List.of(
                        new SpringScalarFieldModel(idId, "id", "id", "id", SpringJavaType.UUID, false, true),
                        new SpringScalarFieldModel(usernameId, "username", "username", "username", SpringJavaType.STRING, false, false),
                        new SpringScalarFieldModel(passwordId, "password", "password", "password", SpringJavaType.STRING, false, false)
                ),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        SpringGenerationModel model = new SpringGenerationModel(
                "1.0",
                "frontend-demo",
                "com.example.frontend",
                "FrontendApplication",
                "21",
                "4.0.8",
                "9.2.0",
                List.of(entity),
                List.of(new SpringRepositoryModel(
                        "UsuarioRepository", "Usuario", "UUID", "java.util.UUID", false
                ))
        );
        return new Fixture(model, classId, usernameId, passwordId);
    }

    private record Fixture(
            SpringGenerationModel model,
            UUID classId,
            UUID usernameId,
            UUID passwordId
    ) { }
}
