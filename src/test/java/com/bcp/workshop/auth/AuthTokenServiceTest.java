package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.algorithm.SignatureAlgorithm;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class AuthTokenServiceTest {
    private static final long TTL_SECONDS = 3600;

    @Inject
    AuthTokenService tokens;

    @ConfigProperty(name = "auth.token.secret")
    String secret;

    // Protege: spec auth-token "Token firmado con claims minimos".
    @Test
    void issuedTokenCarriesSubjectClientIatAndExp() throws ParseException {
        String token = tokens.issue("student", "angular-workshop");
        JsonWebToken parsed = tokens.verify(token);

        assertEquals("student", parsed.getSubject());
        assertEquals("angular-workshop", parsed.getClaim("client"));
        assertTrue(parsed.getIssuedAtTime() > 0);
        assertTrue(parsed.getExpirationTime() > parsed.getIssuedAtTime());
    }

    // Protege: spec auth-token "Duracion de token unica" (exp - iat == ttl).
    @Test
    void expMinusIatEqualsConfiguredTtl() throws ParseException {
        JsonWebToken parsed = tokens.verify(tokens.issue("student", "angular-workshop"));

        assertEquals(TTL_SECONDS, parsed.getExpirationTime() - parsed.getIssuedAtTime());
    }

    // Protege: spec auth-token "Rechazo de tokens con firma invalida" (contenido alterado).
    @Test
    void tokenWithAlteredPayloadIsRejected() {
        String[] parts = tokens.issue("student", "angular-workshop").split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"admin\",\"client\":\"angular-workshop\"}".getBytes(StandardCharsets.UTF_8));
        String altered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThrows(ParseException.class, () -> tokens.verify(altered));
    }

    // Protege: spec auth-token "Rechazo de tokens con firma invalida" (otro secreto).
    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        String foreign = Jwt.claims()
                .subject("student")
                .expiresAt(Instant.now().plusSeconds(TTL_SECONDS))
                .jws()
                .algorithm(SignatureAlgorithm.HS256)
                .signWithSecret("otro-secreto-distinto-para-prueba-0123456789abcdef");

        assertThrows(ParseException.class, () -> tokens.verify(foreign));
    }

    // Protege: spec auth-token "Expiracion verificable" (token expirado).
    @Test
    void expiredTokenIsRejected() {
        String expired = Jwt.claims()
                .subject("student")
                .issuedAt(Instant.now().minusSeconds(7200))
                .expiresAt(Instant.now().minusSeconds(3600))
                .jws()
                .algorithm(SignatureAlgorithm.HS256)
                .signWithSecret(secret);

        assertThrows(ParseException.class, () -> tokens.verify(expired));
    }

    // Protege: spec auth-token "Token en formato anterior es rechazado".
    @Test
    void legacyBase64TokenIsRejected() {
        String legacy = Base64.getEncoder().encodeToString(
                ("student|STUDENT|" + (Instant.now().getEpochSecond() + 3600) + "|" + "legacy-secret")
                        .getBytes(StandardCharsets.UTF_8));

        assertThrows(ParseException.class, () -> tokens.verify(legacy));
    }

    // Protege: spec auth-token "Secreto configurado por entorno" (secreto presente permite emitir).
    @Test
    void configuredSecretIsNotEmptyAndIssuesToken() {
        assertFalse(secret.isBlank());
        assertTrue(tokens.issue("student", "angular-workshop").split("\\.").length == 3);
    }
}
