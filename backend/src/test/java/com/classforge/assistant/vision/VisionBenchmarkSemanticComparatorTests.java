package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionBenchmarkSemanticComparatorTests {

    private static final Pattern CODE_NAME = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    @Test
    void ignoresDiacriticsForCanonicalClassAndAttributeComparison() {
        var comparison = VisionBenchmarkSemanticComparator.compare(
                Set.of("CREATE_CLASS|Categoría|añoPublicacion:STRING,id:STRING"),
                Set.of("CREATE_CLASS|Categoria|anoPublicacion:STRING,id:STRING")
        );

        assertTrue(comparison.classes().exact());
        assertTrue(comparison.attributes().exact());
        assertTrue(comparison.semanticExact());
    }

    @Test
    void associationEndpointsAreUndirectedAndMultiplicitiesFollowTheirEndpoint() {
        var comparison = VisionBenchmarkSemanticComparator.compare(
                Set.of("CREATE_RELATIONSHIP|Libro|Autor|ASSOCIATION|0:*|1:*"),
                Set.of("CREATE_RELATIONSHIP|Autor|Libro|ASSOCIATION|1:*|0:*")
        );

        assertTrue(comparison.relationships().exact());
        assertTrue(comparison.multiplicities().exact());
        assertTrue(comparison.semanticExact());
    }

    @Test
    void directionalRelationshipsRemainDirectional() {
        var comparison = VisionBenchmarkSemanticComparator.compare(
                Set.of("CREATE_RELATIONSHIP|Animal|Mascota|GENERALIZATION|null:null|null:null"),
                Set.of("CREATE_RELATIONSHIP|Mascota|Animal|GENERALIZATION|null:null|null:null")
        );

        assertFalse(comparison.relationships().exact());
        assertFalse(comparison.semanticExact());
    }

    @Test
    void reportsMatchedExpectedAndUnexpectedRelationshipElements() {
        var comparison = VisionBenchmarkSemanticComparator.compare(
                Set.of(
                        "CREATE_RELATIONSHIP|Biblioteca|Libro|AGGREGATION|null:null|null:null",
                        "CREATE_RELATIONSHIP|Libro|Autor|ASSOCIATION|null:null|null:null",
                        "CREATE_RELATIONSHIP|Categoria|Prestamo|ASSOCIATION|null:null|null:null"
                ),
                Set.of(
                        "CREATE_RELATIONSHIP|Biblioteca|Libro|AGGREGATION|null:null|null:null",
                        "CREATE_RELATIONSHIP|Autor|Libro|ASSOCIATION|1:*|0:*",
                        "CREATE_RELATIONSHIP|Usuario|Libro|ASSOCIATION|0:*|0:*"
                )
        );

        assertEquals(2, comparison.relationships().matched());
        assertEquals(3, comparison.relationships().expected());
        assertEquals(1, comparison.relationships().unexpected());
        assertEquals(0, comparison.multiplicities().matched());
        assertEquals(4, comparison.multiplicities().expected());
    }


    @Test
    void associationClassContributesClassAttributesTopologyAndMultiplicities() {
        var comparison = VisionBenchmarkSemanticComparator.compare(
                Set.of("CREATE_ASSOCIATION_CLASS|DetallePedido|Pedido|Producto|COMPOSITION|1:*|1:*|precioUnitario:DECIMAL,cantidad:INTEGER"),
                Set.of("CREATE_ASSOCIATION_CLASS|DetallePedido|Pedido|Producto|COMPOSITION|1:*|1:*|cantidad:INTEGER,precioUnitario:DECIMAL")
        );

        assertTrue(comparison.classes().exact());
        assertTrue(comparison.attributes().exact());
        assertTrue(comparison.relationships().exact());
        assertTrue(comparison.multiplicities().exact());
        assertTrue(comparison.semanticExact());
    }

    @Test
    void libraryWhiteboardRealisticOracleContainsOnlyDrawnPhysicalPairs() throws Exception {
        JsonNode fixture;
        try (InputStream input = getClass().getResourceAsStream("/assistant/vision/benchmark/hardening.json")) {
            assertTrue(input != null);
            fixture = JsonMapper.builder().build().readTree(input);
        }
        JsonNode benchmarkCase = null;
        for (JsonNode candidate : fixture.get("cases")) {
            if ("library-whiteboard-realistic".equals(candidate.get("id").asString())) {
                benchmarkCase = candidate;
                break;
            }
        }
        assertTrue(benchmarkCase != null);

        Set<String> pairs = new LinkedHashSet<>();
        for (JsonNode signature : benchmarkCase.get("expectedSignatures")) {
            String value = signature.asString();
            if (!value.startsWith("CREATE_RELATIONSHIP|")) {
                continue;
            }
            String[] parts = value.split("\\|");
            pairs.add(parts[1].compareTo(parts[2]) <= 0 ? parts[1] + "|" + parts[2] : parts[2] + "|" + parts[1]);
        }

        assertEquals(Set.of(
                "Autor|Libro",
                "Biblioteca|Libro",
                "Biblioteca|Usuario",
                "Categoria|Libro",
                "Libro|Prestamo",
                "Prestamo|Usuario"
        ), pairs);
        assertFalse(pairs.contains("Libro|Usuario"));
    }

    @Test
    void benchmarkManifestsUseCodeCompatibleIdentifiers() throws Exception {
        for (String suite : Set.of("regression", "holdout", "hardening")) {
            JsonNode manifest;
            try (InputStream input = getClass().getResourceAsStream(
                    "/assistant/vision/benchmark/" + suite + ".json"
            )) {
                assertTrue(input != null);
                manifest = JsonMapper.builder().build().readTree(input);
            }

            for (JsonNode benchmarkCase : manifest.get("cases")) {
                for (JsonNode existingClass : benchmarkCase.get("existingClasses")) {
                    assertCodeName(existingClass.get("name").asString());
                    for (JsonNode attribute : existingClass.get("attributes")) {
                        assertCodeName(attribute.asString());
                    }
                }
                for (JsonNode signature : benchmarkCase.get("expectedSignatures")) {
                    assertSignatureCodeNames(signature.asString());
                }
            }
        }
    }

    private void assertSignatureCodeNames(String signature) {
        String[] parts = signature.split("\\|", -1);
        if ("CREATE_CLASS".equals(parts[0]) || "ADD_ATTRIBUTES".equals(parts[0])) {
            assertCodeName(parts[1]);
            if (!parts[2].isBlank()) {
                for (String attribute : parts[2].split(",")) {
                    assertCodeName(attribute.substring(0, attribute.lastIndexOf(':')));
                }
            }
        }
        if ("CREATE_RELATIONSHIP".equals(parts[0])) {
            assertCodeName(parts[1]);
            assertCodeName(parts[2]);
        }
        if ("CREATE_ASSOCIATION_CLASS".equals(parts[0])) {
            assertCodeName(parts[1]);
            assertCodeName(parts[2]);
            assertCodeName(parts[3]);
            if (!parts[7].isBlank()) {
                for (String attribute : parts[7].split(",")) {
                    assertCodeName(attribute.substring(0, attribute.lastIndexOf(':')));
                }
            }
        }
    }

    private void assertCodeName(String value) {
        assertTrue(CODE_NAME.matcher(value).matches(), () -> "Invalid code identifier: " + value);
    }
}
