package com.bcp.workshop.auth;

import com.password4j.Argon2Function;
import com.password4j.Password;
import com.password4j.types.Argon2;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PasswordHasher {
    private static final int MEMORY_KIB = 19456;
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;
    private static final int HASH_BYTES = 32;
    private static final int SALT_BYTES = 16;
    private static final String HASH_PREFIX = "$argon2id$";
    private static final Argon2Function FUNCTION = Argon2Function.getInstance(
            MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_BYTES, Argon2.ID);

    public String hash(String password) {
        return Password.hash(password).addRandomSalt(SALT_BYTES).with(FUNCTION).getResult();
    }

    public boolean verify(String password, String encodedHash) {
        if (encodedHash == null || !encodedHash.startsWith(HASH_PREFIX)) {
            return false;
        }
        try {
            return Password.check(password, encodedHash).with(Argon2Function.getInstanceFromHash(encodedHash));
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}