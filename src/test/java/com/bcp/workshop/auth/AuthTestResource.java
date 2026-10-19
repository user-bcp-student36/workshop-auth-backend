package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.security.SecureRandom;

public class AuthTestResource implements QuarkusTestResourceLifecycleManager {
    private TestDatabase database;

    @Override
    public Map<String, String> start() {
        try {
            database = new TestDatabase();
            byte[] testKey = new byte[32];
            new SecureRandom().nextBytes(testKey);
            return Map.of("auth.db.url", database.url,
                    "quarkus.http.test-port", "0",
                    "auth.db.migrate-plaintext", "false",
                    "auth.password.iterations", "600000",
                    "auth.seed.student-hash", TestDatabase.STUDENT_HASH,
                    "auth.seed.trainer-hash", TestDatabase.TRAINER_HASH,
                    "auth.token.secret", Base64.getEncoder().encodeToString(testKey),
                    "auth.cors.origins", "https://allowed.example");
        } catch (IOException error) {
            throw new IllegalStateException("Unable to create isolated test database", error);
        }
    }

    @Override
    public void stop() {
        if (database == null) {
            return;
        }
        try {
            database.close();
        } catch (IOException error) {
            throw new IllegalStateException("Unable to remove isolated test database", error);
        }
    }
}
