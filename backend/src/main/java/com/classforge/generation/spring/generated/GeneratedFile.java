package com.classforge.generation.spring.generated;

import java.util.Arrays;
import java.util.Objects;

public final class GeneratedFile {
    private final String path;
    private final GeneratedFileType type;
    private final byte[] content;
    public GeneratedFile(String path, GeneratedFileType type, byte[] content) { this.path = path; this.type = Objects.requireNonNull(type); this.content = Arrays.copyOf(Objects.requireNonNull(content), content.length); }
    public String path() { return path; }
    public GeneratedFileType type() { return type; }
    public byte[] content() { return Arrays.copyOf(content, content.length); }
    @Override public boolean equals(Object other) { return other instanceof GeneratedFile file && Objects.equals(path, file.path) && type == file.type && Arrays.equals(content, file.content); }
    @Override public int hashCode() { return Objects.hash(path, type, Arrays.hashCode(content)); }
}
