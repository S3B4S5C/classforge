package com.classforge.assistant.vision;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class VisionHybridImageFactory {

    public VisionNormalizedImage png(String name, byte[] bytes, int width, int height) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Hybrid diagnostic image bytes are required");
        }
        return new VisionNormalizedImage(
                name,
                "image/png",
                "image/png",
                bytes,
                width,
                height,
                sha256(bytes),
                true
        );
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo calcular SHA-256 de imagen hibrida.", exception);
        }
    }
}
