package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigurationTest {
    @Test
    void acceptsOnlyExplicitHttpOrigins() {
        assertDoesNotThrow(() -> new CorsConfiguration(
                "https://allowed.example,http://localhost:4200"));
        for (String invalid : new String[]{"", "*", "/.*/", "null", "file:example",
                "https://example.test/path", "https://example.test?query",
                "https://example.test#fragment", "https://user@example.test",
                "https://example.test:99999", "https://example.test,", "http://["}) {
            assertThrows(IllegalArgumentException.class, () -> new CorsConfiguration(invalid));
        }
    }
}
