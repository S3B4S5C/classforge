package com.classforge.generation.spring.archive;

import com.classforge.generation.spring.generated.GeneratedFile;
import com.classforge.generation.spring.generated.GeneratedProject;
import com.classforge.generation.spring.generated.GeneratedProjectValidator;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.CRC32;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.stereotype.Component;

@Component
public class DeterministicZipWriter {
    private static final long ZIP_EPOCH_MILLIS = 315532800000L;
    private final GeneratedProjectValidator validator;

    public DeterministicZipWriter(GeneratedProjectValidator validator) {
        this.validator = validator;
    }

    public byte[] write(GeneratedProject project) {
        validator.validate(project);
        String root = project.artifactName();
        Set<String> names = new HashSet<>();
        try (
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                ZipArchiveOutputStream zip = new ZipArchiveOutputStream(bytes)
        ) {
            zip.setEncoding(StandardCharsets.UTF_8.name());
            zip.setUseLanguageEncodingFlag(true);
            for (GeneratedFile file : project.files().stream()
                    .sorted(java.util.Comparator.comparing(GeneratedFile::path))
                    .toList()) {
                String name = root + "/" + file.path();
                if (!names.add(name)) {
                    throw new SpringArchiveException("Duplicate archive entry: " + name);
                }
                byte[] content = file.content();
                CRC32 crc = new CRC32();
                crc.update(content);

                ZipArchiveEntry entry = new ZipArchiveEntry(name);
                entry.setMethod(ZipArchiveEntry.STORED);
                entry.setSize(content.length);
                entry.setCompressedSize(content.length);
                entry.setCrc(crc.getValue());
                entry.setTime(ZIP_EPOCH_MILLIS);
                entry.setUnixMode(file.path().equals("gradlew") ? 0755 : 0644);

                zip.putArchiveEntry(entry);
                zip.write(content);
                zip.closeArchiveEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new SpringArchiveException(
                    "Could not write deterministic Spring Boot archive",
                    exception
            );
        }
    }
}
