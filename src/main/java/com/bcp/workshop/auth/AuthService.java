package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

@ApplicationScoped
public class AuthService {
    private final UserRepository repository;
    private final PasswordHasher hasher;
    private final TokenService tokens;

    @Inject
    public AuthService(UserRepository repository, PasswordHasher hasher, TokenService tokens) {
        this.repository = repository;
        this.hasher = hasher;
        this.tokens = tokens;
    }

    public Optional<UserRecord> authenticate(String username, String password) {
        return repository.findByUsername(username)
                .filter(stored -> hasher.matches(password, stored.passwordHash()))
                .map(UserRepository.StoredUser::user);
    }

    public String issueToken(UserRecord user) {
        return tokens.create(user.username(), user.role());
    }
}
