package com.classforge.project.application;

public class ProjectInvitationConflictException extends RuntimeException {

    private final String code;

    public ProjectInvitationConflictException(
            String code,
            String message
    ) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
