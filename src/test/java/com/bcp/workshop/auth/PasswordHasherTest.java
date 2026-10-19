package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void generatesSaltedArgon2idAndVerifiesOnlyTheCorrectPassword() {
        long start = System.nanoTime();
        String first = hasher.hash("same-password");
        String second = hasher.hash("same-password");
        System.out.printf("Argon2id local hash average: %.1f ms%n", (System.nanoTime() - start) / 2_000_000.0);
        assertTrue(first.startsWith("$argon2id$v=19$m=19456,t=2,p=1$"));
        assertNotEquals(first, second);
        assertTrue(hasher.verify("same-password", first));
        assertFalse(hasher.verify("wrong-password", first));
    }

    @Test
    void doesNotTruncateLongPasswordsOrNormalizeUnicode() {
        String prefix = "x".repeat(100);
        String hash = hasher.hash(prefix + "\u00e9");
        assertTrue(hasher.verify(prefix + "\u00e9", hash));
        assertFalse(hasher.verify(prefix + "different", hash));
        assertFalse(hasher.verify(prefix + "e\u0301", hash));
    }

    @Test
    void failsClosedForMissingAndCorruptedHashes() {
        assertFalse(hasher.verify("password", null));
        assertFalse(hasher.verify("password", "plaintext"));
        assertFalse(hasher.verify("password", "$argon2id$broken"));
    }
}