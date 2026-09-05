package com.classforge.assistant.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VisionMultiplicityParserTests {

    private final VisionMultiplicityParser parser = new VisionMultiplicityParser();

    @Test
    void parsesSupportedMultiplicityGrammar() {
        assertNull(parser.parse(null));
        assertNull(parser.parse(""));
        assertMultiplicity("1", 1, 1, false);
        assertMultiplicity("0..1", 0, 1, false);
        assertMultiplicity("*", 0, null, true);
        assertMultiplicity("0..*", 0, null, true);
        assertMultiplicity("1..*", 1, null, true);
        assertMultiplicity("2", 2, 2, false);
        assertMultiplicity("2..5", 2, 5, false);
        assertMultiplicity("3..*", 3, null, true);
        assertMultiplicity(" 2 .. 5 ", 2, 5, false);
    }

    @Test
    void rejectsAmbiguousOrInvalidMultiplicityText() {
        for (String value : new String[]{"foo", "1...", "..*", "5..2", "-1", "1 or *"}) {
            assertThrows(RuntimeException.class, () -> parser.parse(value));
        }
    }

    private void assertMultiplicity(String raw, Integer lower, Integer upper, boolean unbounded) {
        VisionMultiplicityProposal value = parser.parse(raw);
        assertEquals(lower, value.lower());
        assertEquals(upper, value.upper());
        assertEquals(unbounded, value.unbounded());
    }
}
