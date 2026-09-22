package com.classforge.assistant.vision;

import com.classforge.assistant.bedrock.BedrockConverseSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "classforge.assistant.vision.provider", havingValue = "bedrock")
public class BedrockVisionModelGateway implements VisionModelGateway {

    private final JsonMapper jsonMapper;
    private final VisionPromptBuilder promptBuilder;
    private final BedrockConverseSupport bedrock;
    private final String modelId;
    private final int maxCompletionTokens;
    private final boolean denseTwoPassEnabled;
    private final int denseTwoPassMinClasses;
    private final int relationshipMaxCompletionTokens;

    public BedrockVisionModelGateway(
            JsonMapper jsonMapper,
            VisionPromptBuilder promptBuilder,
            BedrockConverseSupport bedrock,
            @Value("${classforge.assistant.bedrock.vision-model:us.amazon.nova-2-lite-v1:0}") String modelId,
            @Value("${classforge.assistant.vision.max-completion-tokens:3200}") int maxCompletionTokens,
            @Value("${classforge.assistant.vision.dense-two-pass-enabled:true}") boolean denseTwoPassEnabled,
            @Value("${classforge.assistant.vision.dense-two-pass-min-classes:4}") int denseTwoPassMinClasses,
            @Value("${classforge.assistant.vision.relationship-max-completion-tokens:1800}") int relationshipMaxCompletionTokens
    ) {
        this.jsonMapper = jsonMapper;
        this.promptBuilder = promptBuilder;
        this.bedrock = bedrock;
        this.modelId = required(modelId, "Bedrock vision model");
        this.maxCompletionTokens = Math.max(256, maxCompletionTokens);
        this.denseTwoPassEnabled = denseTwoPassEnabled;
        this.denseTwoPassMinClasses = Math.max(2, denseTwoPassMinClasses);
        this.relationshipMaxCompletionTokens = Math.max(256, relationshipMaxCompletionTokens);
    }

    @Override
    public VisionUmlProposal analyze(VisionNormalizedImage image, VisionProjectContext context) {
        if (image == null || image.bytes() == null || image.bytes().length == 0) {
            throw contract("No hay imagen normalizada para enviar a Amazon Bedrock.");
        }

        VisionUmlProposal firstPass = executeFirstPass(image, context);
        if (!shouldRunRelationshipPass(firstPass)) {
            return firstPass;
        }
        return mergeRelationshipPass(firstPass, executeRelationshipPass(image, firstPass));
    }

    private VisionUmlProposal executeFirstPass(VisionNormalizedImage image, VisionProjectContext context) {
        JsonNode input = invokeStructuredImage(
                image,
                promptBuilder.systemPrompt(),
                promptBuilder.userPrompt(context, image.width(), image.height()),
                VisionUmlProposalJsonSchema.json(),
                "vision_uml_proposal",
                maxCompletionTokens
        );
        try {
            VisionUmlProposal proposal = jsonMapper.treeToValue(
                    input, VisionUmlProposal.class
            );
            return VisionProposalOutcomeNormalizer.normalize(proposal);
        } catch (Exception exception) {
            throw outputContract(
                    "La salida de Bedrock no cumple VisionUmlProposal; se rechazo sin reparacion heuristica.",
                    exception
            );
        }
    }

    private VisionRelationshipPassProposal executeRelationshipPass(
            VisionNormalizedImage image,
            VisionUmlProposal firstPass
    ) {
        JsonNode input = invokeStructuredImage(
                image,
                promptBuilder.relationshipSystemPrompt(),
                promptBuilder.relationshipUserPrompt(firstPass, image.width(), image.height()),
                VisionRelationshipPassJsonSchema.json(),
                "vision_relationship_pass",
                relationshipMaxCompletionTokens
        );
        try {
            return jsonMapper.treeToValue(input, VisionRelationshipPassProposal.class);
        } catch (Exception exception) {
            throw outputContract(
                    "La segunda pasada Bedrock no cumple VisionRelationshipPassProposal; se rechazo sin reparacion heuristica.",
                    exception
            );
        }
    }

    private JsonNode invokeStructuredImage(
            VisionNormalizedImage image,
            String systemPrompt,
            String userPrompt,
            String schema,
            String toolName,
            int maxTokens
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
        } catch (VisionModelGatewayException exception) {
            throw exception;
        } catch (Exception exception) {
            throw transport(
                    "No pudimos completar la inferencia visual contra Amazon Bedrock con " + modelId + ".",
                    exception
            );
        }
    }

    private boolean shouldRunRelationshipPass(VisionUmlProposal firstPass) {
        return denseTwoPassEnabled
                && firstPass != null
                && firstPass.safeClasses().size() >= denseTwoPassMinClasses;
    }

    private VisionUmlProposal mergeRelationshipPass(
            VisionUmlProposal firstPass,
            VisionRelationshipPassProposal relationshipPass
    ) {
        if (relationshipPass == null) {
            throw contract("La segunda pasada visual no devolvio propuesta de relaciones.");
        }

        Set<String> allowedRefs = new LinkedHashSet<>();
        for (VisionClassProposal umlClass : firstPass.safeClasses()) {
            allowedRefs.add(normalizeRef(requiredProposal(umlClass.ref(), "class.ref")));
        }
        if (allowedRefs.isEmpty()) {
            throw contract("La segunda pasada visual no puede ejecutarse sin refs de clases confirmadas.");
        }

        Set<String> seenRelationships = new LinkedHashSet<>();
        for (VisionRelationshipProposal relationship : relationshipPass.safeRelationships()) {
            String source = normalizeRef(requiredProposal(relationship.sourceRef(), "relationship.sourceRef"));
            String target = normalizeRef(requiredProposal(relationship.targetRef(), "relationship.targetRef"));
            if (!allowedRefs.contains(source) || !allowedRefs.contains(target)) {
                throw contract(
                        "La segunda pasada visual intento usar un ref de clase no confirmado: "
                                + relationship.sourceRef() + " -> " + relationship.targetRef() + "."
                );
            }

            String type = requiredProposal(relationship.type(), "relationship.type")
                    .toUpperCase(Locale.ROOT);
            String duplicateKey;
            if ("ASSOCIATION".equals(type)) {
                duplicateKey = source.compareTo(target) <= 0
                        ? type + "|" + source + "|" + target
                        : type + "|" + target + "|" + source;
            } else {
                duplicateKey = type + "|" + source + "|" + target;
            }
            if (!seenRelationships.add(duplicateKey)) {
                throw contract("La segunda pasada visual repitio una relacion: " + duplicateKey + ".");
            }
        }

        List<String> warnings = new ArrayList<>();
        warnings.addAll(firstPass.safeWarnings());
        warnings.addAll(relationshipPass.safeWarnings());

        return new VisionUmlProposal(
                firstPass.summary(),
                firstPass.safeClasses(),
                VisionAssociationClassTopology.reconcile(firstPass, relationshipPass.safeRelationships()),
                firstPass.safeAssociationClasses(),
                List.copyOf(new LinkedHashSet<>(warnings)),
                combinedConfidence(firstPass.confidence(), relationshipPass.confidence())
        );
    }

    private Double combinedConfidence(Double first, Double second) {
        if (first == null) return second;
        if (second == null) return first;
        return Math.min(first, second);
    }


    private String normalizeRef(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String requiredProposal(String value, String field) {
        if (value == null || value.isBlank()) {
            throw contract("La segunda pasada visual no incluye " + field + ".");
        }
        return value.trim();
    }

    private VisionModelGatewayException contract(String message) {
        return new VisionModelGatewayException(VisionModelGatewayException.Reason.OUTPUT_CONTRACT, message);
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
