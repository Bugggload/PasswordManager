/*
This class defines:
1) public static Connection getConnection(): Connects to the passwordmanager database and
					makes sure its tables are up to date (see SchemaMigrator)
2) public static Path getDatabasePath() / setDatabasePath(Path): Which database file is used

Where the database lives (first match wins):
  a) setDatabasePath(...) was called (tests use this to point at a temporary file)
  b) the JVM was started with -Dpasswordmanager.db=/some/path/file.db
  c) default: <user home>/.passwordmanager/passwordmanager.db
 */

package database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static final String DB_PATH_PROPERTY = "passwordmanager.db";

    private static Path databasePath = defaultDatabasePath();

    private static Path defaultDatabasePath() {
        String override = System.getProperty(DB_PATH_PROPERTY);

        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath();
        }

        return Path.of(System.getProperty("user.home"), ".passwordmanager", "passwordmanager.db");
    }

    public static Path getDatabasePath() {
        return databasePath;
    }

    public static void setDatabasePath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Database path cannot be null.");
        }

        databasePath = path.toAbsolutePath();
    }

    public static Connection getConnection() throws SQLException {
        // Make sure the folder exists; SQLite creates the file but not missing folders
        try {
            Path folder = databasePath.getParent();

            if (folder != null) {
                Files.createDirectories(folder);
            }
        } catch (IOException e) {
            throw new SQLException("Could not create database folder for " + databasePath, e);
        }

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);

        try {
            // Foreign keys are OFF by default in SQLite and must be turned on for every connection
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
            }

            // Create tables / apply any schema changes this database hasn't had yet
            SchemaMigrator.migrate(connection);

        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }

        return connection;
    }
}
