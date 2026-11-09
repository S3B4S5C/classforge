package com.classforge.assistant.groq;

import com.classforge.assistant.SpeechToTextGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "classforge.assistant.voice.provider", havingValue = "groq")
public class GroqSpeechToTextGateway implements SpeechToTextGateway {
    private final GroqClient client;
    private final String model;
    private final String language;
    public GroqSpeechToTextGateway(GroqClient client,
            @Value("${classforge.assistant.groq.speech-model:whisper-large-v3-turbo}") String model,
            @Value("${classforge.assistant.groq.language:es}") String language) {
        this.client = client; this.model = model; this.language = language;
    }
    @Override public String transcribe(byte[] wavAudio, String filename) {
        var result = client.transcribe(wavAudio, "audio/wav", filename, model, language);
        var text = result.get("text");
        if (text == null || !text.isString() || text.asString().isBlank() || text.asString().length() > 4000) {
            throw new GroqApiException(502, 0, "Groq no devolvió una transcripción válida de hasta 4000 caracteres.");
        }
        return text.asString().trim();
    }
}
