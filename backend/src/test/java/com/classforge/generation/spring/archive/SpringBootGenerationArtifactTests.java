package com.classforge.generation.spring.archive;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SpringBootGenerationArtifactTests {
    @Test void protectsBytesAndUsesContentEquality() {
        byte[] bytes = {1, 2}; SpringBootGenerationArtifact artifact = new SpringBootGenerationArtifact("biblioteca-backend.zip", "application/zip", 7, bytes); bytes[0] = 9;
        assertEquals(1, artifact.content()[0]); byte[] returned = artifact.content(); returned[1] = 9;
        assertEquals(2, artifact.content()[1]); assertEquals(artifact, new SpringBootGenerationArtifact("biblioteca-backend.zip", "application/zip", 7, new byte[] {1, 2})); assertEquals(artifact.hashCode(), new SpringBootGenerationArtifact("biblioteca-backend.zip", "application/zip", 7, new byte[] {1, 2}).hashCode());
    }
}
