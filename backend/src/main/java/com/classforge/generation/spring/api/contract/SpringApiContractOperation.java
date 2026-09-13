package com.classforge.generation.spring.api.contract;

import java.util.List;

public record SpringApiContractOperation(
        SpringApiHttpMethod method,
        String path,
        String operationId,
        String tag,
        String summary,
        boolean authenticationRequired,
        List<SpringApiContractParameter> parameters,
        String requestSchema,
        String responseSchema,
        int successStatus
) {
    public SpringApiContractOperation {
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
    }
}
