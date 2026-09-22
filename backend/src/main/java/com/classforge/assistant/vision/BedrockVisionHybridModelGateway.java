package com.classforge.assistant.vision;

import com.classforge.assistant.bedrock.BedrockConverseSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

@Component
@ConditionalOnProperty(name = "classforge.assistant.vision.provider", havingValue = "bedrock")
public class BedrockVisionHybridModelGateway implements VisionHybridModelGateway {

    private final JsonMapper jsonMapper;
    private final VisionHybridPromptBuilder promptBuilder;
    private final BedrockConverseSupport bedrock;
    private final String modelId;
    private final int localizationTokens;
    private final int relationshipTokens;
    private final int multiplicityTokens;

    public BedrockVisionHybridModelGateway(
            JsonMapper jsonMapper,
            VisionHybridPromptBuilder promptBuilder,
            BedrockConverseSupport bedrock,
            @Value("${classforge.assistant.bedrock.vision-model:us.amazon.nova-2-lite-v1:0}") String modelId,
            @Value("${classforge.assistant.vision.dense-hybrid.localization-max-completion-tokens:1200}") int localizationTokens,
            @Value("${classforge.assistant.vision.dense-hybrid.relationship-max-completion-tokens:512}") int relationshipTokens,
            @Value("${classforge.assistant.vision.dense-hybrid.multiplicity-max-completion-tokens:128}") int multiplicityTokens
    ) {
        this.jsonMapper = jsonMapper;
        this.promptBuilder = promptBuilder;
        this.bedrock = bedrock;
        this.modelId = required(modelId, "Bedrock vision model");
        this.localizationTokens = Math.max(256, localizationTokens);
        this.relationshipTokens = Math.max(256, relationshipTokens);
        this.multiplicityTokens = Math.max(1, multiplicityTokens);
    }

    @Override
    public VisionGeometryClassMappingProposal mapClassRegions(
            VisionNormalizedImage labeledRegionsImage,
            List<VisionGeometryClassRegion> regions,
            List<VisionClassProposal> classes
    ) {
        try {
            JsonNode input = structured(
                    labeledRegionsImage,
                    promptBuilder.mappingSystemPrompt(),
                    promptBuilder.mappingUserPrompt(regions, classes),
                    VisionGeometryClassMappingJsonSchema.json(),
                    localizationTokens,
                    "vision_geometry_class_mapping"
            );
            return jsonMapper.treeToValue(input, VisionGeometryClassMappingProposal.class);
        } catch (VisionModelGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw outputContract("El mapeo cerrado Bx->classRef no cumple su contrato JSON.", exception);
        }
    }

    @Override
    public VisionHybridRelationshipClassificationProposal classifyRelationship(
            VisionNormalizedImage evidencePanel,
            VisionGeometryEdgeCandidate candidate,
            List<VisionClassProposal> classes
    ) {
        try {
            JsonNode input = structured(
                    evidencePanel,
                    promptBuilder.relationshipSystemPrompt(),
                    promptBuilder.relationshipUserPrompt(candidate, classes),
                    VisionHybridRelationshipJsonSchema.jsonForEdge(candidate.edgeId()),
                    relationshipTokens,
                    "vision_relationship_classification"
            );
            VisionHybridRelationshipClassificationProposal proposal = jsonMapper.treeToValue(
                    input, VisionHybridRelationshipClassificationProposal.class
            );
            validateSingletonAnnotation(proposal, candidate.edgeId());
            return proposal;
        } catch (VisionModelGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw outputContract("La anotacion local de relaciones no cumple su contrato JSON.", exception);
        }
    }

    @Override
    public VisionHybridMultiplicityTranscription transcribeMultiplicity(
            VisionNormalizedImage transcriptionPanel,
            VisionGeometryEdgeCandidate candidate,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass
    ) {
        try {
            JsonNode input = structured(
                    transcriptionPanel,
                    promptBuilder.multiplicityTranscriptionSystemPrompt(),
                    promptBuilder.multiplicityUserPrompt(candidate, endpoint, endpointClass),
                    VisionHybridMultiplicityTranscriptionJsonSchema.jsonForEndpoint(candidate.edgeId(), endpoint),
                    multiplicityTokens,
                    "vision_multiplicity_transcription"
            );
            VisionHybridMultiplicityTranscription transcription = jsonMapper.treeToValue(
                    input, VisionHybridMultiplicityTranscription.class
            );
            validateMultiplicityTranscription(transcription, candidate.edgeId(), endpoint);
            return normalizeMultiplicityTranscription(transcription);
        } catch (VisionModelGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw outputContract("La transcripcion local de multiplicidad no cumple su contrato JSON.", exception);
        }
    }

    @Override
    public VisionHybridMultiplicityAttribution attributeMultiplicity(
            VisionNormalizedImage attributionPanel,
            VisionGeometryEdgeCandidate candidate,
            VisionHybridEndpoint endpoint,
            VisionClassProposal endpointClass,
            String candidateRawLabel,
            List<String> visibleCompetingEdgeIds
    ) {
        try {
            JsonNode input = structured(
                    attributionPanel,
                    promptBuilder.multiplicityAttributionSystemPrompt(),
                    promptBuilder.multiplicityAttributionUserPrompt(
                            candidate, endpoint, endpointClass, candidateRawLabel, visibleCompetingEdgeIds
                    ),
                    VisionHybridMultiplicityAttributionJsonSchema.jsonForMultiplicityAttribution(
                            candidate.edgeId(), endpoint, visibleCompetingEdgeIds
                    ),
                    multiplicityTokens,
                    "vision_multiplicity_attribution"
            );
            VisionHybridMultiplicityAttribution attribution = jsonMapper.treeToValue(
                    input, VisionHybridMultiplicityAttribution.class
            );
            validateMultiplicityAttribution(attribution, candidate.edgeId(), endpoint, visibleCompetingEdgeIds);
            return attribution;
        } catch (VisionModelGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw outputContract("La verificacion de ownership de multiplicidad no cumple su contrato JSON.", exception);
        }
    }

    private JsonNode structured(
            VisionNormalizedImage image,
            String systemPrompt,
            String userPrompt,
            String schema,
            int maxTokens,
            String toolName
    ) {
        try {
            return bedrock.invokeStructuredImage(
                    modelId,
                    systemPrompt,
                    userPrompt,
                    image.bytes(),
                    image.mediaType(),
                    schema,
                    toolName,
                    maxTokens
            ).input();
        } catch (Exception exception) {
            throw transport("No pudimos completar " + toolName + " contra Amazon Bedrock.", exception);
        }
    }

    private void validateSingletonAnnotation(VisionHybridRelationshipClassificationProposal proposal, String edgeId) {
        List<VisionHybridEdgeClassification> edges = proposal == null ? null : proposal.edges();
        if (edges == null || edges.size() != 1) {
            throw outputContract("La clasificacion local debe contener exactamente un edge.", null);
        }
        if (!edgeId.equals(edges.getFirst().edgeId())) {
            throw outputContract("La anotacion local devolvio un edgeId distinto del panel solicitado.", null);
        }
    }

    private void validateMultiplicityTranscription(
            VisionHybridMultiplicityTranscription transcription,
            String edgeId,
            VisionHybridEndpoint endpoint
    ) {
        if (transcription == null || !edgeId.equals(transcription.edgeId()) || endpoint != transcription.endpoint()) {
            throw outputContract("La transcripcion local no corresponde al endpoint solicitado.", null);
        }
        if (transcription.rawLabel() == null
                || transcription.rawLabel().isBlank() || transcription.rawLabel().length() > 16) {
            throw outputContract("La etiqueta local de multiplicidad excede su contrato.", null);
        }
        if (!validConfidence(transcription.confidence())) {
            throw outputContract("La confianza de transcripcion de multiplicidad no cumple su contrato.", null);
        }
    }

    private void validateMultiplicityAttribution(
            VisionHybridMultiplicityAttribution attribution,
            String edgeId,
            VisionHybridEndpoint endpoint,
            List<String> visibleCompetingEdgeIds
    ) {
        if (attribution == null || !edgeId.equals(attribution.edgeId()) || endpoint != attribution.endpoint()) {
            throw outputContract("La verificacion de ownership no corresponde al endpoint solicitado.", null);
        }
        if (!java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(edgeId, "AMBIGUOUS", "NONE"),
                        visibleCompetingEdgeIds.stream()
                ).toList().contains(attribution.owner())) {
            throw outputContract("El ownership de multiplicidad no cumple su contrato.", null);
        }
        if (!validConfidence(attribution.confidence())) {
            throw outputContract("La confianza de ownership de multiplicidad no cumple su contrato.", null);
        }
    }

    private VisionHybridMultiplicityTranscription normalizeMultiplicityTranscription(
            VisionHybridMultiplicityTranscription transcription
    ) {
        String rawLabel = transcription.rawLabel().trim();
        return new VisionHybridMultiplicityTranscription(
                transcription.edgeId(),
                transcription.endpoint(),
                "NONE".equalsIgnoreCase(rawLabel) ? null : rawLabel,
                transcription.confidence()
        );
    }

    private boolean validConfidence(Double confidence) {
        return confidence == null || (confidence >= 0.0 && confidence <= 1.0);
    }

    private VisionModelGatewayException outputContract(String message, Throwable cause) {
        return new VisionModelGatewayException(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, message, cause);
    }

    private VisionModelGatewayException transport(String message, Throwable cause) {
        return new VisionModelGatewayException(VisionModelGatewayException.Reason.TRANSPORT, message, cause);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }
}
