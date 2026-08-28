package com.classforge.auth.security;

import java.util.UUID;

public record UserPrincipal(UUID id, String email) {
}