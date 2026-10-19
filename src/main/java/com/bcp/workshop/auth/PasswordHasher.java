package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@ApplicationScoped
public class PasswordHasher {
    private static final String FORMAT = "pbkdf2-sha256";
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int MIN_ITERATIONS = 600_000;
    private static final int MAX_ITERATIONS = 2_000_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private final SecureRandom random = new SecureRandom();
    private final int iterations;

    @Inject
    public PasswordHasher(@ConfigProperty(name = "auth.password.iterations",
            defaultValue = "600000") int iterations) {
        validateIterations(iterations);
        this.iterations = iterations;
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return String.join("$", FORMAT, Integer.toString(iterations),
                encode(salt), encode(derive(password, salt, iterations)));
    }

    public boolean matches(String password, String encoded) {
        ParsedHash parsed = parse(encoded);
        return MessageDigest.isEqual(parsed.key(),
                derive(password, parsed.salt(), parsed.iterations()));
    }

    public void validate(String encoded) {
        parse(encoded);
    }

    private ParsedHash parse(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("Password hash is required");
        }
        String[] fields = encoded.split("\\$", -1);
        if (fields.length != 4 || !FORMAT.equals(fields[0])) {
            throw new IllegalArgumentException("Unsupported password hash format");
        }
        try {
            int workFactor = Integer.parseInt(fields[1]);
            validateIterations(workFactor);
            byte[] salt = Base64.getDecoder().decode(fields[2]);
            byte[] key = Base64.getDecoder().decode(fields[3]);
            if (salt.length != SALT_BYTES || key.length != KEY_BYTES) {
                throw new IllegalArgumentException("Invalid password hash dimensions");
            }
            return new ParsedHash(workFactor, salt, key);
        } catch (IllegalArgumentException error) {
            // Do not propagate parser messages that could contain hash material.
            throw new IllegalArgumentException("Invalid password hash encoding");
        }
    }

    private static void validateIterations(int iterations) {
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new IllegalArgumentException("Password work factor is outside the supported range");
        }
    }

    private byte[] derive(String password, byte[] salt, int workFactor) {
        var specification = new PBEKeySpec(password.toCharArray(), salt, workFactor, KEY_BYTES * Byte.SIZE);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(specification).getEncoded();
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Password hashing is unavailable", error);
        } finally {
            specification.clearPassword();
        }
    }

    private String encode(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }

    private record ParsedHash(int iterations, byte[] salt, byte[] key) {}
}
