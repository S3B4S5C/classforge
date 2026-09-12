package com.classforge.generation.spring.generated;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratedProjectValidatorTests {
    private final GeneratedProjectValidator validator = new GeneratedProjectValidator();
    @Test void acceptsSafeLfProjectAndRejectsUnsafePathsAndDuplicates() {
        assertDoesNotThrow(() -> validator.validate(project(file("build.gradle", "x\n"), file("src/main/java/App.java", "x\n"))));
        for (String path : List.of("/etc/passwd", "../secret", "src/../secret", "C:/secret", "C:\\secret", "src\\main\\App.java", "./build.gradle", "src//App.java")) assertCode(project(file(path, "x\n")), GeneratedProjectDiagnosticCode.INVALID_PATH);
        assertCode(project(file("Cliente.java", "x\n"), file("cliente.java", "x\n")), GeneratedProjectDiagnosticCode.DUPLICATE_PATH);
        assertCode(project(file("x", "bad\r\n")), GeneratedProjectDiagnosticCode.INVALID_LINE_ENDINGS);
    }
    private GeneratedProject project(GeneratedFile... files) { return new GeneratedProject("demo", List.of(files)); }
    private GeneratedFile file(String path, String content) { return new GeneratedFile(path, GeneratedFileType.TEXT, content.getBytes(StandardCharsets.UTF_8)); }
    private void assertCode(GeneratedProject project, GeneratedProjectDiagnosticCode code) { assertTrue(assertThrows(GeneratedProjectException.class, () -> validator.validate(project)).diagnostics().stream().anyMatch(d -> d.code() == code)); }
}
