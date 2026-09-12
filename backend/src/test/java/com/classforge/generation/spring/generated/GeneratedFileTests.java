package com.classforge.generation.spring.generated;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class GeneratedFileTests {
    @Test void copiesContentAndComparesBytesByValue() {
        byte[] input = {1, 2}; GeneratedFile file = new GeneratedFile("a", GeneratedFileType.BINARY, input); input[0] = 9;
        assertArrayEquals(new byte[] {1, 2}, file.content()); byte[] exposed = file.content(); exposed[1] = 8;
        assertArrayEquals(new byte[] {1, 2}, file.content()); assertEquals(file, new GeneratedFile("a", GeneratedFileType.BINARY, new byte[] {1, 2})); assertEquals(file.hashCode(), new GeneratedFile("a", GeneratedFileType.BINARY, new byte[] {1, 2}).hashCode());
    }
}
