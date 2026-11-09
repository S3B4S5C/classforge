package com.classforge.assistant.groq;

import com.classforge.assistant.AssistantPlanningException;

/** Carries safe provider status; never carries response bodies or credentials. */
public class GroqApiException extends AssistantPlanningException {
    private final int statusCode;
    private final long retryAfterSeconds;
    public GroqApiException(int statusCode, long retryAfterSeconds, String message) {
        this(statusCode, retryAfterSeconds, message, null);
    }
    public GroqApiException(int statusCode, long retryAfterSeconds, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryAfterSeconds = Math.max(0, Math.min(86400, retryAfterSeconds));
    }
    public int statusCode() { return statusCode; }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
