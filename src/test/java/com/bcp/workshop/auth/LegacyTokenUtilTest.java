package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class LegacyTokenUtilTest {
    @Test
    void characterizesLegacyTokenBeforeReplacement() {
        long before = Instant.now().plusSeconds(3600).getEpochSecond();
        String token = LegacyTokenUtil.create("student", "STUDENT");
        long after = Instant.now().plusSeconds(3600).getEpochSecond();
        String[] fields = new String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8).split("\\|");
        assertEquals(4, fields.length);
        assertEquals("student", fields[0]);
        assertEquals("STUDENT", fields[1]);
        assertTrue(Long.parseLong(fields[2]) >= before);
        assertTrue(Long.parseLong(fields[2]) <= after);
        assertEquals("WORKSHOP_SECRET_2026", fields[3]);
    }
}