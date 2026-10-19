package com.bcp.workshop.auth;

public final class StoredCredentials {
    private final UserRecord user;
    private final String passwordHash;

    public StoredCredentials(UserRecord user, String passwordHash) {
        this.user = user;
        this.passwordHash = passwordHash;
    }

    public UserRecord user() {
        return user;
    }

    public String passwordHash() {
        return passwordHash;
    }
}