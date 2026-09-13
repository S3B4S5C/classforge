package com.classforge.generation.spring.archive;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.classforge.generation.spring.generated.*;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;

class DeterministicZipWriterTests {
    @Test
    void writesStoredUtf8SortedDeterministicArchiveWithoutDirectoryEntries() throws Exception {
        GeneratedProjectValidator validator = mock(GeneratedProjectValidator.class);
        DeterministicZipWriter writer = new DeterministicZipWriter(validator);
        GeneratedProject first = project(List.of(
                file("src/main/App.java", "class App {}"),
                file("README.md", "readme"),
                new GeneratedFile("gradle/wrapper/gradle-wrapper.jar", GeneratedFileType.BINARY, new byte[] {0, 1, 2}),
                file("build.gradle", "plugins {}")
        ));
        GeneratedProject second = project(List.of(
                file("build.gradle", "plugins {}"),
                file("README.md", "readme"),
                file("src/main/App.java", "class App {}"),
                new GeneratedFile("gradle/wrapper/gradle-wrapper.jar", GeneratedFileType.BINARY, new byte[] {0, 1, 2})
        ));

        byte[] a = writer.write(first);
        byte[] b = writer.write(second);
        assertArrayEquals(a, b);
        assertArrayEquals(digest(a), digest(b));
        verify(validator).validate(same(first));
        verify(validator).validate(same(second));

        List<String> names = new ArrayList<>();
        Path archive = Files.createTempFile("classforge-archive", ".zip");
        try {
            Files.write(archive, a);
            try (ZipFile input = ZipFile.builder().setPath(archive).get()) {
                for (ZipArchiveEntry entry : Collections.list(input.getEntries())) {
                    names.add(entry.getName());
                    assertEquals(ZipArchiveEntry.STORED, entry.getMethod());
                    assertEquals(entry.getSize(), entry.getCompressedSize());
                    assertFalse(entry.isDirectory());
                    assertEquals(315532800000L, entry.getTime());
                    assertEquals(entry.getName().equals("biblioteca/gradlew") ? 0755 : 0644, entry.getUnixMode() & 0777);
                }
            }
        } finally {
            Files.deleteIfExists(archive);
        }
        assertEquals(List.of(
                "biblioteca/README.md",
                "biblioteca/build.gradle",
                "biblioteca/gradle/wrapper/gradle-wrapper.jar",
                "biblioteca/src/main/App.java"
        ), names);
        assertArrayEquals(
                new byte[] {0, 1, 2},
                content(a, "biblioteca/gradle/wrapper/gradle-wrapper.jar")
        );
        assertArrayEquals(
                "readme".getBytes(StandardCharsets.UTF_8),
                content(a, "biblioteca/README.md")
        );
    }

    @Test
    void rejectsUnsafeProjectThroughTheRealValidator() {
        DeterministicZipWriter writer = new DeterministicZipWriter(new GeneratedProjectValidator());
        GeneratedProjectException exception = assertThrows(
                GeneratedProjectException.class,
                () -> writer.write(project(List.of(file("../evil", "x"))))
        );
        assertTrue(exception.diagnostics().stream().anyMatch(
                diagnostic -> diagnostic.code() == GeneratedProjectDiagnosticCode.INVALID_PATH
        ));
    }

    private GeneratedProject project(List<GeneratedFile> files) {
        return new GeneratedProject("biblioteca", files);
    }

    private GeneratedFile file(String path, String text) {
        return new GeneratedFile(path, GeneratedFileType.TEXT, text.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] content(byte[] zip, String name) throws Exception {
        try (ZipArchiveInputStream input = new ZipArchiveInputStream(
                new ByteArrayInputStream(zip), StandardCharsets.UTF_8.name(), true, true
        )) {
            for (ZipArchiveEntry entry; (entry = input.getNextZipEntry()) != null;) {
                if (entry.getName().equals(name)) {
                    return input.readAllBytes();
                }
            }
        }
        throw new AssertionError(name);
    }

    private byte[] digest(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
}
