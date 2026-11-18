/*
Keeps the database's table structure (schema) up to date.

How it works:
  - Every SQLite file has a built-in number, "PRAGMA user_version", that starts at 0.
    We use it to remember how many migrations this file has already had.
  - MIGRATIONS below is an ordered list. Migration 1 is MIGRATIONS[0], migration 2 is MIGRATIONS[1], ...
  - migrate() runs only the migrations the file hasn't had yet, each inside a transaction,
    and bumps user_version after each one.

RULES for changing the schema:
  1) NEVER edit or reorder a migration that has already been committed. Other people's
     databases have already run it; editing it won't re-run it for them.
  2) To change the schema, ADD a new entry at the END of MIGRATIONS.
 */

package database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

final class SchemaMigrator
{

    // Each inner array is one migration: a list of SQL statements run together

    private static final String[][] MIGRATIONS =
    {
        // ---------- Migration 1: the original two tables ----------
        // Uses IF NOT EXISTS because databases made before migrations existed
        // already have these tables but still have user_version = 0.
        {
            "CREATE TABLE IF NOT EXISTS PasswordManagerUsers (" +
            "PKUserID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "Email TEXT NOT NULL UNIQUE, " +
            "FirstName TEXT NOT NULL, " +
            "LastName TEXT NOT NULL, " +
            "Remarks TEXT" +
            ")",

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
        },

        // ---------- Migration 2: deleting a user also deletes their passwords ----------
        // SQLite can't change an existing foreign key, so we rebuild the table:
        //   1) create a new table with the rule we want
        //   2) copy every row across (IDs included, so nothing gets renumbered)
        //   3) drop the old table
        //   4) rename the new one to the old name
        // All four run in one transaction (see applyMigration), so it's all-or-nothing.
        {
            "CREATE TABLE PasswordManagerPasswords_new (" +
            "PKPasswordID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "FKUserID INTEGER NOT NULL, " +
            "Title TEXT NOT NULL, " +
            "Username TEXT NOT NULL, " +
            "Password TEXT NOT NULL, " +
            "URL TEXT, " +
            "Remarks TEXT, " +
            "FOREIGN KEY (FKUserID) " +
            "REFERENCES PasswordManagerUsers(PKUserID) " +
            "ON DELETE CASCADE" +
            ")",

            "INSERT INTO PasswordManagerPasswords_new (PKPasswordID, FKUserID, Title, Username, Password, URL, Remarks) " +
            "SELECT PKPasswordID, FKUserID, Title, Username, Password, URL, Remarks FROM PasswordManagerPasswords",

            "DROP TABLE PasswordManagerPasswords",

            "ALTER TABLE PasswordManagerPasswords_new RENAME TO PasswordManagerPasswords",

            // Index: makes "WHERE FKUserID = ?" lookups and cascade deletes fast
            // instead of scanning every password row
            "CREATE INDEX IdxPasswordsByUser ON PasswordManagerPasswords (FKUserID)"
        },

        // ---------- Migration 3 goes here (Step 9) ----------
    };




    private SchemaMigrator()
    {
        // Utility class: no objects
    }




    // The version a fully up-to-date database has

    static int latestVersion()
    {
        return MIGRATIONS.length;
    }




    // Bring the database up to the latest version. Safe to call on every connection:
    // if the database is already up to date it only reads one number.

    static void migrate(Connection connection) throws SQLException
    {
        int currentVersion = readVersion(connection);

        if (currentVersion > latestVersion())
        {
            // The file was upgraded by a newer copy of this app; we don't know its tables
            throw new SQLException("Database version " + currentVersion
                + " is newer than this program supports (" + latestVersion() + "). Please update the program.");
        }

        for (int version = currentVersion; version < latestVersion(); version++)
        {
            applyMigration(connection, version + 1, MIGRATIONS[version]);
        }
    }




    // Runs one migration as a single transaction: either all of it happens, or none of it does

    private static void applyMigration(Connection connection, int newVersion, String[] statements) throws SQLException
    {
        boolean previousAutoCommit = connection.getAutoCommit();

        connection.setAutoCommit(false);

        try (Statement statement = connection.createStatement())
        {
            for (String sql : statements)
            {
                statement.executeUpdate(sql);
            }

            // PRAGMA can't use '?' placeholders; newVersion is an int we control, so this is safe

            statement.executeUpdate("PRAGMA user_version = " + newVersion);

            connection.commit();
        }
        catch (SQLException e)
        {
            connection.rollback();

            throw new SQLException("Database migration " + newVersion + " failed; no changes were kept.", e);
        }
        finally
        {
            connection.setAutoCommit(previousAutoCommit);
        }
    }




    static int readVersion(Connection connection) throws SQLException
    {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("PRAGMA user_version"))
        {
            return results.getInt(1);
        }
    }

}
