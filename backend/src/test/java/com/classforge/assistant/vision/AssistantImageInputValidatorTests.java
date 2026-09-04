package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssistantImageInputValidatorTests {

    @Test
    void acceptsRealPngAndRejectsMimeSpoofing() throws Exception {
        AssistantImageInputValidator validator =
                new AssistantImageInputValidator(10_000_000L, 64, 8192);

        byte[] png = png(180, 120);
        VisionImageInput accepted = validator.validate(
                new MockMultipartFile("image", "diagram.png", "image/png", png)
        );

        assertEquals("image/png", accepted.mediaType());
        assertEquals(180, accepted.width());
        assertEquals(120, accepted.height());
        assertEquals(64, accepted.sha256().length());

        assertThrows(
                AssistantImageValidationException.class,
                () -> validator.validate(
                        new MockMultipartFile("image", "fake.png", "image/png", "not-an-image".getBytes())
                )
        );
    }

    @Test
    void rejectsOversizedAndTinyImages() throws Exception {
        byte[] png = png(80, 80);

        AssistantImageInputValidator tooSmallLimit =
                new AssistantImageInputValidator(20L, 64, 8192);
        assertThrows(
                AssistantImageValidationException.class,
                () -> tooSmallLimit.validate(
                        new MockMultipartFile("image", "diagram.png", "image/png", png)
                )
        );

        AssistantImageInputValidator minDimension =
                new AssistantImageInputValidator(10_000_000L, 100, 8192);
        assertThrows(
                AssistantImageValidationException.class,
                () -> minDimension.validate(
                        new MockMultipartFile("image", "diagram.png", "image/png", png)
                )
        );
    }

    @Test
    void validatesWebpHeaderWithoutNeedingAnImageIoPlugin() {
        byte[] webp = new byte[30];
        putAscii(webp, 0, "RIFF");
        putAscii(webp, 8, "WEBP");
        putAscii(webp, 12, "VP8X");
        int widthMinusOne = 319;
        int heightMinusOne = 199;
        putLe24(webp, 24, widthMinusOne);
        putLe24(webp, 27, heightMinusOne);

        AssistantImageInputValidator validator =
                new AssistantImageInputValidator(10_000_000L, 64, 8192);
        VisionImageInput accepted = validator.validate(
                new MockMultipartFile("image", "diagram.webp", "image/webp", webp)
        );

        assertEquals("image/webp", accepted.mediaType());
        assertEquals(320, accepted.width());
        assertEquals(200, accepted.height());
        assertTrue(accepted.bytes().length > 0);
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0x00ffffff);
            }
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void putAscii(byte[] target, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            target[offset + index] = (byte) value.charAt(index);
        }
    }

    private void putLe24(byte[] target, int offset, int value) {
        target[offset] = (byte) value;
        target[offset + 1] = (byte) (value >> 8);
        target[offset + 2] = (byte) (value >> 16);
    }
}
