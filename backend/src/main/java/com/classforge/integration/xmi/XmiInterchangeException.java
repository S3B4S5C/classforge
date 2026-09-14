package com.classforge.integration.xmi;

public class XmiInterchangeException extends RuntimeException {

    private final String code;

    public XmiInterchangeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public XmiInterchangeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
