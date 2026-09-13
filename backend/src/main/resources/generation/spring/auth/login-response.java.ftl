package ${model.basePackage}.security;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds
) { }
