package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisionGeometryClassMappingValidatorTests {

    private final VisionGeometryClassMappingValidator validator = new VisionGeometryClassMappingValidator();

    @Test
    void bindsClosedBijectionWithoutLettingVlmChangeGeometry() {
        List<VisionGeometryClassRegion> regions = List.of(
                new VisionGeometryClassRegion("B1", null, 10, 20, 100, 80, 0.9),
                new VisionGeometryClassRegion("B2", null, 220, 30, 120, 90, 0.9)
        );
        List<VisionClassProposal> classes = List.of(
                new VisionClassProposal("c1", "Cliente", List.of(), null),
                new VisionClassProposal("c2", "Factura", List.of(), null)
        );
        VisionGeometryClassMappingProposal mapping = new VisionGeometryClassMappingProposal(
                List.of(
                        new VisionGeometryClassMapping("B1", "c2", 0.9),
                        new VisionGeometryClassMapping("B2", "c1", 0.9)
                ),
                List.of(),
                0.9
        );

        List<VisionGeometryClassRegion> bound = validator.validateAndBind(mapping, regions, classes);
        assertEquals("c2", bound.get(0).classRef());
        assertEquals(10, bound.get(0).x());
        assertEquals(100, bound.get(0).width());
        assertEquals("c1", bound.get(1).classRef());
    }

    @Test
    void rejectsInventedOrNonBijectiveMapping() {
        List<VisionGeometryClassRegion> regions = List.of(
                new VisionGeometryClassRegion("B1", null, 10, 20, 100, 80, 0.9),
                new VisionGeometryClassRegion("B2", null, 220, 30, 120, 90, 0.9)
        );
        List<VisionClassProposal> classes = List.of(
                new VisionClassProposal("c1", "Cliente", List.of(), null),
                new VisionClassProposal("c2", "Factura", List.of(), null)
        );

        assertThrows(AssistantPlanningException.class, () -> validator.validateAndBind(
                new VisionGeometryClassMappingProposal(
                        List.of(
                                new VisionGeometryClassMapping("B1", "c1", 0.9),
                                new VisionGeometryClassMapping("B2", "c1", 0.9)
                        ), List.of(), 0.9
                ),
                regions, classes
        ));
        assertThrows(AssistantPlanningException.class, () -> validator.validateAndBind(
                new VisionGeometryClassMappingProposal(
                        List.of(
                                new VisionGeometryClassMapping("B1", "c1", 0.9),
                                new VisionGeometryClassMapping("B9", "c2", 0.9)
                        ), List.of(), 0.9
                ),
                regions, classes
        ));
    }
}
