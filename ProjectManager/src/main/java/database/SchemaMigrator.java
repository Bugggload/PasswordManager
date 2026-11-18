package database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Versioned schema changes tracked with PRAGMA user_version.
 * Never edit a committed migration; append a new one instead.
 */
final class SchemaMigrator
{
    private static final String[][] MIGRATIONS =
    {
        // 1: original tables (IF NOT EXISTS adopts databases created before migrations existed)
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

        // 2: ON DELETE CASCADE (SQLite can't alter a foreign key, so the table is rebuilt)
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

            "CREATE INDEX IdxPasswordsByUser ON PasswordManagerPasswords (FKUserID)"
        },

        // 3: encryption. Old plaintext rows can't be encrypted without a master password, so they're wiped (pre-release only).
        {
            "DROP TABLE PasswordManagerPasswords",

            "DELETE FROM PasswordManagerUsers",

            "CREATE TABLE UserVaults (" +
            "FKUserID INTEGER PRIMARY KEY, " +
            "KdfSalt BLOB NOT NULL, " +
            "KdfMemoryKiB INTEGER NOT NULL, " +
            "KdfIterations INTEGER NOT NULL, " +
            "KdfParallelism INTEGER NOT NULL, " +
            "VaultKeyNonce BLOB NOT NULL, " +
            "EncryptedVaultKey BLOB NOT NULL, " +
            "CryptoVersion INTEGER NOT NULL DEFAULT 1, " +
            "FOREIGN KEY (FKUserID) " +
            "REFERENCES PasswordManagerUsers(PKUserID) " +
            "ON DELETE CASCADE" +
            ")",

            "CREATE TABLE PasswordManagerPasswords (" +
            "PKPasswordID INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "FKUserID INTEGER NOT NULL, " +
            "Title TEXT NOT NULL, " +
            "Username TEXT NOT NULL, " +
            "PasswordCiphertext BLOB NOT NULL, " +
            "PasswordNonce BLOB NOT NULL, " +
            "URL TEXT, " +
            "Remarks TEXT, " +
            "CryptoVersion INTEGER NOT NULL DEFAULT 1, " +
            "FOREIGN KEY (FKUserID) " +
            "REFERENCES PasswordManagerUsers(PKUserID) " +
            "ON DELETE CASCADE" +
            ")",

            "CREATE INDEX IdxPasswordsByUser ON PasswordManagerPasswords (FKUserID)"
        },
    };

    private SchemaMigrator()
    {
    }

    static int latestVersion()
    {
        return MIGRATIONS.length;
    }

    static void migrate(Connection connection) throws SQLException
    {
        migrateTo(connection, latestVersion());
    }

    /** Tests use this to build a database as it was at an older version. */
    static void migrateTo(Connection connection, int targetVersion) throws SQLException
    {
        int currentVersion = readVersion(connection);

        if (currentVersion > latestVersion())
        {
            throw new SQLException("Database version " + currentVersion
                + " is newer than this program supports (" + latestVersion() + "). Please update the program.");
        }

        for (int version = currentVersion; version < targetVersion; version++)
        {
            applyMigration(connection, version + 1, MIGRATIONS[version]);
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

    // One migration = one transaction, so a failure leaves the database untouched
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

            // PRAGMA can't take '?' parameters; newVersion is an int we control
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
}
