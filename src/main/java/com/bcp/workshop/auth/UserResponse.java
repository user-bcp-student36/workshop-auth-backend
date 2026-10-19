package com.bcp.workshop.auth;

import org.apache.commons.text.StringEscapeUtils;

public record UserResponse(String username, String displayName, String role) {
    public static UserResponse from(UserRecord user) {
        return new UserResponse(user.username(), StringEscapeUtils.escapeHtml4(user.displayName()), user.role());
    }
}