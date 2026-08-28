package com.classforge.collaboration.application;

public class ProjectCommandRejectedException
        extends RuntimeException {

    private final String code;

    public ProjectCommandRejectedException(
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