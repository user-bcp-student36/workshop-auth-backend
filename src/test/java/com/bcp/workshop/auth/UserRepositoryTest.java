package com.bcp.workshop.auth;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class UserRepositoryTest {
    @Test
    void readsLiteralUsernamesAndProtectsStoredHashFromToString() throws Exception {
        try (var database = new TestDatabase()) {
            var repository = database.repository(false);
            repository.initialize();
            try (var connection = database.connections.open();
                 var insert = connection.prepareStatement("""
                         INSERT INTO legacy_users(username,password_hash,display_name,role)
                         VALUES (?,?,?,?)
                         """)) {
                insert.setString(1, "o'brien");
                insert.setString(2, TestDatabase.STUDENT_HASH);
                insert.setString(3, "Example");
                insert.setString(4, "STUDENT");
                insert.executeUpdate();
            }
            var stored = repository.findByUsername("o'brien").orElseThrow();
            assertEquals("o'brien", stored.user().username());
            assertFalse(stored.toString().contains(stored.passwordHash()));
            assertTrue(repository.findByUsername("' OR 1=1 --").isEmpty());
        }
    }

    @Test
    void reportsSqlFailuresExplicitly() {
        var brokenConnections = new DatabaseConnections("jdbc:sqlite::memory:") {
            @Override
            public java.sql.Connection open() throws SQLException {
                throw new SQLException("synthetic connection failure");
            }
        };
        var repository = new UserRepository(brokenConnections,
                new PasswordMigration(TestDatabase.HASHER), TestDatabase.HASHER, false,
                TestDatabase.STUDENT_HASH, TestDatabase.TRAINER_HASH);
        assertThrows(PersistenceFailure.class, repository::initialize);
        assertThrows(PersistenceFailure.class, () -> repository.findByUsername("student"));
    }

    @Test
    void rejectsUnknownOrAmbiguousSchemaInsteadOfSkippingIt() throws Exception {
        for (String schema : new String[]{
                "CREATE TABLE legacy_users (id INTEGER)",
                "CREATE TABLE legacy_users (password TEXT, password_hash TEXT)"}) {
            try (var database = new TestDatabase();
                 var connection = database.connections.open();
                 var statement = connection.createStatement()) {
                statement.executeUpdate(schema);
                assertThrows(IllegalStateException.class, () -> database.repository(true).initialize());
            }
        }
    }

    @Test
    void rejectsInvalidSeedHashesExplicitly() throws Exception {
        try (var database = new TestDatabase()) {
            var repository = new UserRepository(database.connections,
                    new PasswordMigration(TestDatabase.HASHER), TestDatabase.HASHER, false,
                    "invalid", TestDatabase.TRAINER_HASH);
            assertThrows(IllegalArgumentException.class, repository::initialize);
        }
    }

    @Test
    void validatesDatabaseConfigurationAndDirectoryErrors() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> new DatabaseConnections("jdbc:unsupported:example"));
        new DatabaseConnections("jdbc:sqlite::memory:").prepareDirectory();
        new DatabaseConnections("jdbc:sqlite:file:synthetic?mode=memory").prepareDirectory();
        new DatabaseConnections("jdbc:sqlite:synthetic-no-open.db").prepareDirectory();
        try (var database = new TestDatabase()) {
            var invalidParent = new DatabaseConnections("jdbc:sqlite:"
                    + database.file.toAbsolutePath().resolve("nested").resolve("users.db"));
            assertThrows(PersistenceFailure.class, invalidParent::prepareDirectory);
        }
    }
}
