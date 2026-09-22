package com.classforge.generation.spring.mobile;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.domain.DomainManifestPlan;
import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedProject;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.frontend.GeneratedUiRegressionFixture;
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
        String simpleUsuarioForm = text(simple, "mobile/lib/entities/usuario/usuario_form_page.dart");
        assertFalse(simpleUsuarioForm.contains("idController"));
        assertFalse(simpleUsuarioForm.contains("requiredSelection: rolId"));

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

    @Test
    void rendersStateSafeMobileListsAndReferencePickers() {
        List<GeneratedFile> files = new FlutterMobileRenderer().render(
                GeneratedUiRegressionFixture.manifest(false),
                "ui-regression",
                "com.example.regression",
                "#0F766E"
        );

        String list = text(files, "mobile/lib/entities/person/person_list_page.dart");
        assertTrue(list.contains("requestVersion"));
        assertTrue(list.contains("appliedSearch"));
        assertTrue(list.contains("Pagina ${pageIndex + 1}"));
        assertEquals(1, occurrences(list, "Pagina ${pageIndex + 1}"));

        String form = text(files, "mobile/lib/entities/person/person_form_page.dart");
        assertTrue(form.contains("teamIdValue = widget.existing?.data['teamId']"));
        assertTrue(form.contains("key: const ValueKey('teamId')"));
        assertTrue(form.contains("labelFields: const ['name']"));
        assertTrue(form.contains("birthDateController.text = widget.existing == null ? DateTime.now().toIso8601String().substring(0, 10)"));
        assertTrue(form.contains("appointmentAtController.text = widget.existing == null ? DateTime.now().toIso8601String().substring(0, 16)"));
        assertTrue(form.contains("enabled: !saving"));
        assertTrue(form.contains("requiredSelection: false"));
        assertFalse(form.contains("requiredSelection: teamId"));
        assertFalse(form.contains("requiredSelection: roleIds"));

        DomainManifestPlan manifest = GeneratedUiRegressionFixture.manifest(false);
        DomainManifestPlan.Entity person = manifest.entities().stream()
                .filter(entity -> "Person".equals(entity.codeName()))
                .findFirst().orElseThrow();
        DomainManifestPlan.Relation optionalTeam = person.relations().stream()
                .filter(relation -> "team".equals(relation.name()))
                .findFirst().orElseThrow();
        DomainManifestPlan.Relation requiredTeam = new DomainManifestPlan.Relation(
                optionalTeam.id(), optionalTeam.umlType(), optionalTeam.kind(), optionalTeam.name(),
                optionalTeam.targetEntityId(), optionalTeam.targetEntityName(), false, optionalTeam.requestField(),
                optionalTeam.targetIdentifier(), optionalTeam.onDeleteCascade()
        );
        String requiredWidget = new FlutterEntityFilesRenderer().relationWidget(requiredTeam, manifest);
        assertTrue(requiredWidget.contains("requiredSelection: true"));
        assertFalse(requiredWidget.contains("requiredSelection: teamId"));

        String picker = text(files, "mobile/lib/core/widgets/reference_picker.dart");
        assertTrue(picker.contains("requestVersion"));
        assertTrue(picker.contains("final List<String> labelFields"));
        assertTrue(picker.contains("for (final field in widget.labelFields)"));
        assertTrue(picker.contains("setState(() { options = []; loading = true; error = ''; })"));
        assertTrue(picker.contains("PageResponse<Map<String, dynamic>>.fromJson"));
        assertTrue(picker.contains("choices.putIfAbsent"));
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

    private int occurrences(String text, String token) {
        int count = 0;
        for (int index = text.indexOf(token); index >= 0; index = text.indexOf(token, index + token.length())) count++;
        return count;
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
