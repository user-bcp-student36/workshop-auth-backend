package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    static final String TEST_KEY = Base64.getEncoder().encodeToString(
            "synthetic-test-key-not-for-prod-0123456789".getBytes(StandardCharsets.UTF_8));
    static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void signsClaimsAndExpiresExactlyAtTheBoundary() {
        var service = service(NOW);
        String token = service.create("synthetic-user", "STUDENT");
        var claims = service.verify(token);
        assertEquals("synthetic-user", claims.getSubject());
        assertEquals("STUDENT", claims.getClaim("role"));
        assertEquals(NOW, claims.getIssueTime().toInstant());
        assertEquals(NOW.plusSeconds(3600), claims.getExpirationTime().toInstant());
        assertDoesNotThrow(() -> service(NOW.plusSeconds(3599)).verify(token));
        assertThrows(IllegalArgumentException.class,
                () -> service(NOW.plusSeconds(3600)).verify(token));
    }

    @Test
    void rejectsAlterationWrongKeyAndInvalidEncoding() {
        var service = service(NOW);
        String token = service.create("synthetic-user", "STUDENT");
        String[] parts = token.split("\\.");
        String modified = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"other\"}".getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        assertThrows(IllegalArgumentException.class, () -> service.verify(modified));
        String otherKey = Base64.getEncoder().encodeToString(new byte[32]);
        assertThrows(IllegalArgumentException.class,
                () -> new TokenService(otherKey, Clock.fixed(NOW, ZoneOffset.UTC)).verify(token));
        assertThrows(IllegalArgumentException.class, () -> service.verify("invalid"));
        assertThrows(IllegalArgumentException.class, () -> new TokenService("not-base64"));
        assertThrows(IllegalArgumentException.class, () -> new TokenService("dGVzdA=="));
    }

    static TokenService service(Instant now) {
        return new TokenService(TEST_KEY, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void rejectsUnsupportedAlgorithmAndInvalidRequiredClaims() throws Exception {
        var valid = new JWTClaimsSet.Builder().subject("synthetic-user").claim("role", "STUDENT")
                .issueTime(Date.from(NOW)).expirationTime(Date.from(NOW.plusSeconds(3600))).build();
        var wrongAlgorithm = new SignedJWT(new JWSHeader(JWSAlgorithm.HS384), valid);
        wrongAlgorithm.sign(new MACSigner(new byte[64]));
        assertThrows(IllegalArgumentException.class, () -> service(NOW).verify(wrongAlgorithm.serialize()));
        var invalidClaims = List.of(
                new JWTClaimsSet.Builder(valid).subject(null).build(),
                new JWTClaimsSet.Builder(valid).subject(" ").build(),
                new JWTClaimsSet.Builder(valid).claim("role", null).build(),
                new JWTClaimsSet.Builder(valid).claim("role", " ").build(),
                new JWTClaimsSet.Builder(valid).claim("role", 42).build(),
                new JWTClaimsSet.Builder(valid).issueTime(null).build(),
                new JWTClaimsSet.Builder(valid).expirationTime(null).build(),
                new JWTClaimsSet.Builder(valid).issueTime(Date.from(NOW.plusSeconds(1))).build(),
                new JWTClaimsSet.Builder(valid).expirationTime(Date.from(NOW.minusSeconds(1))).build());
        for (JWTClaimsSet claims : invalidClaims) {
            var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signed.sign(new MACSigner(Base64.getDecoder().decode(TEST_KEY)));
            assertThrows(IllegalArgumentException.class, () -> service(NOW).verify(signed.serialize()));
        }
    }
}
