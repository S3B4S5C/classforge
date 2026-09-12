package com.classforge.generation.spring.generated;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GeneratedProjectValidator {
    public void validate(GeneratedProject project) {
        List<GeneratedProjectDiagnostic> diagnostics = new ArrayList<>();
        if (project == null || project.artifactName() == null || project.artifactName().isBlank() || project.files().isEmpty()) add(diagnostics, GeneratedProjectDiagnosticCode.INVALID_PROJECT, null, "Project artifact and files are required.");
        if (project != null) {
            Set<String> paths = new HashSet<>(), insensitive = new HashSet<>();
            for (GeneratedFile file : project.files()) {
                String path = file.path();
                if (!validPath(path)) add(diagnostics, GeneratedProjectDiagnosticCode.INVALID_PATH, path, "Generated path must be a safe relative POSIX path.");
                else if (!paths.add(path) || !insensitive.add(path.toLowerCase(java.util.Locale.ROOT))) add(diagnostics, GeneratedProjectDiagnosticCode.DUPLICATE_PATH, path, "Generated path is duplicated.");
                if (file.type() == GeneratedFileType.TEXT) validateText(file, diagnostics);
            }
        }
        if (!diagnostics.isEmpty()) throw new GeneratedProjectException(diagnostics);
    }
    private boolean validPath(String path) { if (path == null || path.isBlank() || path.startsWith("/") || path.matches("^[A-Za-z]:.*") || path.contains("\\")) return false; for (String segment : path.split("/", -1)) if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false; return true; }
    private void validateText(GeneratedFile file, List<GeneratedProjectDiagnostic> diagnostics) {
        try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.content())); }
        catch (CharacterCodingException exception) { add(diagnostics, GeneratedProjectDiagnosticCode.INVALID_TEXT_ENCODING, file.path(), "Text content is not valid UTF-8."); return; }
        for (byte value : file.content()) if (value == '\r') { add(diagnostics, GeneratedProjectDiagnosticCode.INVALID_LINE_ENDINGS, file.path(), "Text content must use LF only."); break; }
    }
    private void add(List<GeneratedProjectDiagnostic> ds, GeneratedProjectDiagnosticCode code, String path, String message) { ds.add(new GeneratedProjectDiagnostic(code, path, message)); }
}
