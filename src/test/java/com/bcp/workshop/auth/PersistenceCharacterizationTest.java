package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PersistenceCharacterizationTest {
    @Test
    void repeatedInitializationPreservesSeedUsers() throws Exception {
        try (var database = new TestDatabase()) {
            var repository = database.repository(false);
            repository.initialize();
            var service = new AuthService(repository, TestDatabase.HASHER, null);
            var before = service.authenticate("student", "Workshop123!");
            assertTrue(before.isPresent());
            repository.initialize();
            assertEquals(before, service.authenticate("student", "Workshop123!"));
            assertEquals("TRAINER", service.authenticate("trainer", "Trainer123!").orElseThrow().role());
            assertTrue(service.authenticate("missing", "wrong").isEmpty());
            assertTrue(service.authenticate("student", "wrong").isEmpty());
        }
    }
}
