package ${model.basePackage}.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import java.time.Instant;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final Algorithm algorithm;
    private final long expirationSeconds;

    public JwtService(
            @Value("${r'${app.security.jwt-secret:classforge-change-me-before-production}'}") String secret,
            @Value("${r'${app.security.jwt-expiration-seconds:3600}'}") long expirationSeconds
    ) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.jwt-secret is required");
        }
        this.algorithm = Algorithm.HMAC256(secret);
        this.expirationSeconds = expirationSeconds;
    }

    public LoginResponse issue(String username) {
        Instant now = Instant.now();
        String token = JWT.create()
                .withSubject(username)
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plusSeconds(expirationSeconds)))
                .sign(algorithm);
        return new LoginResponse(token, "Bearer", expirationSeconds);
    }

    public String verify(String token) {
        try {
            return JWT.require(algorithm).build().verify(token).getSubject();
        } catch (JWTVerificationException exception) {
            return null;
        }
    }
}
