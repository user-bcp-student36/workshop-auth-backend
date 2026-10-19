package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    @Test
    void authenticatesAndIssuesOnlyTheUsersIdentityAndRole() throws Exception {
        try (var database = new TestDatabase()) {
            var repository = database.repository(false);
            repository.initialize();
            var tokens = TokenServiceTest.service(TokenServiceTest.NOW);
            var service = new AuthService(repository, TestDatabase.HASHER, tokens);
            var user = service.authenticate("student", "Workshop123!").orElseThrow();
            var claims = tokens.verify(service.issueToken(user));
            assertEquals("student", claims.getSubject());
            assertEquals("STUDENT", claims.getClaim("role"));
            assertNull(claims.getClaim("password"));
            assertNull(claims.getClaim("passwordHash"));
            assertTrue(service.authenticate("student", "wrong").isEmpty());
            assertTrue(service.authenticate("missing", "wrong").isEmpty());
        }
    }
}
