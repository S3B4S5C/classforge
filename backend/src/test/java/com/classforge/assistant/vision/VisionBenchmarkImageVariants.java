package com.classforge.assistant.vision;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class VisionBenchmarkImageVariants {

    enum Strategy {
        ORIGINAL("original"),
        BOARD_CROP("board-crop"),
        TILES("tiles");

        private final String wireName;

        Strategy(String wireName) {
            this.wireName = wireName;
        }

        String wireName() {
            return wireName;
        }

        static Strategy parse(String value) {
            String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            for (Strategy strategy : values()) {
                if (strategy.wireName.equals(normalized)) {
                    return strategy;
                }
            }
            throw new IllegalArgumentException("Unknown Vision benchmark image strategy: " + value);
        }
    }

    private VisionBenchmarkImageVariants() {
    }

    static List<BufferedImage> variants(BufferedImage source, Strategy strategy) {
        if (source == null) {
            throw new IllegalArgumentException("source image is required");
        }
        if (strategy == Strategy.ORIGINAL) {
            return List.of(source);
        }

        BufferedImage board = uprightBoardCrop(source);
        if (strategy == Strategy.BOARD_CROP) {
            return List.of(board);
        }

        return overlappingTiles(board);
    }

    static BufferedImage uprightBoardCrop(BufferedImage source) {
        BufferedImage upright = source.getHeight() > source.getWidth()
                ? rotateCounterClockwise(source)
                : source;

        int left = clamp((int) Math.round(upright.getWidth() * 0.075), 0, upright.getWidth() - 2);
        int top = clamp((int) Math.round(upright.getHeight() * 0.022), 0, upright.getHeight() - 2);
        int right = upright.getWidth();
        int bottom = clamp((int) Math.round(upright.getHeight() * 0.900), top + 1, upright.getHeight());

        return copy(upright.getSubimage(left, top, right - left, bottom - top));
    }

    static List<BufferedImage> overlappingTiles(BufferedImage board) {
        int tileWidth = Math.max(1, (int) Math.round(board.getWidth() * 0.68));
        int tileHeight = Math.max(1, (int) Math.round(board.getHeight() * 0.72));
        int rightX = Math.max(0, board.getWidth() - tileWidth);
        int bottomY = Math.max(0, board.getHeight() - tileHeight);

        int[][] origins = {
                {0, 0},
                {rightX, 0},
                {0, bottomY},
                {rightX, bottomY}
        };

        List<BufferedImage> result = new ArrayList<>();
        for (int[] origin : origins) {
            BufferedImage tile = copy(board.getSubimage(
                    origin[0],
                    origin[1],
                    tileWidth,
                    tileHeight
            ));
            result.add(scale(tile, 1.5));
        }
        return List.copyOf(result);
    }

    private static BufferedImage rotateCounterClockwise(BufferedImage source) {
        BufferedImage rotated = new BufferedImage(
                source.getHeight(),
                source.getWidth(),
                BufferedImage.TYPE_INT_RGB
        );
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                rotated.setRGB(y, source.getWidth() - 1 - x, source.getRGB(x, y));
            }
        }
        return rotated;
    }

    private static BufferedImage scale(BufferedImage source, double factor) {
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC
            );
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY
            );
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage target = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
