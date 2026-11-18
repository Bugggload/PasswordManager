package database;

import models.Password;
import models.User;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaMigratorTest
{

    @TempDir
    Path tempFolder;

    private Path dbFile;




    @BeforeEach
    void setUp()
    {
        dbFile = tempFolder.resolve("test.db");

        DatabaseConnection.setDatabasePath(dbFile);
    }




    // A brand-new file gets every migration

    @Test
    void newDatabaseIsMigratedToLatestVersion() throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            assertEquals(SchemaMigrator.latestVersion(), SchemaMigrator.readVersion(connection));
            assertTrue(tableExists(connection, "PasswordManagerUsers"));
            assertTrue(tableExists(connection, "PasswordManagerPasswords"));
            assertTrue(tableExists(connection, "UserVaults"));
        }
    }




    // getConnection() is called all the time; the second call must not break or re-run anything

    @Test
    void migratingTwiceIsHarmless() throws SQLException
    {
        DatabaseConnection.getConnection().close();

        try (Connection connection = DatabaseConnection.getConnection())
        {
            assertEquals(SchemaMigrator.latestVersion(), SchemaMigrator.readVersion(connection));
        }
    }




    // Migration 1: a database made before migrations existed (tables there, user_version 0)
    // must be adopted without errors or data loss

    @Test
    void migration1AdoptsDatabaseFromBeforeMigrations() throws SQLException
    {
        try (Connection raw = openRaw();
             Statement statement = raw.createStatement())
        {
            statement.executeUpdate("CREATE TABLE PasswordManagerUsers (PKUserID INTEGER PRIMARY KEY AUTOINCREMENT, Email TEXT NOT NULL UNIQUE, FirstName TEXT NOT NULL, LastName TEXT NOT NULL, Remarks TEXT)");
            statement.executeUpdate("INSERT INTO PasswordManagerUsers (Email, FirstName, LastName) VALUES ('old@example.com', 'Old', 'User')");

            SchemaMigrator.migrateTo(raw, 1);

            assertEquals(1, SchemaMigrator.readVersion(raw));
            assertEquals(1, countRows(raw, "PasswordManagerUsers"), "Existing user must survive migration 1");
        }
    }




    // Migration 2 rebuilds the password table: existing rows must survive with the same IDs,
    // and afterwards deleting the user must cascade

    @Test
    void migration2KeepsExistingPasswordsAndAddsCascade() throws SQLException
    {
        try (Connection raw = openRaw();
             Statement statement = raw.createStatement())
        {
            createVersion1WithData(statement);

            SchemaMigrator.migrateTo(raw, 2);

            try (ResultSet results = statement.executeQuery("SELECT PKPasswordID, Title FROM PasswordManagerPasswords"))
            {
                assertTrue(results.next());
                assertEquals(42, results.getInt("PKPasswordID"), "IDs must not be renumbered");
                assertEquals("Bank", results.getString("Title"));
            }

            statement.executeUpdate("DELETE FROM PasswordManagerUsers WHERE PKUserID = 7");

            assertEquals(0, countRows(raw, "PasswordManagerPasswords"), "Deleting the user must cascade");
        }
    }




    // Migration 3 (encryption): plaintext data is wiped, encrypted columns + vault table appear

    @Test
    void migration3WipesPlaintextAndAddsEncryptedStorage() throws SQLException
    {
        try (Connection raw = openRaw();
             Statement statement = raw.createStatement())
        {
            createVersion1WithData(statement);
            SchemaMigrator.migrateTo(raw, 2);

            SchemaMigrator.migrateTo(raw, 3);

            assertEquals(0, countRows(raw, "PasswordManagerPasswords"), "Plaintext passwords must be gone");
            assertEquals(0, countRows(raw, "PasswordManagerUsers"), "Accounts without a master password must be gone");
            assertTrue(tableExists(raw, "UserVaults"));

            assertFalse(columnExists(raw, "PasswordManagerPasswords", "Password"), "No plaintext column may remain");
            assertTrue(columnExists(raw, "PasswordManagerPasswords", "PasswordCiphertext"));
            assertTrue(columnExists(raw, "PasswordManagerPasswords", "PasswordNonce"));
        }
    }




    // A file touched by a NEWER version of the app must be refused, not silently misused

    @Test
    void databaseFromNewerProgramIsRejected() throws SQLException
    {
        try (Connection raw = openRaw();
             Statement statement = raw.createStatement())
        {
            statement.executeUpdate("PRAGMA user_version = " + (SchemaMigrator.latestVersion() + 1));
        }

        assertThrows(SQLException.class, DatabaseConnection::getConnection);
    }




    // ---------- Helpers ----------

    // A connection that does NOT auto-migrate (unlike DatabaseConnection.getConnection)

    private Connection openRaw() throws SQLException
    {
        Connection raw = DriverManager.getConnection("jdbc:sqlite:" + dbFile);

        try (Statement statement = raw.createStatement())
        {
            statement.execute("PRAGMA foreign_keys = ON");
        }

        return raw;
    }


    // The database exactly as version 1 looked, with one user (7) and one password (42)

    private void createVersion1WithData(Statement statement) throws SQLException
    {
        statement.executeUpdate("CREATE TABLE PasswordManagerUsers (PKUserID INTEGER PRIMARY KEY AUTOINCREMENT, Email TEXT NOT NULL UNIQUE, FirstName TEXT NOT NULL, LastName TEXT NOT NULL, Remarks TEXT)");
        statement.executeUpdate("CREATE TABLE PasswordManagerPasswords (PKPasswordID INTEGER PRIMARY KEY AUTOINCREMENT, FKUserID INTEGER NOT NULL, Title TEXT NOT NULL, Username TEXT NOT NULL, Password TEXT NOT NULL, URL TEXT, Remarks TEXT, FOREIGN KEY (FKUserID) REFERENCES PasswordManagerUsers(PKUserID))");
        statement.executeUpdate("INSERT INTO PasswordManagerUsers (PKUserID, Email, FirstName, LastName) VALUES (7, 'old@example.com', 'Old', 'User')");
        statement.executeUpdate("INSERT INTO PasswordManagerPasswords (PKPasswordID, FKUserID, Title, Username, Password) VALUES (42, 7, 'Bank', 'old', 'secret')");
        statement.executeUpdate("PRAGMA user_version = 1");
    }


    private int countRows(Connection connection, String tableName) throws SQLException
    {
        // Table names can't be '?' parameters; these come from the test itself, never from users
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM " + tableName))
        {
            return results.getInt(1);
        }
    }


    private boolean tableExists(Connection connection, String tableName) throws SQLException
    {
        try (var statement = connection.prepareStatement("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?"))
        {
            statement.setString(1, tableName);

            try (ResultSet results = statement.executeQuery())
            {
                return results.next();
            }
        }
    }


    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException
    {
        try (var statement = connection.prepareStatement("SELECT 1 FROM pragma_table_info(?) WHERE name = ?"))
        {
            statement.setString(1, tableName);
            statement.setString(2, columnName);

            try (ResultSet results = statement.executeQuery())
            {
                return results.next();
            }
        }
    }

}
