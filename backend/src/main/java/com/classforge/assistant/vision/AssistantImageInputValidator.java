package com.classforge.assistant.vision;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

@Component
public class AssistantImageInputValidator {

    private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    private final long maxBytes;
    private final int minDimension;
    private final int maxDimension;

    public AssistantImageInputValidator(
            @Value("${classforge.assistant.image.max-bytes:10485760}") long maxBytes,
            @Value("${classforge.assistant.image.min-dimension:64}") int minDimension,
            @Value("${classforge.assistant.image.max-dimension:8192}") int maxDimension
    ) {
        this.maxBytes = maxBytes;
        this.minDimension = minDimension;
        this.maxDimension = maxDimension;
    }

    public VisionImageInput validate(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new AssistantImageValidationException("La imagen no puede estar vacia.");
        }

        if (image.getSize() > maxBytes) {
            throw new AssistantImageValidationException(
                    "La imagen supera el limite de " + maxBytes + " bytes."
            );
        }

        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (Exception exception) {
            throw new AssistantImageValidationException("No pudimos leer la imagen recibida.");
        }

        String detectedMediaType = detectMediaType(bytes);
        String declaredMediaType = normalizeMediaType(image.getContentType());

        if (!ALLOWED_MEDIA_TYPES.contains(detectedMediaType)) {
            throw new AssistantImageValidationException(
                    "Formato no soportado. Usa PNG, JPEG o WEBP."
            );
        }

        if (declaredMediaType != null && !declaredMediaType.equals(detectedMediaType)) {
            throw new AssistantImageValidationException(
                    "El tipo MIME declarado no coincide con el contenido real de la imagen."
            );
        }

        int[] dimensions = readDimensions(detectedMediaType, bytes);
        int width = dimensions[0];
        int height = dimensions[1];

        if (width < minDimension || height < minDimension) {
            throw new AssistantImageValidationException(
                    "La imagen es demasiado pequena. Minimo "
                            + minDimension + "x" + minDimension + "."
            );
        }

        if (width > maxDimension || height > maxDimension) {
            throw new AssistantImageValidationException(
                    "La imagen es demasiado grande. Maximo "
                            + maxDimension + "x" + maxDimension + "."
            );
        }

        return new VisionImageInput(
                safeFilename(image.getOriginalFilename()),
                detectedMediaType,
                bytes,
                width,
                height,
                sha256(bytes)
        );
    }

    private String normalizeMediaType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String normalized = raw.toLowerCase(Locale.ROOT).trim();
        if ("image/jpg".equals(normalized)) {
            return "image/jpeg";
        }
        return normalized;
    }

    private String safeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return "diagram-image";
        }
        String normalized = raw.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private String detectMediaType(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return "image/png";
        }

        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }

        if (bytes.length >= 12
                && ascii(bytes, 0, 4).equals("RIFF")
                && ascii(bytes, 8, 4).equals("WEBP")) {
            return "image/webp";
        }

        return "application/octet-stream";
    }

    private int[] readDimensions(String mediaType, byte[] bytes) {
        if ("image/webp".equals(mediaType)) {
            return readWebpDimensions(bytes);
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new AssistantImageValidationException("La imagen no se pudo decodificar.");
            }
            return new int[] {image.getWidth(), image.getHeight()};
        } catch (AssistantImageValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssistantImageValidationException("La imagen no se pudo decodificar.");
        }
    }

    private int[] readWebpDimensions(byte[] bytes) {
        if (bytes.length < 30) {
            throw new AssistantImageValidationException("El archivo WEBP esta incompleto.");
        }

        String chunk = ascii(bytes, 12, 4);
        if ("VP8X".equals(chunk)) {
            int width = 1 + le24(bytes, 24);
            int height = 1 + le24(bytes, 27);
            return new int[] {width, height};
        }

        if ("VP8 ".equals(chunk)) {
            if ((bytes[23] & 0xff) != 0x9d
                    || (bytes[24] & 0xff) != 0x01
                    || (bytes[25] & 0xff) != 0x2a) {
                throw new AssistantImageValidationException("Cabecera WEBP VP8 invalida.");
            }
            int width = ((bytes[26] & 0xff) | ((bytes[27] & 0xff) << 8)) & 0x3fff;
            int height = ((bytes[28] & 0xff) | ((bytes[29] & 0xff) << 8)) & 0x3fff;
            return new int[] {width, height};
        }

        if ("VP8L".equals(chunk)) {
            if ((bytes[20] & 0xff) != 0x2f) {
                throw new AssistantImageValidationException("Cabecera WEBP VP8L invalida.");
            }
            int b1 = bytes[21] & 0xff;
            int b2 = bytes[22] & 0xff;
            int b3 = bytes[23] & 0xff;
            int b4 = bytes[24] & 0xff;
            int width = 1 + (b1 | ((b2 & 0x3f) << 8));
            int height = 1 + ((b2 >> 6) | (b3 << 2) | ((b4 & 0x0f) << 10));
            return new int[] {width, height};
        }

        throw new AssistantImageValidationException("Variante WEBP no soportada por el validador local.");
    }

    private int le24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16);
    }

    private String ascii(byte[] bytes, int offset, int length) {
        StringBuilder result = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            result.append((char) (bytes[offset + index] & 0xff));
        }
        return result.toString();
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }
}
