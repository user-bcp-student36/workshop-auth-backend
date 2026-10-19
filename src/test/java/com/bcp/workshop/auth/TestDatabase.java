package com.bcp.workshop.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

final class TestDatabase implements AutoCloseable {
    static final PasswordHasher HASHER = new PasswordHasher(600_000);
    static final String STUDENT_HASH = HASHER.hash("Workshop123!");
    static final String TRAINER_HASH = HASHER.hash("Trainer123!");
    final Path file;
    final String url;
    final DatabaseConnections connections;

    TestDatabase() throws IOException {
        file = Files.createTempFile(Path.of("target"), "auth-isolated-", ".db");
        url = "jdbc:sqlite:" + file.toAbsolutePath();
        connections = new DatabaseConnections(url);
    }

    UserRepository repository(boolean allowMigration) {
        return new UserRepository(connections, new PasswordMigration(HASHER), HASHER,
                allowMigration, STUDENT_HASH, TRAINER_HASH);
    }

    void legacyUser(String username, String password) throws SQLException {
        try (Connection connection = connections.open();
             var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS legacy_users (
                        id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE,
                        password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)
                    """);
            try (var insert = connection.prepareStatement("""
                    INSERT INTO legacy_users(username,password,display_name,role) VALUES (?,?,?,?)
                    """)) {
                insert.setString(1, username);
                insert.setString(2, password);
                insert.setString(3, "<Example & Name>");
                insert.setString(4, "STUDENT");
                insert.executeUpdate();
            }
        }
    }

    @Override
    public void close() throws IOException {
        Files.deleteIfExists(file.resolveSibling(file.getFileName() + "-wal"));
        Files.deleteIfExists(file.resolveSibling(file.getFileName() + "-shm"));
        Files.deleteIfExists(file.resolveSibling(file.getFileName() + "-journal"));
        Files.deleteIfExists(file);
    }
}
