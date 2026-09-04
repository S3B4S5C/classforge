package com.classforge.assistant.vision;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class VisionImageNormalizer {

    public VisionNormalizedImage normalize(VisionImageInput input) {
        if ("image/webp".equals(input.mediaType())) {
            // JDK ImageIO does not decode WEBP by default. Keep validated WEBP bytes
            // canonical and let the future VLM adapter consume them directly.
            return new VisionNormalizedImage(
                    input.originalFilename(),
                    input.mediaType(),
                    input.mediaType(),
                    input.bytes(),
                    input.width(),
                    input.height(),
                    input.sha256(),
                    false
            );
        }

        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(input.bytes()));
            if (decoded == null) {
                throw new AssistantImageValidationException("La imagen no se pudo normalizar.");
            }

            BufferedImage oriented = decoded;
            if ("image/jpeg".equals(input.mediaType())) {
                oriented = orient(decoded, JpegExifOrientation.read(input.bytes()));
            }

            BufferedImage rgb = toRgb(oriented);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(rgb, "png", output)) {
                throw new AssistantImageValidationException("No pudimos convertir la imagen a PNG normalizado.");
            }

            byte[] normalized = output.toByteArray();
            return new VisionNormalizedImage(
                    input.originalFilename(),
                    input.mediaType(),
                    "image/png",
                    normalized,
                    rgb.getWidth(),
                    rgb.getHeight(),
                    sha256(normalized),
                    true
            );
        } catch (AssistantImageValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AssistantImageValidationException("La imagen no se pudo normalizar.");
        }
    }

    private BufferedImage toRgb(BufferedImage source) {
        BufferedImage target = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR
            );
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private BufferedImage orient(BufferedImage source, int orientation) {
        if (orientation == 1) {
            return source;
        }

        int width = source.getWidth();
        int height = source.getHeight();
        AffineTransform transform = new AffineTransform();
        int targetWidth = width;
        int targetHeight = height;

        switch (orientation) {
            case 2 -> {
                transform.translate(width, 0);
                transform.scale(-1, 1);
            }
            case 3 -> {
                transform.translate(width, height);
                transform.rotate(Math.PI);
            }
            case 4 -> {
                transform.translate(0, height);
                transform.scale(1, -1);
            }
            case 5 -> {
                targetWidth = height;
                targetHeight = width;
                transform.rotate(Math.PI / 2);
                transform.scale(1, -1);
            }
            case 6 -> {
                targetWidth = height;
                targetHeight = width;
                transform.translate(height, 0);
                transform.rotate(Math.PI / 2);
            }
            case 7 -> {
                targetWidth = height;
                targetHeight = width;
                transform.translate(height, width);
                transform.scale(-1, 1);
                transform.rotate(3 * Math.PI / 2);
            }
            case 8 -> {
                targetWidth = height;
                targetHeight = width;
                transform.translate(0, width);
                transform.rotate(3 * Math.PI / 2);
            }
            default -> {
                return source;
            }
        }

        BufferedImage target = new BufferedImage(
                targetWidth,
                targetHeight,
                BufferedImage.TYPE_INT_ARGB
        );
        new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR)
                .filter(source, target);
        return target;
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
