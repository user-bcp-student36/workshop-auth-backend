package com.bcp.workshop.auth;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
@TestProfile(MissingSecretProfile.class)
class AuthTokenMissingSecretTest {
    @Inject
    AuthTokenService tokens;

    // Protege: spec auth-token "Sin variable de entorno no se emiten tokens" (tarea 4.3).
    @Test
    void issueFailsWhenSecretIsEmpty() {
        assertThrows(IllegalStateException.class, () -> tokens.issue("student", "angular-workshop"));
    }
}
