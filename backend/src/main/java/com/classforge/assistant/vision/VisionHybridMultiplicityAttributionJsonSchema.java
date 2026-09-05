package com.classforge.assistant.vision;

import java.util.List;

public final class VisionHybridMultiplicityAttributionJsonSchema {

    private VisionHybridMultiplicityAttributionJsonSchema() {
    }

    public static String jsonForMultiplicityAttribution(
            String currentEdgeId,
            VisionHybridEndpoint endpoint,
            List<String> visibleCompetingEdgeIds
    ) {
        List<String> owners = java.util.stream.Stream.concat(
                java.util.stream.Stream.of(currentEdgeId),
                visibleCompetingEdgeIds.stream().sorted()
        ).distinct().toList();
        String ownerEnum = java.util.stream.Stream.concat(owners.stream(), java.util.stream.Stream.of("AMBIGUOUS", "NONE"))
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        return """
                {"type":"object","additionalProperties":false,
                "required":["edgeId","endpoint","owner","confidence"],
                "properties":{"edgeId":{"type":"string","enum":["%s"]},
                "endpoint":{"type":"string","enum":["%s"]},
                "owner":{"type":"string","enum":[%s]},
                "confidence":{"type":["number","null"],"minimum":0,"maximum":1}}}
                """.formatted(currentEdgeId.replace("\\", "\\\\").replace("\"", "\\\""), endpoint.name(), ownerEnum);
    }
}
