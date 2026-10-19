package com.bcp.workshop.auth;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.runtime.Startup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@ApplicationScoped
@Startup
public class LegacyDatabase {
    private static final String DB_URL = System.getenv().getOrDefault("AUTH_DB_URL", "jdbc:sqlite:./data/workshop.db");
    private static final String FIND_USER = "SELECT id, username, password_hash, display_name, role FROM legacy_users WHERE username=?";
    private static final String CREATE_USERS = "CREATE TABLE IF NOT EXISTS legacy_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)";
    private static final String INSERT_USER = "INSERT OR IGNORE INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)";
    private static final String CREATE_REPLACEMENT = "CREATE TABLE migrated_users (id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password_hash TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)";
    private static final String INSERT_MIGRATED = "INSERT INTO migrated_users(id,username,password_hash,display_name,role) VALUES (?,?,?,?,?)";
    private static final int HASH_SCHEMA_VERSION = 1;
    private static final String STUDENT_PASSWORD = "Workshop123!";
    private static final String TRAINER_PASSWORD = "Trainer123!";
    private final PasswordHasher passwordHasher;
    private final String databaseUrl;

    @Inject
    public LegacyDatabase(PasswordHasher passwordHasher) {
        this(passwordHasher, DB_URL);
    }

    public LegacyDatabase() {
        this(new PasswordHasher());
    }

    LegacyDatabase(PasswordHasher passwordHasher, String databaseUrl) {
        this.passwordHasher = passwordHasher;
        this.databaseUrl = databaseUrl;
    }

    @PostConstruct
    void initialize() {
        try {
            createParentDirectory();
            try (Connection connection = DriverManager.getConnection(databaseUrl)) {
                execute(connection, "PRAGMA secure_delete=ON");
                boolean migrated = initializeSchema(connection);
                seedUser(connection, "student", STUDENT_PASSWORD, "Workshop Student", "STUDENT");
                seedUser(connection, "trainer", TRAINER_PASSWORD, "Workshop Trainer", "TRAINER");
                if (migrated) {
                    execute(connection, "PRAGMA wal_checkpoint(TRUNCATE)");
                    execute(connection, "VACUUM");
                }
            }
        } catch (Exception error) {
            throw new IllegalStateException("Authentication database initialization failed", error);
        }
    }

    private void createParentDirectory() throws Exception {
        Path parent = Path.of(databaseUrl.substring("jdbc:sqlite:".length())).getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private boolean initializeSchema(Connection connection) throws SQLException {
        connection.setAutoCommit(false);
        try {
            boolean legacy = hasLegacyPasswords(connection);
            if (legacy) {
                migratePasswords(connection);
            }
            execute(connection, CREATE_USERS);
            execute(connection, "PRAGMA user_version=" + HASH_SCHEMA_VERSION);
            connection.commit();
            return legacy;
        } catch (SQLException | RuntimeException error) {
            connection.rollback();
            throw error;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private boolean hasLegacyPasswords(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet columns = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
            while (columns.next()) {
                if ("password".equals(columns.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void migratePasswords(Connection connection) throws SQLException {
        execute(connection, CREATE_REPLACEMENT);
        copyCredentials(connection);
        execute(connection, "DROP TABLE legacy_users");
        execute(connection, "ALTER TABLE migrated_users RENAME TO legacy_users");
    }

    private void copyCredentials(Connection connection) throws SQLException {
        try (Statement select = connection.createStatement();
             ResultSet users = select.executeQuery("SELECT id,username,password,display_name,role FROM legacy_users");
             PreparedStatement insert = connection.prepareStatement(INSERT_MIGRATED)) {
            while (users.next()) {
                insert.setLong(1, users.getLong("id"));
                insert.setString(2, users.getString("username"));
                insert.setString(3, passwordHasher.hash(users.getString("password")));
                insert.setString(4, users.getString("display_name"));
                insert.setString(5, users.getString("role"));
                insert.executeUpdate();
            }
        }
    }

    private void seedUser(Connection connection, String username, String password, String displayName, String role) throws SQLException {
        if (userExists(connection, username)) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_USER)) {
            statement.setString(1, username);
            statement.setString(2, passwordHasher.hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    private boolean userExists(Connection connection, String username) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM legacy_users WHERE username=?")) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    public StoredCredentials findByUsername(String username) {
        try (Connection connection = DriverManager.getConnection(databaseUrl);
             PreparedStatement statement = connection.prepareStatement(FIND_USER)) {
            statement.setString(1, username);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? new StoredCredentials(mapUser(result), result.getString("password_hash")) : null;
            }
        } catch (Exception error) {
            org.jboss.logging.Logger.getLogger(LegacyDatabase.class).warn("Authentication database query failed");
        }
        return null;
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private UserRecord mapUser(ResultSet result) throws SQLException {
        return new UserRecord(result.getLong("id"), result.getString("username"), result.getString("display_name"), result.getString("role"));
    }
}
