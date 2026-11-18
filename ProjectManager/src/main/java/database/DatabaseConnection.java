/*
This class defines:
1) public static Connection getConnection(): Connects to the passwordmanager database and
					creates a user and password table if they are not already there
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

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS PasswordManagerUsers (" +
                "PKUserID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "Email TEXT NOT NULL UNIQUE, " +
                "FirstName TEXT NOT NULL, " +
                "LastName TEXT NOT NULL, " +
                "Remarks TEXT" +
                ")"
            );

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS PasswordManagerPasswords (" +
                "PKPasswordID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "FKUserID INTEGER NOT NULL, " +
                "Title TEXT NOT NULL, " +
                "Username TEXT NOT NULL, " +
                "Password TEXT NOT NULL, " +
                "URL TEXT, " +
                "Remarks TEXT, " +
                "FOREIGN KEY (FKUserID) " +
                "REFERENCES PasswordManagerUsers(PKUserID)" +
                ")"
            );

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
