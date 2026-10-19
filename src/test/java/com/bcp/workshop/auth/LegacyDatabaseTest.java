package com.bcp.workshop.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDatabaseTest {
    private LegacyDatabase database;
    private AuthService service;
    Path temporaryDirectory;

    @BeforeEach
    void initializeDatabase() throws Exception {
        DriverManager.registerDriver(new org.sqlite.JDBC());
        temporaryDirectory = Files.createTempDirectory(Files.createDirectories(Path.of("target/test-databases")), "migration-");
        database = new LegacyDatabase();
        database.initialize();
        service = new AuthService(database, new PasswordHasher());
    }

    @Test
    void characterizesSeedUsersAndCredentialFailures() {
        UserRecord student = service.authenticate("student", "Workshop123!");
        assertNotNull(student);
        assertTrue(student.id() > 0);
        assertEquals("student", student.username());
        assertEquals("Workshop Student", student.displayName());
        assertEquals("STUDENT", student.role());
        assertEquals("TRAINER", service.authenticate("trainer", "Trainer123!").role());
        assertNull(service.authenticate("missing", "Workshop123!"));
        assertNull(service.authenticate("student", "wrong"));
        assertNull(service.authenticate(" student", "Workshop123!"));
        assertNull(service.authenticate("student", "Workshop123! "));
    }

    @Test
    void preservesExistingUsersOnRepeatedInitialization() throws Exception {
        String password = "x".repeat(100) + "\u00e9";
        insertUser("existing-user", password, "Existing User", "TRAINER");
        UserRecord before = service.authenticate("existing-user", password);
        database.initialize();
        assertEquals(before, service.authenticate("existing-user", password));
    }

    @Test
    void initializesFreshDatabaseWithWorkingCredentials() throws Exception {
        execute("DROP TABLE legacy_users");
        database.initialize();
        assertNotNull(service.authenticate("student", "Workshop123!"));
        assertNotNull(service.authenticate("trainer", "Trainer123!"));
    }

    @Test
    void returnsNullForDatabaseFailure() throws Exception {
        withMissingTable(() -> assertNull(service.authenticate("student", "Workshop123!")));
    }

    @Test
    void treatsSqlCharactersAsLiteralCredentials() throws Exception {
        insertUser("o'connor", "pass'word", "Literal User", "STUDENT");
        assertNotNull(service.authenticate("o'connor", "pass'word"));
        assertNull(service.authenticate("o'connor", "wrong"));
        assertNull(service.authenticate("student' OR '1'='1' --", "wrong"));
        assertNull(service.authenticate("student", "' OR '1'='1"));
    }

    @Test
    void migratesLegacyPasswordsAndPreservesUsersIdempotently() throws Exception {
        String url = createLegacyFixture();
        LegacyDatabase migrated = new LegacyDatabase(new PasswordHasher(), url);
        migrated.initialize();
        AuthService authentication = new AuthService(migrated, new PasswordHasher());
        assertEquals(new UserRecord(42, "legacy-user", "Legacy User", "TRAINER"),
                authentication.authenticate("legacy-user", "legacy-secret"));
        assertNotNull(authentication.authenticate("long-user", "x".repeat(100) + "\u00e9"));
        assertNull(authentication.authenticate("long-user", "x".repeat(100) + "other"));
        String originalHash = migrated.findByUsername("legacy-user").passwordHash();
        migrated.initialize();
        assertEquals(originalHash, migrated.findByUsername("legacy-user").passwordHash());
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            try (var columns = statement.executeQuery("PRAGMA table_info(legacy_users)")) {
                Set<String> names = new HashSet<>();
                while (columns.next()) {
                    names.add(columns.getString("name"));
                }
                assertTrue(names.contains("password_hash"));
                assertFalse(names.contains("password"));
            }
            try (var version = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(version.next());
                assertEquals(1, version.getInt(1));
            }
        }
        String fileBytes = new String(Files.readAllBytes(temporaryDirectory.resolve("legacy.db")), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(fileBytes.contains("legacy-secret"));
    }

    @Test
    void rollsBackMigrationWhenHashingFails() throws Exception {
        String url = createLegacyFixture();
        PasswordHasher failing = new PasswordHasher() {
            private int calls;

            @Override
            public String hash(String password) {
                if (++calls == 2) {
                    throw new IllegalArgumentException("Isolated hashing failure");
                }
                return super.hash(password);
            }
        };
        assertThrows(IllegalStateException.class, () -> new LegacyDatabase(failing, url).initialize());
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            try (var users = statement.executeQuery("SELECT count(*) FROM legacy_users WHERE password IS NOT NULL")) {
                assertTrue(users.next());
                assertEquals(2, users.getInt(1));
            }
            try (var version = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(version.next());
                assertEquals(0, version.getInt(1));
            }
            try (var replacement = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='migrated_users'")) {
                assertTrue(replacement.next());
                assertEquals(0, replacement.getInt(1));
            }
        }
        new LegacyDatabase(new PasswordHasher(), url).initialize();
    }

    @Test
    void refusesToStartWithInvalidDatabaseLocation() {
        assertThrows(IllegalStateException.class, () -> new LegacyDatabase(new PasswordHasher(), "invalid-jdbc-url").initialize());
    }

    private String createLegacyFixture() throws Exception {
        String url = "jdbc:sqlite:" + temporaryDirectory.resolve("legacy.db");
        try (var connection = DriverManager.getConnection(url); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE legacy_users(id INTEGER PRIMARY KEY AUTOINCREMENT, username TEXT UNIQUE, password TEXT NOT NULL, display_name TEXT NOT NULL, role TEXT NOT NULL)");
            try (var insert = connection.prepareStatement("INSERT INTO legacy_users VALUES (?,?,?,?,?)")) {
                for (int index = 0; index < 2; index++) {
                    insert.setLong(1, 42 + index);
                    insert.setString(2, index == 0 ? "legacy-user" : "long-user");
                    insert.setString(3, index == 0 ? "legacy-secret" : "x".repeat(100) + "\u00e9");
                    insert.setString(4, "Legacy User");
                    insert.setString(5, "TRAINER");
                    insert.executeUpdate();
                }
            }
        }
        return url;
    }

    static String databaseUrl() {
        String url = System.getenv("AUTH_DB_URL");
        assertNotNull(url, "Tests require an isolated AUTH_DB_URL");
        assertTrue(url.contains("target/test-databases/"), "Tests must not use the workshop database");
        return url;
    }

    static void insertUser(String username, String password, String displayName, String role) throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl());
             var statement = connection.prepareStatement("INSERT OR IGNORE INTO legacy_users(username,password_hash,display_name,role) VALUES (?,?,?,?)")) {
            statement.setString(1, username);
            statement.setString(2, new PasswordHasher().hash(password));
            statement.setString(3, displayName);
            statement.setString(4, role);
            statement.executeUpdate();
        }
    }

    static void withMissingTable(Runnable assertion) throws Exception {
        execute("ALTER TABLE legacy_users RENAME TO temporarily_unavailable_users");
        try {
            assertion.run();
        } finally {
            execute("ALTER TABLE temporarily_unavailable_users RENAME TO legacy_users");
        }
    }

    static void execute(String sql) throws Exception {
        try (var connection = DriverManager.getConnection(databaseUrl()); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}