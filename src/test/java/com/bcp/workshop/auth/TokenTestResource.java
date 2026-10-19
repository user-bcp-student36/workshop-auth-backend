package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;

public class TokenTestResource implements QuarkusTestResourceLifecycleManager {
    static final String ISSUER = "https://auth.test.invalid";
    static final String AUDIENCE = "requirements-test";
    static final KeyPair KEYS = generateKeys();
    private Path directory;

    private static KeyPair generateKeys() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new IllegalStateException("Test signing key generation failed", error);
        }
    }

    static TokenService service() {
        return new TokenService(KEYS.getPrivate(), ISSUER, AUDIENCE, Clock.systemUTC());
    }

    static JwtClaims verify(String token) throws Exception {
        return new JwtConsumerBuilder().setVerificationKey(KEYS.getPublic())
                .setExpectedIssuer(ISSUER).setExpectedAudience(AUDIENCE)
                .setRequireSubject().setRequireExpirationTime()
                .setJwsAlgorithmConstraints(AlgorithmConstraints.ConstraintType.PERMIT, AlgorithmIdentifiers.RSA_USING_SHA256)
                .build().processToClaims(token);
    }

    @Override
    public Map<String, String> start() {
        try {
            directory = Files.createTempDirectory("reqflow-signing-test-");
            Path key = directory.resolve("private.pem");
            Files.writeString(key, "-----BEGIN PRIVATE KEY-----\n"
                    + Base64.getMimeEncoder().encodeToString(KEYS.getPrivate().getEncoded())
                    + "\n-----END PRIVATE KEY-----\n");
            return Map.of("auth.token.key-location", key.toUri().toString(),
                    "auth.token.issuer", ISSUER, "auth.token.audience", AUDIENCE);
        } catch (Exception error) {
            throw new IllegalStateException("Test signing configuration failed", error);
        }
    }

    @Override
    public void stop() {
        try {
            if (directory != null) {
                Files.deleteIfExists(directory.resolve("private.pem"));
                Files.deleteIfExists(directory);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Test signing cleanup failed", error);
        }
    }
}