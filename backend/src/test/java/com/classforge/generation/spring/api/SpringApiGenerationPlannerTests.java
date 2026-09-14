package com.classforge.generation.spring.api;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.application.SpringBootGenerationOptions;
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
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpringApiGenerationPlannerTests {
    private final SpringApiGenerationPlanner planner = new SpringApiGenerationPlanner();

    @Test
    void simpleCrudNeedsNoAuthSelection() {
        SpringApiGenerationPlan plan = planner.plan(model(), SpringBootGenerationOptions.simpleCrud(false));
        assertTrue(plan.enabled());
        assertFalse(plan.authEnabled());
        assertEquals(1, plan.entities().size());
        assertFalse(plan.entities().getFirst().authEntity());
    }

    @Test
    void authUsesStableClassAndAttributeIdsAndRemovesPasswordFromResponse() {
        Fixture fixture = fixture();
        SpringApiGenerationPlan plan = planner.plan(
                fixture.model,
                SpringBootGenerationOptions.authenticated(false, fixture.classId, fixture.usernameId, fixture.passwordId)
        );
        assertTrue(plan.authEnabled());
        SpringApiEntityModel auth = plan.authEntity();
        assertNotNull(auth);
        assertEquals("username", auth.usernameField().fieldName());
        assertEquals("password", auth.passwordField().fieldName());
        assertTrue(auth.responseFields().stream().noneMatch(field -> field.sourceAttributeId().equals(fixture.passwordId)));
    }

    @Test
    void stringPrimaryKeySelectedAsUsernameRemainsWritable() {
        UUID classId = UUID.fromString("90000000-0000-0000-0000-000000000001");
        UUID usernameId = UUID.fromString("90000000-0000-0000-0000-000000000002");
        UUID passwordId = UUID.fromString("90000000-0000-0000-0000-000000000003");
        SpringEntityIdModel id = new SpringEntityIdModel(
                SpringIdKind.SIMPLE,
                SpringJavaType.STRING,
                null,
                List.of(new SpringIdFieldModel(usernameId, "username", "username", SpringJavaType.STRING)),
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
                        new SpringScalarFieldModel(usernameId, "username", "username", "username", SpringJavaType.STRING, false, true),
                        new SpringScalarFieldModel(passwordId, "password", "password", "password", SpringJavaType.STRING, false, false)
                ),
                List.of(), List.of(), List.of(), List.of()
        );
        SpringGenerationModel model = new SpringGenerationModel(
                "1.0", "demo", "com.example.demo", "DemoApplication", "21", "4.0.8", "9.2.0",
                List.of(entity),
                List.of(new SpringRepositoryModel("UsuarioRepository", "Usuario", "String", "java.lang.String", false))
        );

        SpringApiEntityModel auth = planner.plan(
                model,
                SpringBootGenerationOptions.authenticated(false, classId, usernameId, passwordId)
        ).authEntity();

        assertNotNull(auth);
        assertTrue(auth.id().automaticallyGenerated());
        assertTrue(auth.requestFields().stream().anyMatch(field -> field.sourceAttributeId().equals(usernameId)));
    }

    @Test
    void authFailsClosedForStaleOrNonStringCredentialSelection() {
        Fixture fixture = fixture();
        SpringApiGenerationException stale = assertThrows(
                SpringApiGenerationException.class,
                () -> planner.plan(
                        fixture.model,
                        SpringBootGenerationOptions.authenticated(false, fixture.classId, UUID.randomUUID(), fixture.passwordId)
                )
        );
        assertEquals(SpringApiGenerationDiagnosticCode.AUTH_USERNAME_ATTRIBUTE_NOT_FOUND, stale.diagnostics().getFirst().code());

        UUID numericPasswordId = UUID.randomUUID();
        SpringScalarFieldModel numericPassword = new SpringScalarFieldModel(
                numericPasswordId, "pin", "pin", "pin", SpringJavaType.INTEGER, false, false
        );
        SpringEntityModel original = fixture.model.entities().getFirst();
        SpringEntityModel withNumericPassword = new SpringEntityModel(
                original.sourceClassId(), original.logicalName(), original.className(), original.tableName(),
                original.inheritance(), original.id(),
                List.of(original.scalarFields().get(0), original.scalarFields().get(1), numericPassword),
                List.of(), List.of(), List.of(), List.of()
        );
        SpringGenerationModel invalidModel = new SpringGenerationModel(
                "1.0", "demo", "com.example.demo", "DemoApplication", "21", "4.0.8", "9.2.0",
                List.of(withNumericPassword), fixture.model.repositories()
        );
        SpringApiGenerationException type = assertThrows(
                SpringApiGenerationException.class,
                () -> planner.plan(
                        invalidModel,
                        SpringBootGenerationOptions.authenticated(false, fixture.classId, fixture.usernameId, numericPasswordId)
                )
        );
        assertTrue(type.diagnostics().stream().anyMatch(d -> d.code() == SpringApiGenerationDiagnosticCode.AUTH_ATTRIBUTES_MUST_BE_STRING));
    }

    private SpringGenerationModel model() {
        return fixture().model;
    }

    private Fixture fixture() {
        UUID classId = UUID.randomUUID();
        UUID idId = UUID.randomUUID();
        UUID usernameId = UUID.randomUUID();
        UUID passwordId = UUID.randomUUID();
        SpringIdFieldModel idField = new SpringIdFieldModel(idId, "id", "id", SpringJavaType.UUID);
        SpringEntityIdModel id = new SpringEntityIdModel(SpringIdKind.SIMPLE, SpringJavaType.UUID, null, List.of(idField), true);
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
