package com.bcp.workshop.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.sqlite.SQLiteDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

@ApplicationScoped
public class DatabaseConnections {
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";
    private final String url;
    private final SQLiteDataSource source;

    @Inject
    public DatabaseConnections(@ConfigProperty(name = "auth.db.url") String url) {
        if (!url.startsWith(SQLITE_PREFIX)) {
            throw new IllegalArgumentException("Only SQLite database URLs are supported");
        }
        this.url = url;
        source = new SQLiteDataSource();
        source.setUrl(url);
    }

    public Connection open() throws SQLException {
        return source.getConnection();
    }

    public void prepareDirectory() {
        String location = url.substring(SQLITE_PREFIX.length());
        if (location.equals(":memory:") || location.startsWith("file:")) {
            return;
        }
        Path parent = Path.of(location).getParent();
        if (parent == null) {
            return;
        }
        try {
            Files.createDirectories(parent);
        } catch (IOException error) {
            throw new PersistenceFailure("Unable to prepare database directory", error);
        }
    }
}
