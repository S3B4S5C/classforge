package com.classforge.assistant;

public class AssistantPlanningException
        extends RuntimeException {

    private final AssistantPlanningStage stage;
    private final String source;
    private final String transcript;
    private final AssistantSemanticPlan attemptedPlan;

    public AssistantPlanningException(
            String message
    ) {
        this(
                message,
                null,
                null,
                null,
                null,
                null
        );
    }

    public AssistantPlanningException(
            String message,
            Throwable cause
    ) {
        this(
                message,
                cause,
                null,
                null,
                null,
                null
        );
    }

    private AssistantPlanningException(
            String message,
            Throwable cause,
            AssistantPlanningStage stage,
            String source,
            String transcript,
            AssistantSemanticPlan attemptedPlan
    ) {
        super(
                message,
                cause
        );

        this.stage =
                stage;

        this.source =
                source;

        this.transcript =
                transcript;

        this.attemptedPlan =
                attemptedPlan;
    }

    public AssistantPlanningException withDiagnostic(
            AssistantPlanningStage stage,
            String source,
            String transcript,
            AssistantSemanticPlan attemptedPlan
    ) {
        return new AssistantPlanningException(
                getMessage(),
                this,
                stage,
                source,
                transcript,
                attemptedPlan
        );
    }

    public AssistantPlanningStage stage() {
        return stage;
    }

    public String source() {
        return source;
    }

    public String transcript() {
        return transcript;
    }

    public AssistantSemanticPlan attemptedPlan() {
        return attemptedPlan;
    }
}