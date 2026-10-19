package com.bcp.workshop.auth;

import io.smallrye.config.SmallRyeConfigBuilder;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenServiceTest {
    private static final String ISSUER = "https://auth.test.invalid";
    private static final long ISSUED_AT = 1800000000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(ISSUED_AT), ZoneOffset.UTC);
    private static KeyPair keys;
    private static KeyPair otherKeys;
    Path temporaryDirectory;

    @BeforeEach
    void isolateTestKeyLocation() throws Exception {
        temporaryDirectory = Files.createTempDirectory(Files.createDirectories(Path.of("target/test-databases")), "test-keys-");
    }

    @BeforeAll
    static void generateIsolatedKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keys = generator.generateKeyPair();
        otherKeys = generator.generateKeyPair();
    }

    @Test
    void signsClaimsWithOneHourLifetimeAndNoSecrets() throws Exception {
        String token = new TokenService(keys.getPrivate(), ISSUER, CLOCK).create("student", "STUDENT");
        JwtClaims claims = verifier(keys, ISSUED_AT).processToClaims(token);
        assertEquals("student", claims.getSubject());
        assertEquals(List.of("STUDENT"), claims.getStringListClaimValue("groups"));
        assertEquals(ISSUER, claims.getIssuer());
        assertEquals(ISSUED_AT, claims.getIssuedAt().getValue());
        assertEquals(TokenPolicy.LIFETIME_SECONDS, claims.getExpirationTime().getValue() - claims.getIssuedAt().getValue());
        assertFalse(claims.getClaimsMap().containsKey("password"));
        assertFalse(claims.getClaimsMap().containsKey("password_hash"));
        assertFalse(claims.toJson().contains("WORKSHOP_SECRET_2026"));
    }

    @Test
    void rejectsTamperingWrongKeyExpiryAndUnexpectedAlgorithm() throws Exception {
        String token = new TokenService(keys.getPrivate(), ISSUER, CLOCK).create("student", "STUDENT");
        String[] parts = token.split("\\.");
        String changedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"sub\":\"trainer\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidJwtException.class, () -> verifier(keys, ISSUED_AT)
                .processToClaims(parts[0] + "." + changedPayload + "." + parts[2]));
        assertThrows(InvalidJwtException.class, () -> verifier(otherKeys, ISSUED_AT).processToClaims(token));
        assertThrows(InvalidJwtException.class, () -> verifier(keys, ISSUED_AT + TokenPolicy.LIFETIME_SECONDS + 1).processToClaims(token));
        String noneHeader = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        assertThrows(InvalidJwtException.class, () -> verifier(keys, ISSUED_AT).processToClaims(noneHeader + "." + parts[1] + "."));
        String wrongIssuer = new TokenService(keys.getPrivate(), "https://wrong.test.invalid", CLOCK).create("student", "STUDENT");
        assertThrows(InvalidJwtException.class, () -> verifier(keys, ISSUED_AT).processToClaims(wrongIssuer));
    }

    @Test
    void loadsExternalTestKeyAndRejectsMissingOrInvalidConfiguration() throws Exception {
        Path keyFile = temporaryDirectory.resolve("test-only.pem");
        String encoded = Base64.getMimeEncoder().encodeToString(keys.getPrivate().getEncoded());
        Files.writeString(keyFile, "-----BEGIN PRIVATE KEY-----\n" + encoded + "\n-----END PRIVATE KEY-----\n");
        var config = new SmallRyeConfigBuilder().withDefaultValue("auth.token.key-location", keyFile.toUri().toString())
                .withDefaultValue("auth.token.issuer", ISSUER).build();
        String token = TokenService.fromConfiguration(config, CLOCK).create("student", "STUDENT");
        assertEquals("student", verifier(keys, ISSUED_AT).processToClaims(token).getSubject());
        assertThrows(IllegalStateException.class, () -> TokenService.fromConfiguration(new SmallRyeConfigBuilder().build(), CLOCK));
        Files.writeString(keyFile, "invalid-test-only-key");
        IllegalStateException error = assertThrows(IllegalStateException.class, () -> TokenService.fromConfiguration(config, CLOCK));
        assertEquals("JWT signing configuration invalid", error.getMessage());
        assertNull(error.getCause());
        assertThrows(IllegalStateException.class, () -> new TokenService(null, ISSUER, CLOCK));
        assertThrows(IllegalStateException.class, () -> new TokenService(keys.getPrivate(), null, CLOCK));
        assertThrows(IllegalStateException.class, () -> new TokenService(keys.getPrivate(), " ", CLOCK));
        assertThrows(IllegalStateException.class, () -> new TokenService(keys.getPrivate(), ISSUER, null));
        KeyPairGenerator weakGenerator = KeyPairGenerator.getInstance("RSA");
        weakGenerator.initialize(1024);
        assertThrows(IllegalStateException.class, () -> new TokenService(weakGenerator.generateKeyPair().getPrivate(), ISSUER, CLOCK));
    }

    private JwtConsumer verifier(KeyPair verificationKeys, long now) {
        return new JwtConsumerBuilder().setVerificationKey(verificationKeys.getPublic())
                .setExpectedIssuer(ISSUER).setSkipDefaultAudienceValidation().setRequireSubject()
                .setRequireExpirationTime().setEvaluationTime(NumericDate.fromSeconds(now))
                .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, AlgorithmIdentifiers.RSA_USING_SHA256)
                .build();
    }
}