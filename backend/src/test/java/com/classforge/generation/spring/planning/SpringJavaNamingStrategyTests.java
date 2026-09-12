package com.classforge.generation.spring.planning;

import static org.junit.jupiter.api.Assertions.*;
import com.classforge.generation.spring.model.SpringGenerationConfig;
import com.classforge.generation.spring.validation.*;
import org.junit.jupiter.api.Test;

class SpringJavaNamingStrategyTests {
    private final SpringJavaNamingStrategy naming = new SpringJavaNamingStrategy();

    @Test void convertsRequiredJavaNames() {
        assertEquals("Cliente", naming.entityClassName("Cliente"));
        assertEquals("LineaPedido", naming.entityClassName("LineaPedido"));
        assertEquals("LineaPedido", naming.entityClassName("linea_pedido"));
        assertEquals("UrlExterna", naming.entityClassName("URLExterna"));
        assertEquals("fechaPrestamo", naming.fieldName("fechaPrestamo"));
        assertEquals("fechaPrestamo", naming.fieldName("fecha_prestamo"));
        assertEquals("urlExterna", naming.fieldName("URLExterna"));
        assertEquals("ClienteRepository", naming.repositoryName("Cliente"));
        assertEquals("DetallePedidoId", naming.idClassName("DetallePedido"));
        assertEquals("BibliotecaApplication", naming.applicationClassName("biblioteca"));
        assertEquals("BibliotecaApiApplication", naming.applicationClassName("biblioteca-api"));
    }

    @Test void rejectsKeywordsAndInvalidConfiguration() {
        SpringGenerationException keyword = assertThrows(SpringGenerationException.class, () -> naming.fieldName("class"));
        assertEquals(SpringGenerationDiagnosticCode.JAVA_IDENTIFIER_INVALID, keyword.diagnostics().getFirst().code());
        for (String artifact : new String[] { "biblioteca", "biblioteca-api", "sistema2" }) assertDoesNotThrow(() -> new SpringGenerationConfig(artifact, "com.example.biblioteca"));
        for (String artifact : new String[] { "Biblioteca", "-biblioteca", "biblioteca_", "../demo", "demo app", "" }) assertCode(() -> new SpringGenerationConfig(artifact, "com.example"), SpringGenerationDiagnosticCode.INVALID_ARTIFACT_NAME);
        for (String basePackage : new String[] { "com.example.biblioteca", "org.demo", "classforge.generated_app" }) assertDoesNotThrow(() -> new SpringGenerationConfig("demo", basePackage));
        for (String basePackage : new String[] { "Com.example", "com.example.", "com..demo", "com.example.class", "../demo", "" }) assertCode(() -> new SpringGenerationConfig("demo", basePackage), SpringGenerationDiagnosticCode.INVALID_BASE_PACKAGE);
    }

    @Test void mapsEveryClosedRelationalType() {
        assertType(com.classforge.generation.relational.model.RelationalDataType.VARCHAR, "String", "java.lang.String");
        assertType(com.classforge.generation.relational.model.RelationalDataType.INTEGER, "Integer", "java.lang.Integer");
        assertType(com.classforge.generation.relational.model.RelationalDataType.BIGINT, "Long", "java.lang.Long");
        assertType(com.classforge.generation.relational.model.RelationalDataType.DECIMAL, "BigDecimal", "java.math.BigDecimal");
        assertType(com.classforge.generation.relational.model.RelationalDataType.BOOLEAN, "Boolean", "java.lang.Boolean");
        assertType(com.classforge.generation.relational.model.RelationalDataType.DATE, "LocalDate", "java.time.LocalDate");
        assertType(com.classforge.generation.relational.model.RelationalDataType.TIMESTAMP, "LocalDateTime", "java.time.LocalDateTime");
        assertType(com.classforge.generation.relational.model.RelationalDataType.UUID, "UUID", "java.util.UUID");
    }

    private void assertCode(org.junit.jupiter.api.function.Executable action, SpringGenerationDiagnosticCode code) { assertTrue(assertThrows(SpringGenerationException.class, action).diagnostics().stream().anyMatch(d -> d.code() == code)); }
    private void assertType(com.classforge.generation.relational.model.RelationalDataType source, String simple, String qualified) { assertEquals(simple, com.classforge.generation.spring.model.SpringJavaType.from(source).simpleName()); assertEquals(qualified, com.classforge.generation.spring.model.SpringJavaType.from(source).qualifiedName()); }
}
