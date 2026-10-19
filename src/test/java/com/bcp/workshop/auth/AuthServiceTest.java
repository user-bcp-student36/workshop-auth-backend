package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthServiceTest {
    private final PasswordHasher hasher = new PasswordHasher();
    private final UserRecord user = new UserRecord(42, "test-user", "<Test>", "STUDENT");

    @Test
    void authenticatesAndMapsOnlyPublicFields() {
        AuthService service = service(new StoredCredentials(user, hasher.hash("password")));
        assertEquals(user, service.authenticate("test-user", "password"));
        LoginResponse response = service.login("test-user", "password");
        assertEquals(new UserResponse("test-user", "&lt;Test&gt;", "STUDENT"), response.user());
        assertEquals(TokenPolicy.LIFETIME_SECONDS, response.expiresIn());
        assertNotNull(response.token());
        assertEquals("authenticated-with-legacy-flow", response.debug());
    }

    @Test
    void rejectsMissingUserWrongPasswordAndCorruptHash() {
        assertNull(service(null).login("test-user", "password"));
        assertNull(service(new StoredCredentials(user, hasher.hash("password"))).login("test-user", "wrong"));
        assertNull(service(new StoredCredentials(user, "corrupted")).login("test-user", "password"));
    }

    private AuthService service(StoredCredentials credentials) {
        LegacyDatabase repository = new LegacyDatabase() {
            @Override
            public StoredCredentials findByUsername(String username) {
                assertEquals("test-user", username);
                return credentials;
            }
        };
        return new AuthService(repository, hasher);
    }
}