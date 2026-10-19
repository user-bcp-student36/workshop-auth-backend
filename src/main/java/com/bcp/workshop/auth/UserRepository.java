package com.bcp.workshop.auth;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

@Startup
@ApplicationScoped
public class UserRepository {
    private final DatabaseConnections connections;
    private final PasswordMigration migration;
    private final PasswordHasher hasher;
    private final boolean migrationAllowed;
    private final String studentHash;
    private final String trainerHash;

    @Inject
    public UserRepository(DatabaseConnections connections, PasswordMigration migration,
                          PasswordHasher hasher,
                          @ConfigProperty(name = "auth.db.migrate-plaintext", defaultValue = "false")
                          boolean migrationAllowed,
                          @ConfigProperty(name = "auth.seed.student-hash") String studentHash,
                          @ConfigProperty(name = "auth.seed.trainer-hash") String trainerHash) {
        this.connections = connections;
        this.migration = migration;
        this.hasher = hasher;
        this.migrationAllowed = migrationAllowed;
        this.studentHash = studentHash;
        this.trainerHash = trainerHash;
    }

    @PostConstruct
    void initialize() {
        hasher.validate(studentHash);
        hasher.validate(trainerHash);
        connections.prepareDirectory();
        try (Connection connection = connections.open()) {
            initializeTransaction(connection);
        } catch (SQLException error) {
            throw new PersistenceFailure("Unable to initialize user repository", error);
        }
    }

    private void initializeTransaction(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            migration.prepareSchema(connection, migrationAllowed);
            insertSeed(connection, "student", studentHash, "Workshop Student", "STUDENT");
            insertSeed(connection, "trainer", trainerHash, "Workshop Trainer", "TRAINER");
            connection.commit();
        } catch (SQLException | RuntimeException error) {
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                error.addSuppressed(rollbackFailure);
            }
            throw error;
        }
    }

    public Optional<StoredUser> findByUsername(String username) {
        try (Connection connection = connections.open();
             var statement = connection.prepareStatement("""
                     SELECT id,username,password_hash,display_name,role
                     FROM legacy_users WHERE username=?
                     """)) {
            statement.setString(1, username);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                var user = new UserRecord(rows.getLong("id"), rows.getString("username"),
                        rows.getString("display_name"), rows.getString("role"));
                return Optional.of(new StoredUser(user, rows.getString("password_hash")));
            }
        } catch (SQLException error) {
            throw new PersistenceFailure("Unable to query user repository", error);
        }
    }

    private void insertSeed(Connection connection, String username, String hash,
                            String displayName, String role) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO legacy_users(username,password_hash,display_name,role)
                VALUES (?,?,?,?) ON CONFLICT(username) DO NOTHING
                """)) {
            statement.setString(1, username);
            statement.setString(2, hash);
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    public record StoredUser(UserRecord user, String passwordHash) {
        @Override
        public String toString() {
            return "StoredUser[redacted]";
        }
    }
}
