package com.classforge.project.domain;

import com.classforge.project.domain.document.ProjectDocument;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Project(
        UUID id,
        UUID ownerId,
        String name,
        long revision,
        ProjectDocument document,
        Instant createdAt,
        Instant updatedAt
) {

    public Project {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ownerId, "ownerId is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(document, "document is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        if (name.isBlank()) {
            throw new IllegalArgumentException("Project name cannot be blank");
        }

        if (revision < 0) {
            throw new IllegalArgumentException("Project revision cannot be negative");
        }
    }

    public static Project create(UUID ownerId, String name) {
        Instant now = Instant.now();

        return new Project(
                UUID.randomUUID(),
                ownerId,
                normalizeName(name),
                0L,
                ProjectDocument.empty(),
                now,
                now
        );
    }

    public Project rename(String newName) {
        return new Project(
                id,
                ownerId,
                normalizeName(newName),
                revision,
                document,
                createdAt,
                Instant.now()
        );
    }

    public Project saveDocument(ProjectDocument newDocument) {
        return new Project(
                id,
                ownerId,
                name,
                revision + 1,
                Objects.requireNonNull(newDocument, "document is required"),
                createdAt,
                Instant.now()
        );
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name cannot be blank");
        }

        return name.trim();
    }
}