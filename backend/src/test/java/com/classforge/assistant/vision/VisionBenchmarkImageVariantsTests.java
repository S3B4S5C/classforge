package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class VisionBenchmarkImageVariantsTests {

    @Test
    void originalKeepsTheExactImageObject() {
        BufferedImage image = new BufferedImage(899, 1599, BufferedImage.TYPE_INT_RGB);

        List<BufferedImage> variants = VisionBenchmarkImageVariants.variants(
                image,
                VisionBenchmarkImageVariants.Strategy.ORIGINAL
        );

        assertEquals(1, variants.size());
        assertSame(image, variants.getFirst());
    }

    @Test
    void boardCropRotatesPortraitWhiteboardAndRemovesOuterMargins() {
        BufferedImage image = new BufferedImage(899, 1599, BufferedImage.TYPE_INT_RGB);

        BufferedImage board = VisionBenchmarkImageVariants.variants(
                image,
                VisionBenchmarkImageVariants.Strategy.BOARD_CROP
        ).getFirst();

        assertEquals(1479, board.getWidth());
        assertEquals(789, board.getHeight());
    }

    @Test
    void tilesProduceFourOverlappingUpscaledRegions() {
        BufferedImage image = new BufferedImage(899, 1599, BufferedImage.TYPE_INT_RGB);

        List<BufferedImage> tiles = VisionBenchmarkImageVariants.variants(
                image,
                VisionBenchmarkImageVariants.Strategy.TILES
        );

        assertEquals(4, tiles.size());
        assertEquals(1509, tiles.getFirst().getWidth());
        assertEquals(852, tiles.getFirst().getHeight());
    }
}
