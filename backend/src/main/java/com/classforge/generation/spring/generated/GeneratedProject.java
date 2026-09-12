package com.classforge.generation.spring.generated;

import java.util.Comparator;
import java.util.List;

public record GeneratedProject(String artifactName, List<GeneratedFile> files) {
    public GeneratedProject { files = List.copyOf((files == null ? List.<GeneratedFile>of() : files).stream().sorted(Comparator.comparing(GeneratedFile::path)).toList()); }
}
