package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class PasswordMigration {
    private static final String CREATE_USERS = """
            CREATE TABLE IF NOT EXISTS legacy_users (
                id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE,
                password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)
            """;
    private final PasswordHasher hasher;

    @Inject
    public PasswordMigration(PasswordHasher hasher) {
        this.hasher = hasher;
    }

    void prepareSchema(Connection connection, boolean migrationAllowed) throws SQLException {
        Set<String> columns = columns(connection);
        if (columns.isEmpty()) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate(CREATE_USERS);
            }
            return;
        }
        if (columns.contains("password_hash") && !columns.contains("password")) {
            validateHashes(connection);
            return;
        }
        if (!columns.contains("password") || columns.contains("password_hash")) {
            throw new IllegalStateException("Unsupported user schema");
        }
        if (!migrationAllowed) {
            throw new IllegalStateException("Legacy password migration requires explicit authorization");
        }
        migrate(connection);
    }

    private Set<String> columns(Connection connection) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (rows.next()) {
                columns.add(rows.getString("name"));
            }
        }
        return columns;
    }

    private void migrate(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE legacy_users RENAME TO legacy_users_plaintext");
            statement.executeUpdate(CREATE_USERS);
        }
        copyUsers(connection);
        validateHashes(connection);
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE legacy_users_plaintext");
        }
    }

    private void copyUsers(Connection connection) throws SQLException {
        try (var select = connection.prepareStatement(
                "SELECT id,username,password,display_name,role FROM legacy_users_plaintext");
             var rows = select.executeQuery();
             var insert = connection.prepareStatement("""
                     INSERT INTO legacy_users(id,username,password_hash,display_name,role)
                     VALUES (?,?,?,?,?)
                     """)) {
            while (rows.next()) {
                insert.setLong(1, rows.getLong("id"));
                insert.setString(2, rows.getString("username"));
                insert.setString(3, hasher.hash(rows.getString("password")));
                insert.setString(4, rows.getString("display_name"));
                insert.setString(5, rows.getString("role"));
                insert.executeUpdate();
            }
        }
    }

    private void validateHashes(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT password_hash FROM legacy_users");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                hasher.validate(rows.getString("password_hash"));
            }
        }
    }
}
