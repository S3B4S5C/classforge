package com.classforge.project.domain.document;

public record DiagramNodeLayout(
        double x,
        double y,
        double width,
        double height
) {
    public static DiagramNodeLayout defaultForIndex(int index) {
        int column = Math.floorMod(index, 3);
        int row = Math.floorDiv(index, 3);

        return new DiagramNodeLayout(
                80 + (column * 280.0),
                80 + (row * 220.0),
                240,
                160
        );
    }
}