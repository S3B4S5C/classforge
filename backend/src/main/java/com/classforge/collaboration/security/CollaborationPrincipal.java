package com.classforge.collaboration.security;

import java.security.Principal;
import java.util.UUID;

public record CollaborationPrincipal(
        UUID id,
        String email
) implements Principal {

    @Override
    public String getName() {
        return id.toString();
    }
}