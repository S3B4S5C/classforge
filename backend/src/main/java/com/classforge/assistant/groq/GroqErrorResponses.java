package com.classforge.assistant.groq;

import org.springframework.http.*;
import java.util.*;

/** A provider 401 must never log the human user out of ClassForge. */
public final class GroqErrorResponses {
    private GroqErrorResponses() { }
    public static ResponseEntity<Map<String, Object>> from(Throwable failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof GroqApiException groq) {
                int status = groq.statusCode() == 429 ? 429 : 503;
                var body = new LinkedHashMap<String, Object>();
                body.put("error", status == 429 ? "GROQ_RATE_LIMIT" : "GROQ_UNAVAILABLE");
                body.put("message", groq.getMessage());
                body.put("retryAfterSeconds", groq.retryAfterSeconds());
                var builder = ResponseEntity.status(status);
                if (groq.retryAfterSeconds() > 0) builder.header(HttpHeaders.RETRY_AFTER, Long.toString(groq.retryAfterSeconds()));
                return builder.body(body);
            }
        }
        return null;
    }
}
