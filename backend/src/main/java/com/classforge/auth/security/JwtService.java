package com.classforge.auth.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.classforge.auth.persistence.UserEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final String ISSUER = "classforge";

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Duration expiration;

    public JwtService(
            @Value("${classforge.security.jwt.secret}") String secret,
            @Value("${classforge.security.jwt.expiration-minutes:480}") long expirationMinutes
    ) {
        this.algorithm = Algorithm.HMAC256(secret);
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String issue(UserEntity user) {
        Instant now = Instant.now();

        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getId().toString())
                .withClaim("email", user.getEmail())
                .withIssuedAt(Date.from(now))
                .withExpiresAt(Date.from(now.plus(expiration)))
                .sign(algorithm);
    }

    public UserPrincipal verify(String token) {
        DecodedJWT jwt = verifier.verify(token);
        return new UserPrincipal(
                UUID.fromString(jwt.getSubject()),
                jwt.getClaim("email").asString()
        );
    }

    public long expirationSeconds() {
        return expiration.toSeconds();
    }
}