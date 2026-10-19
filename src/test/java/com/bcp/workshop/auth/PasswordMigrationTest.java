package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordMigrationTest {
    @Test
    void migrationRequiresOptInAndPreservesLiteralLongCredentials() throws Exception {
        try (var database = new TestDatabase()) {
            String password = " long-".repeat(80) + " ";
            database.legacyUser("o'brien", password);
            assertThrows(IllegalStateException.class, () -> database.repository(false).initialize());
            var repository = database.repository(true);
            repository.initialize();
            String hash = repository.findByUsername("o'brien").orElseThrow().passwordHash();
            repository.initialize();
            assertEquals(hash, repository.findByUsername("o'brien").orElseThrow().passwordHash());
            var service = new AuthService(repository, TestDatabase.HASHER, null);
            assertTrue(service.authenticate("o'brien", password).isPresent());
            assertTrue(service.authenticate("o'brien", password.trim()).isEmpty());
            assertTrue(service.authenticate("o'brien' --", password).isEmpty());
            try (var connection = database.connections.open();
                 var statement = connection.createStatement()) {
                assertThrows(java.sql.SQLException.class,
                        () -> statement.executeQuery("SELECT password FROM legacy_users"));
                try (var rows = statement.executeQuery(
                        "SELECT COUNT(*) FROM sqlite_master WHERE name='legacy_users_plaintext'")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
            }
        }
    }

    @Test
    void failureRollsBackTheEntireMigration() throws Exception {
        try (var database = new TestDatabase()) {
            database.legacyUser("rollback-user", "synthetic-password");
            var failingHasher = new PasswordHasher(600_000) {
                @Override
                public String hash(String password) {
                    throw new IllegalStateException("synthetic hash failure");
                }
            };
            var repository = new UserRepository(database.connections,
                    new PasswordMigration(failingHasher), TestDatabase.HASHER, true,
                    TestDatabase.STUDENT_HASH, TestDatabase.TRAINER_HASH);
            assertThrows(IllegalStateException.class, repository::initialize);
            try (var connection = database.connections.open();
                 var select = connection.prepareStatement(
                         "SELECT password FROM legacy_users WHERE username=?")) {
                select.setString(1, "rollback-user");
                try (var rows = select.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("synthetic-password", rows.getString(1));
                }
            }
        }
    }
}
