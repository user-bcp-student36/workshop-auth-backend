package com.bcp.workshop.auth;

import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.Instant;
import java.util.Optional;

@ApplicationScoped
public class AuthTokenService {
    @ConfigProperty(name = "auth.token.secret")
    Optional<String> secret;

    @ConfigProperty(name = "auth.token.ttl-seconds", defaultValue = "3600")
    long ttlSeconds;

    @Inject
    JWTParser parser;

    public String issue(String username, String client) {
        Instant now = Instant.now();
        return Jwt.claims()
                .subject(username)
                .claim("client", client)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .jws()
                .algorithm(SignatureAlgorithm.HS256)
                .signWithSecret(requireSecret());
    }

    public JsonWebToken verify(String token) throws ParseException {
        return parser.verify(token, requireSecret());
    }

    private String requireSecret() {
        return secret.filter(value -> !value.isBlank())
                .orElseThrow(() -> new IllegalStateException("AUTH_TOKEN_SECRET is not configured"));
    }
}
