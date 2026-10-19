package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserResponseTest {
    @Test
    void preservesHtmlEscapingAndDoesNotReturnPersistenceIdentifier() {
        UserResponse response = UserResponse.from(new UserRecord(42, "synthetic-user",
                "<Example & \"Name\">", "STUDENT"));
        assertEquals(new UserResponse("synthetic-user",
                "&lt;Example &amp; &quot;Name&quot;&gt;", "STUDENT"), response);
    }
}
