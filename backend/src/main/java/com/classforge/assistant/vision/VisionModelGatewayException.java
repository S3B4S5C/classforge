package com.classforge.assistant.vision;

import com.classforge.assistant.AssistantPlanningException;

public class VisionModelGatewayException extends AssistantPlanningException {

    public enum Reason {
        TRANSPORT,
        OUTPUT_CONTRACT
    }

    private final Reason reason;

    public VisionModelGatewayException(
            Reason reason,
            String message
    ) {
        super(message);
        this.reason = reason;
    }

    public VisionModelGatewayException(
            Reason reason,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
