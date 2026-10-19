package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AuthService {
    private final LegacyDatabase database;
    private final PasswordHasher passwordHasher;

    @Inject
    public AuthService(LegacyDatabase database, PasswordHasher passwordHasher) {
        this.database = database;
        this.passwordHasher = passwordHasher;
    }

    public UserRecord authenticate(String username, String password) {
        StoredCredentials credentials = database.findByUsername(username);
        if (credentials == null || !passwordHasher.verify(password, credentials.passwordHash())) {
            return null;
        }
        return credentials.user();
    }

    public LoginResponse login(String username, String password) {
        UserRecord user = authenticate(username, password);
        if (user == null) {
            return null;
        }
        return LoginResponse.from(user, LegacyTokenUtil.create(user.username(), user.role()));
    }
}