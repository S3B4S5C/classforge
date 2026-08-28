package com.classforge.project.application;

public class ProjectRevisionConflictException extends RuntimeException {

    private final long requestedRevision;
    private final long currentRevision;

    public ProjectRevisionConflictException(
            long requestedRevision,
            long currentRevision
    ) {
        super(
                "The project has changed since revision "
                        + requestedRevision
                        + ". Current revision is "
                        + currentRevision
        );
        this.requestedRevision = requestedRevision;
        this.currentRevision = currentRevision;
    }

    public long getRequestedRevision() {
        return requestedRevision;
    }

    public long getCurrentRevision() {
        return currentRevision;
    }
}