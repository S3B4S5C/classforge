package com.classforge.assistant;

public interface SpeechToTextGateway {

    String transcribe(
            byte[] wavAudio,
            String filename
    );
}