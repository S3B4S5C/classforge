package com.classforge.assistant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
public class WhisperCppSpeechToTextGateway
        implements SpeechToTextGateway {

    static final int MAX_AUDIO_BYTES =
            4 * 1024 * 1024;

    static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(45);

    private final JsonMapper jsonMapper;
    private final HttpClient httpClient;
    private final String url;
    private final String language;

    public WhisperCppSpeechToTextGateway(
            JsonMapper jsonMapper,
            @Value("${classforge.assistant.whisper-url:http://127.0.0.1:8093}")
            String url,
            @Value("${classforge.assistant.whisper-language:es}")
            String language
    ) {
        this.jsonMapper =
                jsonMapper;

        this.url =
                url.endsWith("/")
                        ? url.substring(
                                0,
                                url.length() - 1
                        )
                        : url;

        this.language =
                language == null
                        || language.isBlank()
                        ? "es"
                        : language.trim();

        this.httpClient =
                HttpClient
                        .newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(3)
                        )
                        .build();
    }

    @Override
    public String transcribe(
            byte[] wavAudio,
            String filename
    ) {
        validateWav(
                wavAudio
        );

        String boundary =
                "ClassForgeWhisper"
                        + UUID.randomUUID()
                                .toString()
                                .replace(
                                        "-",
                                        ""
                                );

        try {
            byte[] body =
                    multipartBody(
                            boundary,
                            wavAudio,
                            filename
                    );

            HttpRequest request =
                    HttpRequest
                            .newBuilder(
                                    URI.create(
                                            url
                                                    + "/inference"
                                    )
                            )
                            .timeout(
                                    REQUEST_TIMEOUT
                            )
                            .header(
                                    "Content-Type",
                                    "multipart/form-data; boundary="
                                            + boundary
                            )
                            .POST(
                                    HttpRequest.BodyPublishers.ofByteArray(
                                            body
                                    )
                            )
                            .build();

            HttpResponse<String> response =
                    httpClient.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );

            if (
                    response.statusCode() < 200
                            || response.statusCode() >= 300
            ) {
                throw new AssistantPlanningException(
                        "whisper.cpp respondio HTTP "
                                + response.statusCode()
                                + ". No se aplico ningun cambio."
                );
            }

            return extractTranscript(
                    response.body()
            );
        } catch (
                HttpTimeoutException exception
        ) {
            throw new AssistantPlanningException(
                    "whisper.cpp supero "
                            + REQUEST_TIMEOUT.toSeconds()
                            + " segundos transcribiendo el audio. "
                            + "No se aplico ningun cambio.",
                    exception
            );
        } catch (
                AssistantPlanningException exception
        ) {
            throw exception;
        } catch (Exception exception) {
            throw new AssistantPlanningException(
                    "No pudimos comunicarnos con whisper.cpp en "
                            + url
                            + ". Verifica que whisper-server este ejecutandose.",
                    exception
            );
        }
    }

    void validateWav(
            byte[] wavAudio
    ) {
        if (
                wavAudio == null
                        || wavAudio.length < 44
        ) {
            throw new AssistantPlanningException(
                    "El audio grabado esta vacio o incompleto."
            );
        }

        if (
                wavAudio.length
                        > MAX_AUDIO_BYTES
        ) {
            throw new AssistantPlanningException(
                    "El audio supera el limite de 4 MB."
            );
        }

        boolean riff =
                wavAudio[0] == 'R'
                        && wavAudio[1] == 'I'
                        && wavAudio[2] == 'F'
                        && wavAudio[3] == 'F';

        boolean wave =
                wavAudio[8] == 'W'
                        && wavAudio[9] == 'A'
                        && wavAudio[10] == 'V'
                        && wavAudio[11] == 'E';

        if (!riff || !wave) {
            throw new AssistantPlanningException(
                    "ClassForge esperaba audio WAV PCM generado por el navegador."
            );
        }
    }

    String extractTranscript(
            String body
    ) throws Exception {
        JsonNode root =
                jsonMapper.readTree(
                        body
                );

        JsonNode text =
                root.get(
                        "text"
                );

        if (text == null) {
            throw new AssistantPlanningException(
                    "whisper.cpp devolvio una respuesta sin transcript."
            );
        }

        String transcript =
                text.asString()
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        if (transcript.isBlank()) {
            throw new AssistantPlanningException(
                    "No se detecto voz suficiente para preparar un cambio UML."
            );
        }

        if (transcript.length() > 1000) {
            throw new AssistantPlanningException(
                    "La transcripcion supera 1000 caracteres. "
                            + "Divide la instruccion en una orden mas corta."
            );
        }

        return transcript;
    }

    private byte[] multipartBody(
            String boundary,
            byte[] wavAudio,
            String filename
    ) throws IOException {
        ByteArrayOutputStream body =
                new ByteArrayOutputStream();

        writeField(
                body,
                boundary,
                "temperature",
                "0.0"
        );

        writeField(
                body,
                boundary,
                "temperature_inc",
                "0.2"
        );

        writeField(
                body,
                boundary,
                "language",
                language
        );

        writeField(
                body,
                boundary,
                "response_format",
                "json"
        );

        writeField(
                body,
                boundary,
                "prompt",
                "UML, clase, atributo, asociacion, agregacion, composicion, generalizacion, multiplicidad"
        );

        writeFile(
                body,
                boundary,
                wavAudio,
                safeFilename(
                        filename
                )
        );

        body.write(
                (
                        "--"
                                + boundary
                                + "--\r\n"
                ).getBytes(
                        StandardCharsets.UTF_8
                )
        );

        return body.toByteArray();
    }

    private void writeField(
            ByteArrayOutputStream body,
            String boundary,
            String name,
            String value
    ) throws IOException {
        body.write(
                (
                        "--"
                                + boundary
                                + "\r\n"
                                + "Content-Disposition: form-data; name=\""
                                + name
                                + "\"\r\n\r\n"
                                + value
                                + "\r\n"
                ).getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private void writeFile(
            ByteArrayOutputStream body,
            String boundary,
            byte[] wavAudio,
            String filename
    ) throws IOException {
        body.write(
                (
                        "--"
                                + boundary
                                + "\r\n"
                                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                                + filename
                                + "\"\r\n"
                                + "Content-Type: audio/wav\r\n\r\n"
                ).getBytes(
                        StandardCharsets.UTF_8
                )
        );

        body.write(
                wavAudio
        );

        body.write(
                "\r\n".getBytes(
                        StandardCharsets.UTF_8
                )
        );
    }

    private String safeFilename(
            String filename
    ) {
        if (
                filename == null
                        || filename.isBlank()
        ) {
            return "classforge-voice.wav";
        }

        return filename
                .replaceAll(
                        "[^a-zA-Z0-9._-]",
                        "_"
                );
    }
}