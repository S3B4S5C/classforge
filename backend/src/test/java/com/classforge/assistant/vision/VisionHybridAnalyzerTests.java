package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VisionHybridAnalyzerTests {

    @Test
    void rejectsEmptyClassificationInsteadOfDroppingGeometryConfirmedTopology() throws Exception {
        List<VisionGeometryClassRegion> regions = List.of(
                new VisionGeometryClassRegion("B1", "c1", 10, 10, 70, 70, 1.0),
                new VisionGeometryClassRegion("B2", "c2", 130, 10, 70, 70, 1.0),
                new VisionGeometryClassRegion("B3", "c3", 230, 10, 70, 70, 1.0)
        );
        VisionGeometryEdgeCandidate candidate = new VisionGeometryEdgeCandidate(
                "E1", "B1", "B2", "c1", "c2", 0.8, 0, 0, 210, 100, 80, 45, 130, 45
        );
        VisionHybridModelGateway gateway = new VisionHybridModelGateway() {
            @Override
            public VisionGeometryClassMappingProposal mapClassRegions(
                    VisionNormalizedImage ignored, List<VisionGeometryClassRegion> ignoredRegions, List<VisionClassProposal> ignoredClasses
            ) {
                return new VisionGeometryClassMappingProposal(List.of(
                        new VisionGeometryClassMapping("B1", "c1", 1.0),
                        new VisionGeometryClassMapping("B2", "c2", 1.0),
                        new VisionGeometryClassMapping("B3", "c3", 1.0)
                ), List.of(), 1.0);
            }

            @Override
            public VisionHybridRelationshipClassificationProposal classifyRelationship(
                    VisionNormalizedImage panel, VisionGeometryEdgeCandidate ignored, List<VisionClassProposal> ignoredClasses
            ) {
                return new VisionHybridRelationshipClassificationProposal(List.of(), List.of(), null);
            }

            @Override
            public VisionHybridMultiplicityTranscription transcribeMultiplicity(
                    VisionNormalizedImage panel,
                    VisionGeometryEdgeCandidate ignored,
                    VisionHybridEndpoint endpoint,
                    VisionClassProposal endpointClass
            ) {
                throw new AssertionError("Multiplicity must not run after an empty classification");
            }

            @Override
            public VisionHybridMultiplicityOwnership verifyMultiplicityOwnership(
                    VisionNormalizedImage panel,
                    VisionGeometryEdgeCandidate ignored,
                    VisionHybridEndpoint endpoint,
                    VisionClassProposal endpointClass,
                    String candidateRawLabel
            ) {
                throw new AssertionError("Ownership must not run after an empty classification");
            }
        };
        VisionHybridAnalyzer analyzer = new VisionHybridAnalyzer(
                (image, context) -> semantic(), gateway,
                (image, expected) -> new UmlClassRegionDetection(regions, new byte[]{1}, new byte[]{1}),
                new VisionGeometryClassMappingValidator(), new VisionHybridImageFactory(),
                (image, mapped) -> new UmlDiagramGeometry(mapped, List.of(candidate), new byte[]{1}, new byte[]{1}, new byte[]{1}),
                new VisionRelationshipEvidenceSheetRenderer(), new VisionHybridProposalAssembler(), true, 2, false
        );

        VisionModelGatewayException exception = assertThrows(
                VisionModelGatewayException.class, () -> analyzer.analyze(image(), null)
        );

        assertEquals(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, exception.reason());
    }

    @Test
    void annotatesEachGeometryEdgeIndependentlyInGeometryOrder() throws Exception {
        List<String> calls = new ArrayList<>();
        VisionHybridModelGateway hybridGateway = new VisionHybridModelGateway() {
            @Override
            public VisionGeometryClassMappingProposal mapClassRegions(
                    VisionNormalizedImage ignored, List<VisionGeometryClassRegion> regions, List<VisionClassProposal> classes
            ) {
                return new VisionGeometryClassMappingProposal(List.of(
                        new VisionGeometryClassMapping("B1", "c1", 1.0),
                        new VisionGeometryClassMapping("B2", "c2", 1.0),
                        new VisionGeometryClassMapping("B3", "c3", 1.0)
                ), List.of(), 1.0);
            }

            @Override
            public VisionHybridRelationshipClassificationProposal classifyRelationship(
                    VisionNormalizedImage panel, VisionGeometryEdgeCandidate candidate, List<VisionClassProposal> classes
            ) {
                calls.add(candidate.edgeId() + " classify");
                return new VisionHybridRelationshipClassificationProposal(List.of(new VisionHybridEdgeClassification(
                        candidate.edgeId(), "ASSOCIATION", "NONE", "linea visible", "E1".equals(candidate.edgeId()) ? 0.8 : 0.5
                )), List.of(), "E1".equals(candidate.edgeId()) ? 0.8 : 0.5);
            }

            @Override
            public VisionHybridMultiplicityTranscription transcribeMultiplicity(
                    VisionNormalizedImage panel,
                    VisionGeometryEdgeCandidate candidate,
                    VisionHybridEndpoint endpoint,
                    VisionClassProposal endpointClass
            ) {
                calls.add(candidate.edgeId() + " " + endpoint.name() + " transcribe");
                String rawLabel = switch (candidate.edgeId() + endpoint.name()) {
                    case "E1A" -> "0..*";
                    case "E1B" -> "1..*";
                    case "E2A" -> null;
                    case "E2B" -> "1";
                    default -> throw new AssertionError("Unexpected endpoint");
                };
                return new VisionHybridMultiplicityTranscription(candidate.edgeId(), endpoint, rawLabel, 0.9);
            }

            @Override
            public VisionHybridMultiplicityOwnership verifyMultiplicityOwnership(
                    VisionNormalizedImage panel,
                    VisionGeometryEdgeCandidate candidate,
                    VisionHybridEndpoint endpoint,
                    VisionClassProposal endpointClass,
                    String candidateRawLabel
            ) {
                calls.add(candidate.edgeId() + " " + endpoint.name() + " ownership");
                return new VisionHybridMultiplicityOwnership(candidate.edgeId(), endpoint, "BELONGS", 0.8);
            }
        };
        List<VisionGeometryClassRegion> regions = List.of(
                new VisionGeometryClassRegion("B1", "c1", 10, 10, 70, 70, 1.0),
                new VisionGeometryClassRegion("B2", "c2", 130, 10, 70, 70, 1.0),
                new VisionGeometryClassRegion("B3", "c3", 230, 10, 70, 70, 1.0)
        );
        List<VisionGeometryEdgeCandidate> candidates = List.of(
                new VisionGeometryEdgeCandidate("E1", "B1", "B2", "c1", "c2", 0.8, 0, 0, 210, 100, 80, 45, 130, 45),
                new VisionGeometryEdgeCandidate("E2", "B1", "B3", "c1", "c3", 0.6, 0, 0, 310, 100, 80, 50, 230, 50)
        );
        VisionHybridAnalyzer analyzer = new VisionHybridAnalyzer(
                (image, context) -> semantic(), hybridGateway,
                (image, expected) -> new UmlClassRegionDetection(regions, new byte[]{1}, new byte[]{1}),
                new VisionGeometryClassMappingValidator(), new VisionHybridImageFactory(),
                (image, mapped) -> new UmlDiagramGeometry(mapped, candidates, new byte[]{1}, new byte[]{1}, new byte[]{1}),
                new VisionRelationshipEvidenceSheetRenderer(), new VisionHybridProposalAssembler(), true, 2, false
        );

        VisionUmlProposal result = analyzer.analyze(image(), null);

        assertEquals(List.of(
                "E1 classify", "E1 A transcribe", "E1 A ownership", "E1 B transcribe", "E1 B ownership",
                "E2 classify", "E2 A transcribe", "E2 B transcribe", "E2 B ownership"
        ), calls);
        assertEquals(2, result.safeRelationships().size());
        assertEquals(0.5, result.confidence());
        assertEquals(0, result.safeRelationships().get(0).sourceMultiplicity().lower());
        assertEquals(true, result.safeRelationships().get(0).sourceMultiplicity().unbounded());
        assertEquals(1, result.safeRelationships().get(0).targetMultiplicity().lower());
        assertEquals(true, result.safeRelationships().get(0).targetMultiplicity().unbounded());
        assertEquals(null, result.safeRelationships().get(1).sourceMultiplicity());
        assertEquals(1, result.safeRelationships().get(1).targetMultiplicity().lower());
        assertEquals(1, result.safeRelationships().get(1).targetMultiplicity().upper());
        assertEquals(2, analyzer.lastDiagnostics().safeRelationshipPanels().size());
        assertTrue(analyzer.lastDiagnostics().safeMultiplicityEndpointPanels().size() >= 7);
        assertEquals("hybrid-relationship-evidence.png", analyzer.lastDiagnostics().relationshipEvidenceSheet().originalFilename());
    }

    @Test
    void combinesStatelessTranscriptionAndOwnershipDeterministically() throws Exception {
        List<String> calls = new ArrayList<>();
        VisionHybridModelGateway gateway = new VisionHybridModelGateway() {
            @Override
            public VisionGeometryClassMappingProposal mapClassRegions(
                    VisionNormalizedImage ignored, List<VisionGeometryClassRegion> regions, List<VisionClassProposal> classes
            ) {
                return new VisionGeometryClassMappingProposal(List.of(
                        new VisionGeometryClassMapping("B1", "c1", 1.0),
                        new VisionGeometryClassMapping("B2", "c2", 1.0),
                        new VisionGeometryClassMapping("B3", "c3", 1.0)
                ), List.of(), 1.0);
            }

            @Override
            public VisionHybridRelationshipClassificationProposal classifyRelationship(
                    VisionNormalizedImage panel, VisionGeometryEdgeCandidate candidate, List<VisionClassProposal> classes
            ) {
                calls.add(candidate.edgeId() + " classify");
                return new VisionHybridRelationshipClassificationProposal(List.of(new VisionHybridEdgeClassification(
                        candidate.edgeId(), "ASSOCIATION", "NONE", "linea", 0.9
                )), List.of(), 0.9);
            }

            @Override
            public VisionHybridMultiplicityTranscription transcribeMultiplicity(
                    VisionNormalizedImage panel, VisionGeometryEdgeCandidate candidate,
                    VisionHybridEndpoint endpoint, VisionClassProposal endpointClass
            ) {
                calls.add(candidate.edgeId() + " " + endpoint.name() + " transcribe");
                String rawLabel = switch (candidate.edgeId() + endpoint.name()) {
                    case "E1A" -> "0..*";
                    case "E1B" -> "1..*";
                    case "E2A", "E3B" -> null;
                    case "E2B", "E3A" -> "1";
                    default -> throw new AssertionError("Unexpected endpoint");
                };
                return new VisionHybridMultiplicityTranscription(candidate.edgeId(), endpoint, rawLabel, 0.8);
            }

            @Override
            public VisionHybridMultiplicityOwnership verifyMultiplicityOwnership(
                    VisionNormalizedImage panel, VisionGeometryEdgeCandidate candidate, VisionHybridEndpoint endpoint,
                    VisionClassProposal endpointClass, String candidateRawLabel
            ) {
                calls.add(candidate.edgeId() + " " + endpoint.name() + " ownership");
                String ownership = switch (candidate.edgeId() + endpoint.name()) {
                    case "E1A", "E1B" -> "BELONGS";
                    case "E2B" -> "NOT_BELONGS";
                    case "E3A" -> "AMBIGUOUS";
                    default -> throw new AssertionError("Ownership must not run for this endpoint");
                };
                return new VisionHybridMultiplicityOwnership(candidate.edgeId(), endpoint, ownership, 0.7);
            }
        };
        List<VisionGeometryClassRegion> regions = List.of(
                new VisionGeometryClassRegion("B1", "c1", 10, 10, 70, 70, 1.0),
                new VisionGeometryClassRegion("B2", "c2", 130, 10, 70, 70, 1.0),
                new VisionGeometryClassRegion("B3", "c3", 230, 10, 70, 70, 1.0)
        );
        List<VisionGeometryEdgeCandidate> candidates = List.of(
                new VisionGeometryEdgeCandidate("E1", "B1", "B2", "c1", "c2", 0.8, 0, 0, 210, 100, 80, 45, 130, 45),
                new VisionGeometryEdgeCandidate("E2", "B1", "B3", "c1", "c3", 0.6, 0, 0, 310, 100, 80, 50, 230, 50),
                new VisionGeometryEdgeCandidate("E3", "B2", "B3", "c2", "c3", 0.6, 0, 0, 310, 100, 200, 50, 230, 50)
        );
        VisionHybridAnalyzer analyzer = new VisionHybridAnalyzer(
                (image, context) -> semantic(), gateway,
                (image, expected) -> new UmlClassRegionDetection(regions, new byte[]{1}, new byte[]{1}),
                new VisionGeometryClassMappingValidator(), new VisionHybridImageFactory(),
                (image, mapped) -> new UmlDiagramGeometry(mapped, candidates, new byte[]{1}, new byte[]{1}, new byte[]{1}),
                new VisionRelationshipEvidenceSheetRenderer(), new VisionHybridProposalAssembler(), true, 2, false
        );

        VisionUmlProposal result = analyzer.analyze(image(), null);

        assertEquals(List.of(
                "E1 classify", "E1 A transcribe", "E1 A ownership", "E1 B transcribe", "E1 B ownership",
                "E2 classify", "E2 A transcribe", "E2 B transcribe", "E2 B ownership",
                "E3 classify", "E3 A transcribe", "E3 A ownership", "E3 B transcribe"
        ), calls);
        assertEquals(3, result.safeRelationships().size());
        assertEquals(0, result.safeRelationships().get(0).sourceMultiplicity().lower());
        assertEquals(1, result.safeRelationships().get(0).targetMultiplicity().lower());
        assertEquals(null, result.safeRelationships().get(1).sourceMultiplicity());
        assertEquals(null, result.safeRelationships().get(1).targetMultiplicity());
        assertEquals(null, result.safeRelationships().get(2).sourceMultiplicity());
        assertEquals(null, result.safeRelationships().get(2).targetMultiplicity());
        assertTrue(analyzer.lastDiagnostics().safeMultiplicityEndpointPanels().size() >= 10);
        assertEquals(false, analyzer.lastDiagnostics().safeMultiplicityObservations().get(3).finalAccepted());
        assertEquals("AMBIGUOUS", analyzer.lastDiagnostics().safeMultiplicityObservations().get(4).rawOwner());
    }

    @Test
    void acceptsBelongsWithoutUsingLabelCenterOrLocalizedOwnershipGuard() throws Exception {
        List<VisionNormalizedImage> attributionPanels = new ArrayList<>();
        List<VisionGeometryClassRegion> regions = List.of(
                new VisionGeometryClassRegion("B1", null, 10, 10, 70, 70, 1.0),
                new VisionGeometryClassRegion("B2", null, 130, 10, 70, 70, 1.0),
                new VisionGeometryClassRegion("B3", null, 230, 10, 70, 70, 1.0)
        );
        List<VisionGeometryEdgeCandidate> candidates = List.of(
                new VisionGeometryEdgeCandidate(
                        "E1", "B1", "B2", "c1", "c2", 0.8, 0, 0, 300, 110, 80, 80, 220, 80,
                        108, 80, 192, 80
                ),
                new VisionGeometryEdgeCandidate(
                        "E2", "B1", "B3", "c1", "c3", 0.7, 0, 0, 300, 110, 80, 105, 250, 105,
                        108, 105, 222, 105
                )
        );
        VisionHybridModelGateway gateway = new VisionHybridModelGateway() {
            @Override
            public VisionGeometryClassMappingProposal mapClassRegions(
                    VisionNormalizedImage ignored, List<VisionGeometryClassRegion> ignoredRegions, List<VisionClassProposal> ignoredClasses
            ) {
                return new VisionGeometryClassMappingProposal(List.of(
                        new VisionGeometryClassMapping("B1", "c1", 1.0),
                        new VisionGeometryClassMapping("B2", "c2", 1.0),
                        new VisionGeometryClassMapping("B3", "c3", 1.0)
                ), List.of(), 1.0);
            }

            @Override
            public VisionHybridRelationshipClassificationProposal classifyRelationship(
                    VisionNormalizedImage panel, VisionGeometryEdgeCandidate candidate, List<VisionClassProposal> ignoredClasses
            ) {
                return new VisionHybridRelationshipClassificationProposal(List.of(new VisionHybridEdgeClassification(
                        candidate.edgeId(), "ASSOCIATION", "NONE", "linea", 0.9
                )), List.of(), 0.9);
            }

            @Override
            public VisionHybridMultiplicityTranscription transcribeMultiplicity(
                    VisionNormalizedImage panel, VisionGeometryEdgeCandidate candidate,
                    VisionHybridEndpoint endpoint, VisionClassProposal endpointClass
            ) {
                return new VisionHybridMultiplicityTranscription(
                        candidate.edgeId(), endpoint, "E1".equals(candidate.edgeId()) ? "1" : null, 0.9
                );
            }

            @Override
            public VisionHybridMultiplicityAttribution attributeMultiplicity(
                    VisionNormalizedImage panel, VisionGeometryEdgeCandidate candidate,
                    VisionHybridEndpoint endpoint, VisionClassProposal endpointClass, String candidateRawLabel,
                    List<String> visibleCompetingEdgeIds
            ) {
                attributionPanels.add(panel);
                return new VisionHybridMultiplicityAttribution(candidate.edgeId(), endpoint, candidate.edgeId(), 0.9);
            }
        };
        VisionHybridAnalyzer analyzer = new VisionHybridAnalyzer(
                (image, context) -> semantic(), gateway,
                (image, expected) -> new UmlClassRegionDetection(regions, new byte[]{1}, new byte[]{1}),
                new VisionGeometryClassMappingValidator(), new VisionHybridImageFactory(),
                (image, mapped) -> new UmlDiagramGeometry(mapped, candidates, new byte[]{1}, new byte[]{1}, new byte[]{1}),
                new VisionRelationshipEvidenceSheetRenderer(), new VisionHybridProposalAssembler(), true, 2, false
        );

        VisionUmlProposal result = analyzer.analyze(image(), null);

        assertEquals(2, result.safeRelationships().size());
        assertEquals(1, result.safeRelationships().get(0).sourceMultiplicity().lower());
        assertEquals(1, result.safeRelationships().get(0).targetMultiplicity().lower());
        VisionHybridMultiplicityObservationDiagnostic accepted = analyzer.lastDiagnostics().safeMultiplicityObservations().getFirst();
        assertEquals("E1", accepted.rawOwner());
        assertEquals("E1", accepted.effectiveOwner());
        assertEquals(2, attributionPanels.size());
        assertEquals(640, attributionPanels.getFirst().width());
        assertEquals(360, attributionPanels.getFirst().height());
        assertEquals("multiplicity-E1-A-attribution.png", attributionPanels.getFirst().originalFilename());
        assertTrue(analyzer.lastDiagnostics().safeMultiplicityEndpointPanels().stream().anyMatch(
                panel -> "multiplicity-E1-A-attribution.png".equals(panel.originalFilename())
                        && panel.width() == 640 && panel.height() == 360
        ));
    }

    private VisionUmlProposal semantic() {
        return new VisionUmlProposal("diagram", List.of(
                new VisionClassProposal("c1", "A", List.of(), null),
                new VisionClassProposal("c2", "B", List.of(), null),
                new VisionClassProposal("c3", "C", List.of(), null)
        ), List.of(), List.of(), 0.9);
    }

    private VisionNormalizedImage image() throws Exception {
        BufferedImage image = new BufferedImage(320, 110, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new VisionNormalizedImage("test.png", "image/png", "image/png", output.toByteArray(), 320, 110, "test", false);
    }
}
