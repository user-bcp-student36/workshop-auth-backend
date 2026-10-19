package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher(600_000);

    @Test
    void saltsHashesAndVerifiesWithoutTruncatingLongPasswords() {
        String password = "long-\u00e9".repeat(100);
        String encoded = hasher.hash(password);
        assertNotEquals(encoded, hasher.hash(password));
        assertTrue(hasher.matches(password, encoded));
        assertFalse(hasher.matches(password + "x", encoded));
        assertFalse(encoded.contains(password));
    }

    @Test
    void rejectsInvalidHashAndUnsafeWorkFactorsExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> hasher.matches("test", "invalid"));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(1));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(2_000_001));
        assertThrows(IllegalArgumentException.class, () -> hasher.validate(null));
        assertThrows(IllegalArgumentException.class, () -> hasher.validate("pbkdf2-sha256$low$?$?"));
        assertThrows(IllegalArgumentException.class, () -> hasher.validate("pbkdf2-sha256$1$?$?"));
        assertThrows(IllegalArgumentException.class, () -> hasher.validate("pbkdf2-sha256$600000$?$?"));
        assertThrows(IllegalArgumentException.class, () -> hasher.validate("pbkdf2-sha256$600000$AA==$AA=="));
    }
}
