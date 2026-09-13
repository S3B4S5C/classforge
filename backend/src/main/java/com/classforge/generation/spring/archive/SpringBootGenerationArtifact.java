package com.classforge.generation.spring.archive;

import java.util.Arrays;
import java.util.Objects;

public final class SpringBootGenerationArtifact {
    private final String fileName;
    private final String mediaType;
    private final long projectRevision;
    private final byte[] content;

    public SpringBootGenerationArtifact(String fileName, String mediaType, long projectRevision, byte[] content) {
        this.fileName = Objects.requireNonNull(fileName);
        this.mediaType = Objects.requireNonNull(mediaType);
        this.projectRevision = projectRevision;
        this.content = Arrays.copyOf(Objects.requireNonNull(content), content.length);
    }

    public String fileName() { return fileName; }
    public String mediaType() { return mediaType; }
    public long projectRevision() { return projectRevision; }
    public byte[] content() { return Arrays.copyOf(content, content.length); }
    @Override public boolean equals(Object other) { return other instanceof SpringBootGenerationArtifact artifact && projectRevision == artifact.projectRevision && fileName.equals(artifact.fileName) && mediaType.equals(artifact.mediaType) && Arrays.equals(content, artifact.content); }
    @Override public int hashCode() { return Objects.hash(fileName, mediaType, projectRevision, Arrays.hashCode(content)); }
}
