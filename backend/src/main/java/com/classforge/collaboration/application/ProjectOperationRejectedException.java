package com.classforge.collaboration.application;

public class ProjectOperationRejectedException
        extends RuntimeException {

    private final String code;
    private final Long currentRevision;

    public ProjectOperationRejectedException(
            String code,
            String message,
            Long currentRevision
    ) {
        super(message);
        this.code = code;
        this.currentRevision = currentRevision;
    }

    public String getCode() {
        return code;
    }

    public Long getCurrentRevision() {
        return currentRevision;
    }
}