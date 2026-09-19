package com.classforge.generation.spring.generated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Validation facade for generated projects. Each artifact family owns its checks. */
@Component
public class GeneratedProjectValidator {
    private final GeneratedTextValidator textValidator = new GeneratedTextValidator();
    private final GeneratedSkeletonValidator skeletonValidator = new GeneratedSkeletonValidator();
    private final GeneratedJavaSourcesValidator javaValidator = new GeneratedJavaSourcesValidator();
    private final GeneratedApiArtifactsValidator apiValidator = new GeneratedApiArtifactsValidator();

    public void validate(GeneratedProject project) {
        List<GeneratedProjectDiagnostic> diagnostics = new ArrayList<>();
        if (project == null || project.artifactName() == null || project.artifactName().isBlank()
                || project.files().isEmpty()) {
            add(diagnostics, GeneratedProjectDiagnosticCode.INVALID_PROJECT, null,
                    "Project artifact and files are required.");
        }
        if (project == null) throw new GeneratedProjectException(diagnostics);

        Set<String> paths = new HashSet<>();
        Set<String> insensitive = new HashSet<>();
        Map<String, GeneratedFile> filesByPath = new HashMap<>();
        Map<String, String> textByPath = new HashMap<>();

        for (GeneratedFile file : project.files()) {
            String path = file.path();
            if (!validPath(path)) {
                add(diagnostics, GeneratedProjectDiagnosticCode.INVALID_PATH, path,
                        "Generated path must be a safe relative POSIX path.");
            } else {
                if (!paths.add(path) || !insensitive.add(path.toLowerCase(Locale.ROOT))) {
                    add(diagnostics, GeneratedProjectDiagnosticCode.DUPLICATE_PATH, path,
                            "Generated path is duplicated.");
                }
                filesByPath.putIfAbsent(path, file);
            }
            if (file.type() == GeneratedFileType.TEXT) {
                String text = textValidator.validateText(file, diagnostics);
                if (text != null && path != null) {
                    textByPath.put(path, text);
                    textValidator.validateTemplateMarkers(path, text, diagnostics);
                }
            }
        }

        skeletonValidator.validateRequiredSkeleton(filesByPath, textByPath, diagnostics);
        javaValidator.validateJavaSources(textByPath, diagnostics);
        apiValidator.validateApiArtifacts(textByPath, diagnostics);
        if (!diagnostics.isEmpty()) throw new GeneratedProjectException(diagnostics);
    }

    private boolean validPath(String path) {
        if (path == null || path.isBlank() || path.startsWith("/")
                || path.matches("^[A-Za-z]:.*") || path.contains("\\")) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }

    private void add(List<GeneratedProjectDiagnostic> diagnostics, GeneratedProjectDiagnosticCode code, String path, String message) {
        diagnostics.add(new GeneratedProjectDiagnostic(code, path, message));
    }
}
