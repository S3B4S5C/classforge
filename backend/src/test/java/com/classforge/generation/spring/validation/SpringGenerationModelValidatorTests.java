package com.classforge.generation.spring.validation;

import static org.junit.jupiter.api.Assertions.*;
import com.classforge.generation.spring.model.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpringGenerationModelValidatorTests {
    private final SpringGenerationModelValidator validator = new SpringGenerationModelValidator();

    @Test void acceptsValidModelAndRejectsEntityAndIdInvariants() {
        assertDoesNotThrow(() -> validator.validate(model(List.of(entity("Cliente")), List.of(repository("Cliente", "ClienteRepository")))));
        invalid(model(List.of(entity("Cliente"), entity("Cliente")), List.of(repository("Cliente", "ClienteRepository"), repository("Cliente", "OtroRepository"))));
        SpringEntityModel duplicateScalar = new SpringEntityModel(UUID.randomUUID(), "Cliente", "Cliente", "cliente", none(), simpleId(), List.of(field("nombre"), field("nombre")), List.of(), List.of(), List.of(), List.of());
        invalid(model(List.of(duplicateScalar), List.of(repository("Cliente", "ClienteRepository"))));
        SpringEntityModel invalidSimple = new SpringEntityModel(UUID.randomUUID(), "Cliente", "Cliente", "cliente", none(), new SpringEntityIdModel(SpringIdKind.SIMPLE, SpringJavaType.UUID, null, List.of(idField(), idField()), true), List.of(), List.of(), List.of(), List.of(), List.of());
        invalid(model(List.of(invalidSimple), List.of(repository("Cliente", "ClienteRepository"))));
        SpringEntityModel invalidComposite = new SpringEntityModel(UUID.randomUUID(), "Cliente", "Cliente", "cliente", none(), new SpringEntityIdModel(SpringIdKind.COMPOSITE, null, "ClienteId", List.of(idField()), true), List.of(), List.of(), List.of(), List.of(), List.of());
        invalid(model(List.of(invalidComposite), List.of(repository("Cliente", "ClienteRepository"))));
    }

    @Test void rejectsRelationsInheritanceAndRepositoryInvariants() {
        SpringEntityModel childMissingParent = new SpringEntityModel(UUID.randomUUID(), "Empleado", "Empleado", "empleado", new SpringInheritanceModel(SpringInheritanceKind.JOINED_SUBCLASS, "Persona", List.of()), inheritedId(), List.of(), List.of(), List.of(), List.of(), List.of());
        invalid(model(List.of(childMissingParent), List.of(repository("Empleado", "EmpleadoRepository"))));
        SpringDirectRelationModel direct = new SpringDirectRelationModel(UUID.randomUUID(), null, SpringDirectRelationKind.MANY_TO_ONE, "missing", "Missing", "missing", List.of(), false, false);
        SpringEntityModel badDirect = new SpringEntityModel(UUID.randomUUID(), "Cliente", "Cliente", "cliente", none(), simpleId(), List.of(), List.of(direct), List.of(), List.of(), List.of());
        invalid(model(List.of(badDirect), List.of(repository("Cliente", "ClienteRepository"))));
        SpringManyToManyRelationModel many = new SpringManyToManyRelationModel(UUID.randomUUID(), null, "missingSet", "Missing", "", List.of(), List.of());
        SpringEntityModel badMany = new SpringEntityModel(UUID.randomUUID(), "Cliente", "Cliente", "cliente", none(), simpleId(), List.of(), List.of(), List.of(many), List.of(), List.of());
        invalid(model(List.of(badMany), List.of(repository("Cliente", "ClienteRepository"))));
        invalid(model(List.of(entity("Cliente")), List.of(repository("Missing", "ClienteRepository"))));
        invalid(model(List.of(entity("Cliente")), List.of(new SpringRepositoryModel("ClienteRepository", "Cliente", "Long", "java.lang.Long", false))));
        invalid(model(List.of(entity("Cliente"), entity("Pedido")), List.of(repository("Cliente", "Repository"), repository("Pedido", "Repository"))));
    }

    @Test void rejectsTwoRepositoriesForSameEntityEvenWhenCountsMatch() {
        invalid(model(List.of(entity("Cliente"), entity("Pedido")), List.of(repository("Cliente", "ClienteRepository"), repository("Cliente", "LegacyClienteRepository"))));
    }

    @Test void rejectsEntityWithoutRepository() {
        invalid(model(List.of(entity("Cliente"), entity("Pedido")), List.of(repository("Cliente", "ClienteRepository"))));
    }

    @Test void rejectsScalarAndDirectRelationFieldCollision() {
        SpringDirectRelationModel relation = new SpringDirectRelationModel(UUID.randomUUID(), null, SpringDirectRelationKind.MANY_TO_ONE, "nombre", "Pedido", "pedido", List.of(new SpringJoinColumnModel("pedido_id", "id", false)), false, false);
        SpringEntityModel client = new SpringEntityModel(UUID.randomUUID(), "Cliente", "Cliente", "cliente", none(), simpleId(), List.of(field("nombre")), List.of(relation), List.of(), List.of(), List.of());
        invalid(model(List.of(client, entity("Pedido")), List.of(repository("Cliente", "ClienteRepository"), repository("Pedido", "PedidoRepository"))));
    }

    private SpringGenerationModel model(List<SpringEntityModel> entities, List<SpringRepositoryModel> repositories) { return new SpringGenerationModel("1.0", "demo", "com.example", "DemoApplication", "21", "4.0.8", "9.2.0", entities, repositories); }
    private SpringEntityModel entity(String name) { return new SpringEntityModel(UUID.randomUUID(), name, name, name.toLowerCase(), none(), simpleId(), List.of(field("nombre")), List.of(), List.of(), List.of(), List.of()); }
    private SpringInheritanceModel none() { return new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()); }
    private SpringEntityIdModel simpleId() { return new SpringEntityIdModel(SpringIdKind.SIMPLE, SpringJavaType.UUID, null, List.of(idField()), true); }
    private SpringEntityIdModel inheritedId() { return new SpringEntityIdModel(SpringIdKind.SIMPLE, SpringJavaType.UUID, null, List.of(idField()), false); }
    private SpringIdFieldModel idField() { return new SpringIdFieldModel(UUID.randomUUID(), "id", "id", SpringJavaType.UUID); }
    private SpringScalarFieldModel field(String name) { return new SpringScalarFieldModel(UUID.randomUUID(), name, name, name, SpringJavaType.STRING, false, false); }
    private SpringRepositoryModel repository(String entity, String name) { return new SpringRepositoryModel(name, entity, "UUID", "java.util.UUID", false); }
    private void invalid(SpringGenerationModel model) { assertThrows(SpringGenerationException.class, () -> validator.validate(model)); }
}
