package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TokenCharacterizationTest {
    @Test
    void opaqueTokenRepresentsUserRoleAndOneHourExpiry() {
        long before = Instant.now().getEpochSecond();
        var service = new TokenService(TokenServiceTest.TEST_KEY);
        String token = service.create("synthetic-user", "STUDENT");
        long after = Instant.now().getEpochSecond();
        var claims = service.verify(token);
        assertEquals("synthetic-user", claims.getSubject());
        assertEquals("STUDENT", claims.getClaim("role"));
        long expiry = claims.getExpirationTime().toInstant().getEpochSecond();
        assertTrue(expiry >= before + 3600 && expiry <= after + 3600);
    }
}
