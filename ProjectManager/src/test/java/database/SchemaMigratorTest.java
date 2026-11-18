/*
Tests for SchemaMigrator: does a database end up at the right version with the right tables,
no matter what state it started in?
 */

package database;

import static org.junit.jupiter.api.Assertions.assertEquals;
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




    // A database made by the old code (tables exist, but user_version is still 0) must upgrade cleanly

    @Test
    void databaseFromBeforeMigrationsIsUpgraded() throws SQLException
    {
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement statement = raw.createStatement())
        {
            statement.executeUpdate("CREATE TABLE PasswordManagerUsers (PKUserID INTEGER PRIMARY KEY AUTOINCREMENT, Email TEXT NOT NULL UNIQUE, FirstName TEXT NOT NULL, LastName TEXT NOT NULL, Remarks TEXT)");
            statement.executeUpdate("INSERT INTO PasswordManagerUsers (Email, FirstName, LastName) VALUES ('old@example.com', 'Old', 'User')");
        }

        try (Connection connection = DatabaseConnection.getConnection())
        {
            assertEquals(SchemaMigrator.latestVersion(), SchemaMigrator.readVersion(connection));
            assertTrue(UserTableDQL.getUserByEmail("old@example.com").isPresent(), "Existing data must survive the upgrade");
        }
    }




    // Migration 2 rebuilds the password table: existing rows must survive with the same IDs,
    // and afterwards deleting the user must cascade

    @Test
    void migration2KeepsExistingPasswordsAndAddsCascade() throws SQLException
    {
        // Build a database exactly as it looked at version 1, with some data in it

        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement statement = raw.createStatement())
        {
            statement.executeUpdate("CREATE TABLE PasswordManagerUsers (PKUserID INTEGER PRIMARY KEY AUTOINCREMENT, Email TEXT NOT NULL UNIQUE, FirstName TEXT NOT NULL, LastName TEXT NOT NULL, Remarks TEXT)");
            statement.executeUpdate("CREATE TABLE PasswordManagerPasswords (PKPasswordID INTEGER PRIMARY KEY AUTOINCREMENT, FKUserID INTEGER NOT NULL, Title TEXT NOT NULL, Username TEXT NOT NULL, Password TEXT NOT NULL, URL TEXT, Remarks TEXT, FOREIGN KEY (FKUserID) REFERENCES PasswordManagerUsers(PKUserID))");
            statement.executeUpdate("INSERT INTO PasswordManagerUsers (PKUserID, Email, FirstName, LastName) VALUES (7, 'old@example.com', 'Old', 'User')");
            statement.executeUpdate("INSERT INTO PasswordManagerPasswords (PKPasswordID, FKUserID, Title, Username, Password) VALUES (42, 7, 'Bank', 'old', 'secret')");
            statement.executeUpdate("PRAGMA user_version = 1");
        }

        // Upgrade happens on connect

        var passwords = PasswordTableDQL.getPasswordsByUserID(7);

        assertEquals(1, passwords.size());
        assertEquals(42, passwords.get(0).getPasswordID(), "IDs must not be renumbered");
        assertEquals("Bank", passwords.get(0).getTitle());

        // Cascade now works on the upgraded database

        UserTableDML.deleteUser(7);

        assertTrue(PasswordTableDQL.getPasswordsByUserID(7).isEmpty());
    }




    // A file touched by a NEWER version of the app must be refused, not silently misused

    @Test
    void databaseFromNewerProgramIsRejected() throws SQLException
    {
        try (Connection raw = DriverManager.getConnection("jdbc:sqlite:" + dbFile);
             Statement statement = raw.createStatement())
        {
            statement.executeUpdate("PRAGMA user_version = " + (SchemaMigrator.latestVersion() + 1));
        }

        assertThrows(SQLException.class, DatabaseConnection::getConnection);
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

}
