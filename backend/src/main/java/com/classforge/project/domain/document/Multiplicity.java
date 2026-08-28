package com.classforge.project.domain.document;

public record Multiplicity(
        int lower,
        Integer upper
) {
    public static Multiplicity one() {
        return new Multiplicity(1, 1);
    }

    public static Multiplicity many() {
        return new Multiplicity(0, null);
    }
}