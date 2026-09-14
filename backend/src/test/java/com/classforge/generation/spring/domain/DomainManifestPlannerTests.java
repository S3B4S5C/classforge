package com.classforge.generation.spring.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.api.SpringApiGenerationPlan;
import com.classforge.generation.spring.api.SpringApiGenerationPlanner;
import com.classforge.generation.spring.api.contract.SpringApiContract;
import com.classforge.generation.spring.api.contract.SpringApiContractPlanner;
import com.classforge.generation.spring.application.SpringBootGenerationOptions;
import com.classforge.generation.spring.model.*;
import com.classforge.project.domain.document.UmlRelationshipType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainManifestPlannerTests {
    private static final UUID USER_CLASS = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111112");
    private static final UUID USERNAME = UUID.fromString("11111111-1111-1111-1111-111111111113");
    private static final UUID PASSWORD = UUID.fromString("11111111-1111-1111-1111-111111111114");
    private static final UUID ORDER_CLASS = UUID.fromString("22222222-2222-2222-2222-222222222221");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ORDER_TOTAL = UUID.fromString("22222222-2222-2222-2222-222222222223");
    private static final UUID OWNER_RELATION = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void plansStableIdsRelationsCapabilitiesAndAuthPrivacy() {
        SpringGenerationModel model = model();
        SpringBootGenerationOptions options = SpringBootGenerationOptions.authenticated(false, USER_CLASS, USERNAME, PASSWORD);
        SpringApiGenerationPlan api = new SpringApiGenerationPlanner().plan(model, options);
        SpringApiContract contract = new SpringApiContractPlanner().plan(api);

        DomainManifestPlan manifest = new DomainManifestPlanner().plan(model, api, contract);

        assertEquals("1.0", manifest.schemaVersion());
        assertEquals("AUTH_INFORMATION_SYSTEM", manifest.generationMode());
        assertTrue(manifest.authentication().enabled());
        assertEquals(USER_CLASS, manifest.authentication().entityId());
        assertEquals(USERNAME, manifest.authentication().usernameAttributeId());
        assertEquals(PASSWORD, manifest.authentication().passwordAttributeId());

        DomainManifestPlan.Entity user = manifest.entities().stream().filter(e -> e.id().equals(USER_CLASS)).findFirst().orElseThrow();
        DomainManifestPlan.Attribute password = user.attributes().stream().filter(a -> a.id().equals(PASSWORD)).findFirst().orElseThrow();
        assertTrue(password.sensitive());
        assertTrue(password.writeOnly());
        assertFalse(password.readable());
        assertFalse(password.searchable());
        assertFalse(password.filterable());
        assertFalse(password.sortable());
        assertTrue(password.validation().requiredOnCreate());
        assertFalse(password.validation().requiredOnUpdate());

        DomainManifestPlan.Entity order = manifest.entities().stream().filter(e -> e.id().equals(ORDER_CLASS)).findFirst().orElseThrow();
        assertEquals("Pedido", order.displayName());
        assertEquals(List.of(), order.aliases());
        DomainManifestPlan.Relation owner = order.relations().getFirst();
        assertEquals(OWNER_RELATION, owner.id());
        assertEquals("ASSOCIATION", owner.umlType());
        assertEquals("MANY_TO_ONE", owner.kind());
        assertEquals(USER_CLASS, owner.targetEntityId());
        assertEquals("ownerId", owner.requestField());
        assertTrue(order.capabilities().containsAll(List.of("LIST", "COUNT", "CREATE", "GET", "UPDATE", "DELETE")));

        assertEquals(
                contract.operations().stream().map(operation -> operation.operationId()).toList(),
                manifest.operations().stream().map(DomainManifestPlan.Operation::operationId).toList()
        );
    }

    @Test
    void plansCompositeIdentifiersAndJoinedInheritance() {
        UUID rootClass = UUID.fromString("44444444-4444-4444-4444-444444444441");
        UUID rootId = UUID.fromString("44444444-4444-4444-4444-444444444442");
        UUID childClass = UUID.fromString("55555555-5555-5555-5555-555555555551");
        UUID childName = UUID.fromString("55555555-5555-5555-5555-555555555552");

        SpringEntityIdModel id = simpleId(rootId);
        SpringEntityModel root = new SpringEntityModel(
                rootClass, "Persona", "Persona", "persona",
                new SpringInheritanceModel(SpringInheritanceKind.JOINED_ROOT, null, List.of()),
                id,
                List.of(field(rootId, "id", SpringJavaType.UUID, false, true)),
                List.of(), List.of(), List.of(), List.of()
        );
        SpringEntityModel child = new SpringEntityModel(
                childClass, "Empleado", "Empleado", "empleado",
                new SpringInheritanceModel(SpringInheritanceKind.JOINED_SUBCLASS, "Persona", List.of(new SpringJoinColumnModel("id", "id", false))),
                new SpringEntityIdModel(SpringIdKind.SIMPLE, SpringJavaType.UUID, null, List.of(new SpringIdFieldModel(rootId, "id", "id", SpringJavaType.UUID)), false),
                List.of(field(childName, "nombre", SpringJavaType.STRING, false, false)),
                List.of(), List.of(), List.of(), List.of()
        );
        SpringGenerationModel model = generation(List.of(root, child));
        SpringApiGenerationPlan api = new SpringApiGenerationPlanner().plan(model, SpringBootGenerationOptions.simpleCrud(false));
        DomainManifestPlan manifest = new DomainManifestPlanner().plan(model, api, new SpringApiContractPlanner().plan(api));
        DomainManifestPlan.Entity employee = manifest.entities().stream().filter(e -> e.id().equals(childClass)).findFirst().orElseThrow();
        assertEquals("JOINED_SUBCLASS", employee.inheritance().kind());
        assertEquals(rootClass, employee.inheritance().superEntityId());
        assertEquals(rootId, employee.identifier().fields().getFirst().attributeId());
        DomainManifestPlan.Attribute rootIdentifier = employee.attributes().stream()
                .filter(DomainManifestPlan.Attribute::identifier)
                .findFirst()
                .orElseThrow();
        assertFalse(rootIdentifier.createWritable());
        assertFalse(rootIdentifier.updateWritable());
        assertFalse(rootIdentifier.validation().requiredOnCreate());
        assertTrue(employee.attributes().stream().anyMatch(a -> a.id().equals(rootId)));
        assertTrue(employee.attributes().stream().anyMatch(a -> a.id().equals(childName)));
    }

    private SpringGenerationModel model() {
        SpringEntityModel user = new SpringEntityModel(
                USER_CLASS, "Usuario", "Usuario", "usuario",
                none(), simpleId(USER_ID),
                List.of(
                        field(USER_ID, "id", SpringJavaType.UUID, false, true),
                        field(USERNAME, "username", SpringJavaType.STRING, false, false),
                        field(PASSWORD, "password", SpringJavaType.STRING, false, false)
                ), List.of(), List.of(), List.of(), List.of()
        );
        SpringDirectRelationModel owner = new SpringDirectRelationModel(
                OWNER_RELATION, UmlRelationshipType.ASSOCIATION, SpringDirectRelationKind.MANY_TO_ONE,
                "owner", "Usuario", "usuario", List.of(new SpringJoinColumnModel("owner_id", "id", false)), false, false
        );
        SpringEntityModel order = new SpringEntityModel(
                ORDER_CLASS, "Pedido", "Pedido", "pedido",
                none(), simpleId(ORDER_ID),
                List.of(
                        field(ORDER_ID, "id", SpringJavaType.UUID, false, true),
                        field(ORDER_TOTAL, "total", SpringJavaType.BIG_DECIMAL, false, false)
                ), List.of(owner), List.of(), List.of(), List.of()
        );
        return generation(List.of(user, order));
    }

    private SpringGenerationModel generation(List<SpringEntityModel> entities) {
        return new SpringGenerationModel(
                "1.0", "manifest-test", "com.example.manifest", "ManifestApplication", "21", "4.0.8", "9.2.0",
                entities,
                entities.stream().map(entity -> new SpringRepositoryModel(
                        entity.className() + "Repository", entity.className(), entity.id().typeSimpleName(),
                        entity.id().typeQualifiedName(), entity.id().kind() == SpringIdKind.COMPOSITE
                )).toList()
        );
    }

    private SpringEntityIdModel simpleId(UUID attributeId) {
        return new SpringEntityIdModel(
                SpringIdKind.SIMPLE, SpringJavaType.UUID, null,
                List.of(new SpringIdFieldModel(attributeId, "id", "id", SpringJavaType.UUID)), true
        );
    }

    private SpringScalarFieldModel field(UUID id, String name, SpringJavaType type, boolean nullable, boolean identifier) {
        return new SpringScalarFieldModel(id, name, name, name, type, nullable, identifier);
    }

    private SpringInheritanceModel none() {
        return new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of());
    }
}
