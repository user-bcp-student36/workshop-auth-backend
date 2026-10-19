package com.bcp.workshop.auth;

public class PersistenceFailure extends RuntimeException {
    public PersistenceFailure(String message, Throwable cause) {
        super(message, cause);
    }
}
