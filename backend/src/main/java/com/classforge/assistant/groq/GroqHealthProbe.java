package com.classforge.assistant.groq;

import com.classforge.assistant.AssistantRuntimeStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.*;

/** Cached model availability check; listing models does not generate model completions. */
@Component
public class GroqHealthProbe {
    private final GroqClient client;
    private final String textModel, visionModel, speechModel;
    private Availability cached;
    private long checkedAt;
    public GroqHealthProbe(GroqClient client,
            @Value("${classforge.assistant.groq.text-model:openai/gpt-oss-120b}") String textModel,
            @Value("${classforge.assistant.groq.vision-model:qwen/qwen3.8-27b}") String visionModel,
            @Value("${classforge.assistant.groq.speech-model:whisper-large-v3-turbo}") String speechModel) {
        this.client = client; this.textModel = textModel; this.visionModel = visionModel; this.speechModel = speechModel;
    }
    public synchronized Availability probe() {
        if (!client.isConfigured()) return unavailable("NOT_CONFIGURED", "Configura GROQ_API_KEY en el backend.");
        long now = System.nanoTime();
        if (cached != null && now - checkedAt < 60_000_000_000L) return cached;
        try {
            var response = client.models();
            Set<String> ids = new HashSet<>();
            var data = response.get("data");
            if (data != null && data.isArray()) for (var model : data) {
                var id = model.get("id"); if (id != null && id.isString()) ids.add(id.asString());
            }
            long latency = (System.nanoTime() - now) / 1_000_000;
            cached = new Availability(status("groq-text", textModel, ids, latency),
                    status("groq-speech", speechModel, ids, latency), status("groq-vision", visionModel, ids, latency));
        } catch (GroqApiException failure) {
            cached = unavailable(failure.statusCode() == 429 ? "RATE_LIMITED" : failure.statusCode() == 401 ? "AUTH_ERROR" : "UNAVAILABLE", failure.getMessage());
        }
        checkedAt = now;
        return cached;
    }
    private AssistantRuntimeStatus status(String name, String model, Set<String> ids, long latency) {
        boolean available = ids.contains(model);
        return new AssistantRuntimeStatus(name, available, available ? "READY" : "MODEL_UNAVAILABLE", latency,
                model + (available ? " · disponible en Groq; sujeto a cuota" : " · no disponible en la cuenta Groq"));
    }
    private Availability unavailable(String state, String message) {
        return new Availability(new AssistantRuntimeStatus("groq-text", false, state, 0, message),
                new AssistantRuntimeStatus("groq-speech", false, state, 0, message),
                new AssistantRuntimeStatus("groq-vision", false, state, 0, message));
    }
    public record Availability(AssistantRuntimeStatus text, AssistantRuntimeStatus speech, AssistantRuntimeStatus vision) { }
}
