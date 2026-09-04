package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisionBenchmarkTileSignatureMergerTests {

    @Test
    void mergesPartialClassAttributesAcrossTiles() {
        Set<String> merged = VisionBenchmarkTileSignatureMerger.merge(new LinkedHashSet<>(Set.of(
                "CREATE_CLASS|Libro|id:STRING,isbn:STRING",
                "CREATE_CLASS|Libro|titulo:STRING,añoPublicacion:STRING"
        )));

        assertEquals(
                Set.of("CREATE_CLASS|Libro|añoPublicacion:STRING,id:STRING,isbn:STRING,titulo:STRING"),
                merged
        );
    }

    @Test
    void mergesReverseAssociationsAndKeepsTheRicherMultiplicityObservation() {
        Set<String> merged = VisionBenchmarkTileSignatureMerger.merge(new LinkedHashSet<>(Set.of(
                "CREATE_RELATIONSHIP|Usuario|Libro|ASSOCIATION|null:null|null:null",
                "CREATE_RELATIONSHIP|Libro|Usuario|ASSOCIATION|0:*|0:*"
        )));

        assertEquals(
                Set.of("CREATE_RELATIONSHIP|Libro|Usuario|ASSOCIATION|0:*|0:*"),
                merged
        );
    }
}
