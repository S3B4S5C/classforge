package com.classforge.assistant.vision;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class HybridVisionModelGateway implements VisionModelGateway {

    private final ObjectProvider<LlamaCppVisionModelGateway> llamaGateway;
    private final ObjectProvider<UnconfiguredVisionModelGateway> unconfiguredGateway;
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

    public HybridVisionModelGateway(
            ObjectProvider<LlamaCppVisionModelGateway> llamaGateway,
            ObjectProvider<UnconfiguredVisionModelGateway> unconfiguredGateway,
            VisionHybridModelGateway hybridGateway,
            UmlClassRegionDetector classRegionDetector,
            VisionGeometryClassMappingValidator mappingValidator,
            VisionHybridImageFactory imageFactory,
            UmlDiagramGeometryAnalyzer geometryAnalyzer,
            VisionRelationshipEvidenceSheetRenderer sheetRenderer,
            VisionHybridProposalAssembler assembler,
            @Value("${classforge.assistant.vision.dense-hybrid.enabled:true}") boolean enabled,
            @Value("${classforge.assistant.vision.dense-hybrid.min-classes:4}") int minClasses,
            @Value("${classforge.assistant.vision.dense-hybrid.fallback-to-semantic:true}") boolean fallbackToSemantic
    ) {
        this.llamaGateway = llamaGateway;
        this.unconfiguredGateway = unconfiguredGateway;
        this.hybridGateway = hybridGateway;
        this.classRegionDetector = classRegionDetector;
        this.mappingValidator = mappingValidator;
        this.imageFactory = imageFactory;
        this.geometryAnalyzer = geometryAnalyzer;
        this.sheetRenderer = sheetRenderer;
        this.assembler = assembler;
        this.enabled = enabled;
        this.minClasses = minClasses;
        this.fallbackToSemantic = fallbackToSemantic;
    }

    @Override
    public VisionUmlProposal analyze(VisionNormalizedImage image, VisionProjectContext context) {
        VisionModelGateway semantic = llamaGateway.getIfAvailable();
        if (semantic == null) {
            semantic = unconfiguredGateway.getIfAvailable();
        }
        if (semantic == null) {
            throw new IllegalStateException("No hay VisionModelGateway proveedor disponible.");
        }
        return new VisionHybridAnalyzer(
                semantic,
                hybridGateway,
                classRegionDetector,
                mappingValidator,
                imageFactory,
                geometryAnalyzer,
                sheetRenderer,
                assembler,
                enabled,
                minClasses,
                fallbackToSemantic
        ).analyze(image, context);
    }
}
