package com.classforge.project.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Project(
        UUID id,
        UUID ownerId,
        String name,
        long revision,
        UmlModelSnapshot umlModel,
        Instant createdAt,
        Instant updatedAt
) {

    public Project {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(ownerId, "ownerId is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(umlModel, "umlModel is required");
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
        String normalizedName = normalizeName(name);
        Instant now = Instant.now();

        return new Project(
                UUID.randomUUID(),
                ownerId,
                normalizedName,
                0L,
                UmlModelSnapshot.empty(),
                now,
                now
        );
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Project name cannot be blank");
        }
        return name.trim();
    }
}