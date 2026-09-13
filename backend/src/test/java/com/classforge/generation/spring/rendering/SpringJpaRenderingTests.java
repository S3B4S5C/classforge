package com.classforge.generation.spring.rendering;

import static org.junit.jupiter.api.Assertions.*;

import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedProject;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import com.classforge.generation.spring.model.*;
import com.classforge.project.domain.document.UmlRelationshipType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpringJpaRenderingTests {
    private final SpringProjectRenderer renderer = new SpringProjectRenderer(new SpringFreeMarkerRenderer(), new GeneratedProjectValidator());

    @Test void rendersCompositeIdClassRepositoriesAndConstraints() {
        SpringEntityModel detail = entity("DetallePedido", composite("DetallePedidoId", id("pedidoId", SpringJavaType.UUID), id("productoId", SpringJavaType.UUID)), fields(field("pedidoId", "pedido_id", SpringJavaType.UUID, false, true), field("productoId", "producto_id", SpringJavaType.UUID, false, true), field("cantidad", "cantidad", SpringJavaType.INTEGER, false, false)), List.of(), List.of(), List.of(new SpringUniqueConstraintModel(List.of("pedido_id", "cantidad"))), List.of(new SpringIndexModel(List.of("cantidad", "pedido_id"))));
        GeneratedProject project = render(List.of(detail));
        String entity = text(project, "DetallePedido.java"), idClass = text(project, "DetallePedidoId.java"), repository = text(project, "DetallePedidoRepository.java");
        assertTrue(entity.contains("@IdClass(DetallePedidoId.class)") && entity.contains("@Id\n    @Column(name = \"pedido_id\"") && entity.contains("@Id\n    @Column(name = \"producto_id\"") && !entity.contains("@EmbeddedId"));
        assertTrue(entity.contains("@UniqueConstraint(columnNames = {\"pedido_id\", \"cantidad\"})") && entity.contains("@Index(columnList = \"cantidad,pedido_id\")"));
        assertTrue(idClass.contains("implements Serializable") && idClass.contains("private UUID pedidoId;") && idClass.contains("private UUID productoId;") && idClass.contains("public DetallePedidoId()") && idClass.contains("public DetallePedidoId(UUID pedidoId, UUID productoId)") && idClass.contains("equals(Object other)") && idClass.contains("hashCode()") && idClass.contains("import java.io.Serializable;") && idClass.contains("import java.util.Objects;") && idClass.contains("import java.util.UUID;") && !idClass.contains("*"));
        assertTrue(repository.contains("JpaRepository<DetallePedido, DetallePedidoId>") && repository.contains("import com.example.demo.entity.DetallePedidoId;") && !repository.contains("*"));
    }

    @Test void rendersDirectRelationsOptionalityAggregationCompositionAndCompositeFk() {
        SpringDirectRelationModel required = direct(UmlRelationshipType.ASSOCIATION, SpringDirectRelationKind.MANY_TO_ONE, "usuario", "Usuario", false, false, join("usuario_id", "id", false));
        SpringDirectRelationModel optional = direct(UmlRelationshipType.ASSOCIATION, SpringDirectRelationKind.MANY_TO_ONE, "biblioteca", "Biblioteca", true, false, join("biblioteca_id", "id", true));
        SpringDirectRelationModel composition = direct(UmlRelationshipType.COMPOSITION, SpringDirectRelationKind.ONE_TO_ONE, "pedido", "Pedido", false, true, join("pedido_id", "id", false));
        SpringDirectRelationModel composite = direct(UmlRelationshipType.ASSOCIATION, SpringDirectRelationKind.MANY_TO_ONE, "tenant", "Tenant", false, false, join("tenant_country_code", "country_code", false), join("tenant_tenant_id", "tenant_id", false));
        SpringEntityModel loan = entity("Prestamo", simple(), fields(field("id", "id", SpringJavaType.UUID, false, true)), List.of(required, optional, composition, composite), List.of(), List.of(), List.of());
        GeneratedProject project = render(List.of(loan, basic("Usuario"), basic("Biblioteca"), basic("Pedido"), basic("Tenant")));
        String source = text(project, "Prestamo.java");
        assertTrue(source.contains("@ManyToOne(fetch = FetchType.LAZY, optional = false)") && source.contains("@JoinColumn(name = \"usuario_id\", referencedColumnName = \"id\", nullable = false)") && source.contains("private Usuario usuario;") && !source.contains("UUID usuarioId"));
        assertTrue(source.contains("optional = true") && source.contains("name = \"biblioteca_id\", referencedColumnName = \"id\", nullable = true"));
        assertTrue(source.contains("@OneToOne(fetch = FetchType.LAZY, optional = false)") && source.contains("private Pedido pedido;") && !source.contains("mappedBy"));
        assertTrue(source.contains("@OnDelete(action = OnDeleteAction.CASCADE)") && source.contains("import org.hibernate.annotations.OnDelete;") && !source.contains("CascadeType.REMOVE") && !source.contains("CascadeType.ALL") && !source.contains("cascade ="));
        assertOrdered(source, "tenant_country_code", "tenant_tenant_id");
    }

    @Test void rendersManyToManyIncludingCompositeJoinsWithoutJoinArtifacts() {
        SpringManyToManyRelationModel relation = new SpringManyToManyRelationModel(UUID.randomUUID(), UmlRelationshipType.ASSOCIATION, "libroSet", "Libro", "autor_libro", List.of(join("autor_country_code", "country_code", false), join("autor_autor_id", "autor_id", false)), List.of(join("libro_id", "id", false)));
        SpringEntityModel author = entity("Autor", composite("AutorId", id("countryCode", SpringJavaType.STRING), id("autorId", SpringJavaType.UUID)), fields(field("countryCode", "country_code", SpringJavaType.STRING, false, true), field("autorId", "autor_id", SpringJavaType.UUID, false, true)), List.of(), List.of(relation), List.of(), List.of());
        GeneratedProject project = render(List.of(author, basic("Libro")));
        String source = text(project, "Autor.java");
        assertTrue(source.contains("@ManyToMany(fetch = FetchType.LAZY)") && source.contains("@JoinTable(name = \"autor_libro\"") && source.contains("private Set<Libro> libroSet = new LinkedHashSet<>();") && source.contains("import java.util.LinkedHashSet;") && source.contains("import java.util.Set;"));
        assertOrdered(source, "autor_country_code", "autor_autor_id");
        assertFalse(project.files().stream().map(GeneratedFile::path).anyMatch(path -> path.contains("AutorLibro") || path.contains("autor_libro")));
        assertFalse(text(project, "Libro.java").contains("Set<Autor>"));
    }

    @Test void rendersJoinedRootSubclassAndCompositeSubclassWithoutRedeclaredIds() {
        SpringEntityModel root = entity("PedidoDocumento", composite("PedidoDocumentoId", id("serie", SpringJavaType.STRING), id("numero", SpringJavaType.LONG)), fields(field("serie", "serie", SpringJavaType.STRING, false, true), field("numero", "numero", SpringJavaType.LONG, false, true)), List.of(), List.of(), List.of(), List.of());
        root = new SpringEntityModel(root.sourceClassId(), root.logicalName(), root.className(), root.tableName(), new SpringInheritanceModel(SpringInheritanceKind.JOINED_ROOT, null, List.of()), root.id(), root.scalarFields(), root.directRelations(), root.manyToManyRelations(), root.uniqueConstraints(), root.indexes());
        SpringEntityModel child = new SpringEntityModel(UUID.randomUUID(), "Factura", "Factura", "factura", new SpringInheritanceModel(SpringInheritanceKind.JOINED_SUBCLASS, "PedidoDocumento", List.of(join("serie", "serie", false), join("numero", "numero", false))), new SpringEntityIdModel(SpringIdKind.COMPOSITE, null, "PedidoDocumentoId", List.of(), false), List.of(), List.of(), List.of(), List.of(), List.of());
        GeneratedProject project = render(List.of(root, child)); String rootSource = text(project, "PedidoDocumento.java"), childSource = text(project, "Factura.java");
        assertTrue(rootSource.contains("@Inheritance(strategy = InheritanceType.JOINED)") && rootSource.contains("@IdClass(PedidoDocumentoId.class)"));
        assertTrue(childSource.contains("class Factura extends PedidoDocumento") && childSource.contains("@PrimaryKeyJoinColumns") && !childSource.contains("private String serie") && !childSource.contains("@IdClass(FacturaId.class)"));
        assertTrue(childSource.indexOf("@PrimaryKeyJoinColumns") < childSource.indexOf("public class Factura"), childSource);
        assertOrdered(childSource, "name = \"serie\"", "name = \"numero\"");
        assertTrue(text(project, "FacturaRepository.java").contains("JpaRepository<Factura, PedidoDocumentoId>"));
        assertFalse(project.files().stream().map(GeneratedFile::path).anyMatch(path -> path.endsWith("FacturaId.java")));
    }

    @Test void rendersDeterministicImportsAndNoCrudArtifacts() {
        SpringDirectRelationModel relation = direct(UmlRelationshipType.COMPOSITION, SpringDirectRelationKind.MANY_TO_ONE, "cliente", "Cliente", false, true, join("cliente_id", "id", false));
        SpringManyToManyRelationModel many = new SpringManyToManyRelationModel(UUID.randomUUID(), UmlRelationshipType.ASSOCIATION, "tags", "Tag", "pedido_tag", List.of(join("pedido_id", "id", false)), List.of(join("tag_id", "id", false)));
        SpringEntityModel entity = entity("Pedido", simple(), fields(field("id", "id", SpringJavaType.UUID, false, true), field("importe", "importe", SpringJavaType.BIG_DECIMAL, false, false), field("fecha", "fecha", SpringJavaType.LOCAL_DATE, false, false), field("actualizado", "actualizado", SpringJavaType.LOCAL_DATE_TIME, false, false)), List.of(relation), List.of(many), List.of(), List.of());
        GeneratedProject first = render(List.of(entity, basic("Cliente"), basic("Tag"))), second = render(List.of(entity, basic("Cliente"), basic("Tag"))); String source = text(first, "Pedido.java");
        List<String> imports = source.lines().filter(line -> line.startsWith("import ")).toList(); assertEquals(imports.stream().sorted().toList(), imports); assertEquals(imports.size(), imports.stream().distinct().count()); assertFalse(source.contains("import java.lang.")); assertEquals(first, second);
        assertFalse(first.files().stream().map(GeneratedFile::path).anyMatch(path -> path.contains("/controller/") || path.contains("/service/") || path.contains("/dto/") || path.contains("/mapper/") || path.contains("/security/")));
        assertFalse(text(first, "build.gradle").matches("(?s).*(springdoc|security|jwt|mapstruct|lombok|flyway|liquibase).*"));
    }

    private GeneratedProject render(List<SpringEntityModel> entities) { return renderer.render(new SpringGenerationModel("1.0", "demo", "com.example.demo", "DemoApplication", "21", "4.0.8", "9.2.0", entities, entities.stream().map(entity -> new SpringRepositoryModel(entity.className() + "Repository", entity.className(), entity.id().typeSimpleName(), entity.id().typeQualifiedName(), entity.id().kind() == SpringIdKind.COMPOSITE)).toList())); }
    private SpringEntityModel basic(String name) { return entity(name, simple(), fields(field("id", "id", SpringJavaType.UUID, false, true)), List.of(), List.of(), List.of(), List.of()); }
    private SpringEntityModel entity(String name, SpringEntityIdModel id, List<SpringScalarFieldModel> fields, List<SpringDirectRelationModel> direct, List<SpringManyToManyRelationModel> many, List<SpringUniqueConstraintModel> unique, List<SpringIndexModel> indexes) { return new SpringEntityModel(UUID.randomUUID(), name, name, name.toLowerCase(), new SpringInheritanceModel(SpringInheritanceKind.NONE, null, List.of()), id, fields, direct, many, unique, indexes); }
    private SpringEntityIdModel simple() { SpringIdFieldModel id = id("id", SpringJavaType.UUID); return new SpringEntityIdModel(SpringIdKind.SIMPLE, SpringJavaType.UUID, null, List.of(id), true); }
    private SpringEntityIdModel composite(String name, SpringIdFieldModel... fields) { return new SpringEntityIdModel(SpringIdKind.COMPOSITE, null, name, List.of(fields), true); }
    private List<SpringScalarFieldModel> fields(SpringScalarFieldModel... fields) { return List.of(fields); }
    private SpringIdFieldModel id(String name, SpringJavaType type) { return new SpringIdFieldModel(UUID.randomUUID(), name, name, type); }
    private SpringScalarFieldModel field(String name, String column, SpringJavaType type, boolean nullable, boolean identifier) { return new SpringScalarFieldModel(UUID.randomUUID(), name, name, column, type, nullable, identifier); }
    private SpringJoinColumnModel join(String local, String referenced, boolean nullable) { return new SpringJoinColumnModel(local, referenced, nullable); }
    private SpringDirectRelationModel direct(UmlRelationshipType type, SpringDirectRelationKind kind, String field, String target, boolean optional, boolean cascade, SpringJoinColumnModel... joins) { return new SpringDirectRelationModel(UUID.randomUUID(), type, kind, field, target, target.toLowerCase(), List.of(joins), optional, cascade); }
    private String text(GeneratedProject project, String file) { return new String(project.files().stream().filter(value -> value.path().endsWith("/" + file) || value.path().equals(file)).findFirst().orElseThrow().content(), StandardCharsets.UTF_8); }
    private void assertOrdered(String source, String first, String second) { assertTrue(source.indexOf(first) < source.indexOf(second), source); }
}
