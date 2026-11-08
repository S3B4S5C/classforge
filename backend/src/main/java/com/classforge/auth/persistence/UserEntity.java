package com.classforge.auth.persistence;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.UUID;

@Document("app_users")
public class UserEntity {

    @Id
    private UUID id;

    private String displayName;

    private String email;

    private String passwordHash;

    private Instant createdAt;

    protected UserEntity() {
    }

    public UserEntity(UUID id, String displayName, String email, String passwordHash, Instant createdAt) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}