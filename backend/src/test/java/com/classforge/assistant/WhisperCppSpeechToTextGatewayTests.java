package com.classforge.assistant;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WhisperCppSpeechToTextGatewayTests {

    private final WhisperCppSpeechToTextGateway gateway =
            new WhisperCppSpeechToTextGateway(
                    JsonMapper.builder()
                            .build(),
                    "http://127.0.0.1:8093",
                    "es"
            );

    @Test
    void extractsAndNormalizesTranscript()
            throws Exception {
        assertEquals(
                "Conecta Animal con Veterinario",
                gateway.extractTranscript(
                        """
                        {
                          "text": "  Conecta   Animal con Veterinario  "
                        }
                        """
                )
        );
    }

    @Test
    void rejectsEmptyTranscript() {
        assertThrows(
                AssistantPlanningException.class,
                () ->
                        gateway.extractTranscript(
                                """
                                {"text":"   "}
                                """
                        )
        );
    }

    @Test
    void rejectsNonWavPayload() {
        assertThrows(
                AssistantPlanningException.class,
                () ->
                        gateway.validateWav(
                                new byte[128]
                        )
        );
    }

    @Test
    void acceptsRiffWaveHeader() {
        byte[] wav =
                new byte[128];

        wav[0] = 'R';
        wav[1] = 'I';
        wav[2] = 'F';
        wav[3] = 'F';

        wav[8] = 'W';
        wav[9] = 'A';
        wav[10] = 'V';
        wav[11] = 'E';

        gateway.validateWav(
                wav
        );
    }
}