package com.bcp.workshop.auth;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(AuthFailureHttpTest.FailureProfile.class)
@QuarkusTestResource(AuthTestResource.class)
class AuthFailureHttpTest {
    @Test
    void persistenceFailurePreservesTheGenericUnauthorizedContract() {
        assertError("persistence-failure", 401, "Invalid credentials");
    }

    @Test
    void unexpectedAuthenticationFailureReturnsOnlyTheGenericMessage() {
        assertError("authentication-failure", 500, "Authentication failed");
    }

    @Test
    void tokenIssuanceFailureReturnsOnlyTheGenericMessage() {
        assertError("token-failure", 500, "Authentication failed");
    }

    private void assertError(String username, int status, String message) {
        given().contentType("application/json")
                .body(Map.of("username", username, "password", "synthetic-password"))
                .when().post("/auth/login").then()
                .statusCode(status).contentType("application/json")
                .body("$", equalTo(Map.of("message", message)));
    }

    public static class FailureProfile implements QuarkusTestProfile {
        @Override
        public Set<Class<?>> getEnabledAlternatives() {
            return Set.of(FailingAuthService.class);
        }
    }

    @Alternative
    @ApplicationScoped
    public static class FailingAuthService extends AuthService {
        public FailingAuthService() {
            super(null, null, null);
        }

        @Override
        public Optional<UserRecord> authenticate(String username, String password) {
            return switch (username) {
                case "persistence-failure" ->
                        throw new PersistenceFailure("synthetic query failure", null);
                case "token-failure" ->
                        Optional.of(new UserRecord(1, username, "Synthetic User", "STUDENT"));
                default -> throw new IllegalStateException("synthetic authentication failure");
            };
        }

        @Override
        public String issueToken(UserRecord user) {
            throw new IllegalStateException("synthetic token issuance failure");
        }
    }
}
