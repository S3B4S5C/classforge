package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class VisionHybridAnalyzer implements VisionModelGateway {

    private final VisionModelGateway semanticGateway;
    private final VisionHybridModelGateway hybridGateway;
    private final UmlClassRegionDetector classRegionDetector;
    private final VisionGeometryClassMappingValidator mappingValidator;
    private final VisionHybridImageFactory imageFactory;
    private final UmlDiagramGeometryAnalyzer geometryAnalyzer;
    private final VisionRelationshipEvidenceSheetRenderer sheetRenderer;
    private final VisionHybridProposalAssembler assembler;
    private final boolean enabled;
    private final int minClasses;
    private final boolean fallbackToSemantic;
    private final boolean geometryOnly;
    private volatile VisionHybridDiagnostics lastDiagnostics;

    public VisionHybridAnalyzer(
            VisionModelGateway semanticGateway,
            VisionHybridModelGateway hybridGateway,
            UmlClassRegionDetector classRegionDetector,
            VisionGeometryClassMappingValidator mappingValidator,
            VisionHybridImageFactory imageFactory,
            UmlDiagramGeometryAnalyzer geometryAnalyzer,
            VisionRelationshipEvidenceSheetRenderer sheetRenderer,
            VisionHybridProposalAssembler assembler,
            boolean enabled,
            int minClasses,
            boolean fallbackToSemantic
    ) {
        this(
                semanticGateway, hybridGateway, classRegionDetector, mappingValidator, imageFactory,
                geometryAnalyzer, sheetRenderer, assembler, enabled, minClasses, fallbackToSemantic, false
        );
    }

    public VisionHybridAnalyzer(
            VisionModelGateway semanticGateway,
            VisionHybridModelGateway hybridGateway,
            UmlClassRegionDetector classRegionDetector,
            VisionGeometryClassMappingValidator mappingValidator,
            VisionHybridImageFactory imageFactory,
            UmlDiagramGeometryAnalyzer geometryAnalyzer,
            VisionRelationshipEvidenceSheetRenderer sheetRenderer,
            VisionHybridProposalAssembler assembler,
            boolean enabled,
            int minClasses,
            boolean fallbackToSemantic,
            boolean geometryOnly
    ) {
        this.semanticGateway = semanticGateway;
        this.hybridGateway = hybridGateway;
        this.classRegionDetector = classRegionDetector;
        this.mappingValidator = mappingValidator;
        this.imageFactory = imageFactory;
        this.geometryAnalyzer = geometryAnalyzer;
        this.sheetRenderer = sheetRenderer;
        this.assembler = assembler;
        this.enabled = enabled;
        this.minClasses = Math.max(2, minClasses);
        this.fallbackToSemantic = fallbackToSemantic;
        this.geometryOnly = geometryOnly;
    }

    @Override
    public VisionUmlProposal analyze(VisionNormalizedImage image, VisionProjectContext context) {
        lastDiagnostics = null;
        VisionUmlProposal semantic = semanticGateway.analyze(image, context);
        if (!enabled || semantic == null || semantic.safeClasses().size() < minClasses) {
            return semantic;
        }
        try {
            UmlClassRegionDetection detection = classRegionDetector.detect(
                    image,
                    semantic.safeClasses().size()
            );
            lastDiagnostics = new VisionHybridDiagnostics(detection, null, null, null, null);
            VisionNormalizedImage labeledRegions = imageFactory.png(
                    "class-regions.png",
                    detection.overlayPng(),
                    image.width(),
                    image.height()
            );
            VisionGeometryClassMappingProposal mapping = hybridGateway.mapClassRegions(
                    labeledRegions,
                    detection.safeRegions(),
                    semantic.safeClasses()
            );
            lastDiagnostics = new VisionHybridDiagnostics(detection, mapping, null, null, null);
            List<VisionGeometryClassRegion> mappedRegions = mappingValidator.validateAndBind(
                    mapping,
                    detection.safeRegions(),
                    semantic.safeClasses()
            );
            UmlDiagramGeometry geometry = geometryAnalyzer.analyze(image, mappedRegions);
            lastDiagnostics = new VisionHybridDiagnostics(detection, mapping, geometry, null, null);

            if (geometryOnly) {
                List<String> warnings = new ArrayList<>(semantic.safeWarnings());
                warnings.add("HYBRID_GEOMETRY_ONLY: CV-first class boxes/mapping/geometry ejecutados sin anotacion VLM de relaciones.");
                return new VisionUmlProposal(
                        semantic.summary(), semantic.safeClasses(), semantic.safeRelationships(),
                        List.copyOf(new LinkedHashSet<>(warnings)), semantic.confidence()
                );
            }

            if (geometry.safeEdgeCandidates().isEmpty()) {
                throw new AssistantPlanningException("OpenCV no genero pares candidatos para el diagrama denso.");
            }
            VisionNormalizedImage sheet = sheetRenderer.render(image, geometry);
            lastDiagnostics = new VisionHybridDiagnostics(detection, mapping, geometry, sheet, null);
            VisionHybridRelationshipAnnotationProposal annotation = hybridGateway.annotateRelationships(
                    sheet,
                    geometry.safeEdgeCandidates(),
                    semantic.safeClasses()
            );
            lastDiagnostics = new VisionHybridDiagnostics(detection, mapping, geometry, sheet, annotation);
            return assembler.assemble(semantic, geometry, annotation);
        } catch (RuntimeException exception) {
            if (!fallbackToSemantic) {
                if (exception instanceof VisionModelGatewayException gatewayException) {
                    throw gatewayException;
                }
                throw new VisionModelGatewayException(
                        VisionModelGatewayException.Reason.OUTPUT_CONTRACT,
                        "El pipeline hibrido rechazo la evidencia: " + compact(exception.getMessage()),
                        exception
                );
            }
            List<String> warnings = new ArrayList<>(semantic.safeWarnings());
            warnings.add("HYBRID_CV_FALLBACK: " + compact(exception.getMessage()));
            return new VisionUmlProposal(
                    semantic.summary(),
                    semantic.safeClasses(),
                    semantic.safeRelationships(),
                    List.copyOf(new LinkedHashSet<>(warnings)),
                    semantic.confidence()
            );
        }
    }

    public VisionHybridDiagnostics lastDiagnostics() {
        return lastDiagnostics;
    }

    private String compact(String message) {
        if (message == null || message.isBlank()) {
            return "fallo hibrido sin diagnostico";
        }
        String compact = message.replaceAll("\\s+", " ").trim();
        return compact.length() <= 240 ? compact : compact.substring(0, 240) + "...";
    }
}
