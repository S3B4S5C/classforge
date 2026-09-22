package com.classforge.generation.spring.frontend;

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

class AngularFrontendRenderingTests {
    @Test
    void rendersSpecificEntityComponentsDashboardAndSelectedPrimaryColor() {
        Fixture fixture = fixture();
        SpringProjectRenderer renderer = new SpringProjectRenderer(
                new SpringFreeMarkerRenderer(),
                new GeneratedProjectValidator()
        );

        GeneratedProject simple = renderer.render(
                fixture.model(),
                SpringBootGenerationOptions.simpleCrud(false, "#7C3AED")
        );

        assertNotNull(file(simple, "frontend/package.json"));
        assertNotNull(file(simple, "frontend/src/app/dashboard/dashboard.component.ts"));
        assertNotNull(file(simple, "frontend/src/app/entities/usuario/usuario-list.component.ts"));
        assertNotNull(file(simple, "frontend/src/app/entities/usuario/usuario-detail.component.ts"));
        assertNotNull(file(simple, "frontend/src/app/entities/usuario/usuario-form.component.ts"));
        assertNotNull(file(simple, "frontend/src/app/entities/usuario/usuario.api.ts"));
        assertNotNull(file(simple, "frontend/src/app/entities/usuario/usuario.models.ts"));
        assertNull(file(simple, "frontend/src/app/core/auth/auth.service.ts"));
        assertTrue(text(simple, "frontend/src/styles.css").contains("--app-primary: #7C3AED;"));
        String dashboard = text(simple, "frontend/src/app/dashboard/dashboard.component.ts");
        assertTrue(dashboard.contains("endpoint: '/api/usuario'"));
        assertTrue(dashboard.contains("`${entity.endpoint}/count`"));
        String tsconfig = text(simple, "frontend/tsconfig.json");
        assertFalse(tsconfig.contains("\"baseUrl\""));
        assertFalse(tsconfig.contains("\"downlevelIteration\""));
        assertTrue(text(simple, "frontend/src/app/entities/usuario/usuario-list.component.ts")
                .contains("filters['username'] ?? ''"));
        assertFalse(text(simple, "frontend/src/app/entities/usuario/usuario-form.component.ts")
                .contains("formControlName=\"id\""));

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
        assertNotNull(file(auth, "frontend/src/app/auth/login.component.ts"));
        assertNotNull(file(auth, "frontend/src/app/auth/bootstrap.component.ts"));
        assertNotNull(file(auth, "frontend/src/app/core/auth/auth.guard.ts"));
        assertNotNull(file(auth, "frontend/src/app/core/auth/auth.interceptor.ts"));
        assertTrue(text(auth, "frontend/src/app/entities/usuario/usuario-form.component.ts")
                .contains("type=\"password\" formControlName=\"password\""));
        assertFalse(text(auth, "frontend/src/app/auth/bootstrap.component.ts")
                .contains("formControlName=\"id\""));
        String app = text(auth, "frontend/src/app/app.component.ts");
        assertTrue(app.contains("@if (auth.authenticated())"));
        assertTrue(app.indexOf("@if (auth.authenticated())") < app.indexOf("<header class=\"app-header\">"));
        String assistant = text(auth, "frontend/src/app/assistant/assistant.component.ts");
        assertTrue(assistant.contains("class=\"btn btn-primary\" (click)=\"send()\""));
        assertTrue(assistant.contains("class=\"btn\" (click)=\"startVoice()\""));
        assertTrue(text(auth, "frontend/src/styles.css").contains("--app-primary: #0F766E;"));
    }

    @Test
    void rendersStateSafeGeneratedControlsAndReferenceData() {
        List<GeneratedFile> files = new AngularFrontendRenderer().render(
                GeneratedUiRegressionFixture.manifest(false),
                "ui-regression",
                "#0F766E"
        );

        String main = text(files, "frontend/src/main.ts");
        assertTrue(main.contains("provideZoneChangeDetection()"));

        String list = text(files, "frontend/src/app/entities/person/person-list.component.ts");
        assertTrue(list.contains("filters['name'] ?? ''"));
        assertTrue(list.contains("private listRequest?: Subscription"));
        assertTrue(list.contains("this.listRequest?.unsubscribe()"));

        String form = text(files, "frontend/src/app/entities/person/person-form.component.ts");
        assertTrue(form.contains("this.teamOptions = []"));
        assertTrue(form.contains("readonly teamLabelFields = ['name']"));
        assertTrue(form.contains("references.withSelected(teamOptions, form.controls.teamId.value, teamIdFields)"));
        assertTrue(form.contains("references.optionLabel(option, teamIdFields, teamLabelFields)"));
        assertTrue(form.contains("this.recordRequest?.unsubscribe()"));
        assertTrue(form.contains("if (!this.editing) this.applyCreateDefaults()"));
        assertTrue(form.contains("birthDate: local.slice(0, 10)"));
        assertTrue(form.contains("appointmentAt: local.slice(0, 16)"));
        assertTrue(form.contains("referencesLoading"));

        String references = text(files, "frontend/src/app/core/api/reference-data.service.ts");
        assertTrue(references.contains("expand((page) =>"));
        assertTrue(references.contains("withSelected("));
        assertTrue(references.contains("for (const field of labelFields)"));
        assertFalse(references.contains("(${idFields.map"));
    }

    private GeneratedFile file(GeneratedProject project, String path) {
        return project.files().stream().filter(candidate -> candidate.path().equals(path)).findFirst().orElse(null);
    }

    private String text(GeneratedProject project, String path) {
        GeneratedFile file = file(project, path);
        assertNotNull(file, path);
        return new String(file.content(), StandardCharsets.UTF_8);
    }

    private String text(List<GeneratedFile> files, String path) {
        GeneratedFile file = files.stream().filter(candidate -> candidate.path().equals(path)).findFirst().orElse(null);
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
