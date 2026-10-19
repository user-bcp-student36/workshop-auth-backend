package com.bcp.workshop.auth;

public record LoginResponse(String token, int expiresIn, UserResponse user, String debug) {
    private static final String DEBUG_FLOW = "authenticated-with-legacy-flow";

    public static LoginResponse from(UserRecord user, String token) {
        return new LoginResponse(token, TokenPolicy.LIFETIME_SECONDS, UserResponse.from(user), DEBUG_FLOW);
    }
}