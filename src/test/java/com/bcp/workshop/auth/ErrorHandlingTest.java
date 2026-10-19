package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorHandlingTest {
    @Test
    void hidesUnexpectedErrorDetails() {
        assertError(new IllegalStateException("synthetic internal failure"));
    }

    @Test
    void usesTheSameGenericErrorForNullExceptionMessages() {
        assertError(new IllegalStateException());
    }

    @Test
    void persistenceFailureRemainsAnObservableUnauthorizedResponse() {
        var resource = failingResource(new PersistenceFailure("synthetic query failure", null));
        try (var response = resource.login(request())) {
            assertEquals(401, response.getStatus());
            assertEquals(Map.of("message", "Invalid credentials"), response.getEntity());
        }
    }

    @Test
    void successUsesAnExplicitOutputDto() {
        var resource = new AuthResource();
        resource.service = new AuthService(null, null, null) {
            @Override
            public Optional<UserRecord> authenticate(String username, String password) {
                return Optional.of(new UserRecord(1, username, "<Example & Name>", "STUDENT"));
            }

            @Override
            public String issueToken(UserRecord user) {
                return "opaque-synthetic-token";
            }
        };
        try (var response = resource.login(request())) {
            assertEquals(200, response.getStatus());
            assertNotNull(response.getEntity());
            assertEquals("LoginResponse", response.getEntity().getClass().getSimpleName());
        }
    }

    @Test
    void logsFailuresWithoutAddingRequestCredentials() {
        var records = new ArrayList<LogRecord>();
        var logger = Logger.getLogger(AuthResource.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }
            @Override
            public void flush() {}
            @Override
            public void close() {}
        };
        logger.addHandler(handler);
        try {
            var failures = Map.of(
                    new IllegalStateException("synthetic failure"), 500,
                    new PersistenceFailure("synthetic query failure",
                            new java.sql.SQLException("synthetic database failure")), 401);
            for (var entry : failures.entrySet()) {
                records.clear();
                var failure = entry.getKey();
                try (var response = failingResource(failure).login(request())) {
                    assertEquals(entry.getValue().intValue(), response.getStatus());
                }
                assertEquals(1, records.size());
                assertEquals(java.util.logging.Level.SEVERE, records.getFirst().getLevel());
                assertTrue(records.stream().anyMatch(record -> record.getThrown() == failure));
                assertTrue(records.stream().noneMatch(record ->
                        record.getMessage().contains("synthetic-password")
                                || record.getMessage().contains("synthetic-user")));
            }
        } finally {
            logger.removeHandler(handler);
        }
    }

    private void assertError(RuntimeException failure) {
        var resource = failingResource(failure);
        try (var response = resource.login(request())) {
            assertEquals(500, response.getStatus());
            assertEquals(Map.of("message", "Authentication failed"), response.getEntity());
        }
    }

    private AuthResource failingResource(RuntimeException failure) {
        var resource = new AuthResource();
        resource.service = new AuthService(null, null, null) {
            @Override
            public Optional<UserRecord> authenticate(String username, String password) {
                throw failure;
            }
        };
        return resource;
    }

    private LoginRequest request() {
        var request = new LoginRequest();
        request.username = "synthetic-user";
        request.password = "synthetic-password";
        return request;
    }
}
