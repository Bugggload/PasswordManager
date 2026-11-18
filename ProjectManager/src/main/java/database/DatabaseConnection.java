package database;

import exceptions.RecordNotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Opens SQLite connections with foreign keys on and the schema up to date.
 * Database file: setDatabasePath(...), else -Dpasswordmanager.db=..., else ~/.passwordmanager/passwordmanager.db
 */
public final class DatabaseConnection
{
    private static final String DB_PATH_PROPERTY = "passwordmanager.db";

    private static Path databasePath = defaultDatabasePath();

    private DatabaseConnection()
    {
    }

    public static Path getDatabasePath()
    {
        return databasePath;
    }

    public static void setDatabasePath(Path path)
    {
        if (path == null)
        {
            throw new IllegalArgumentException("Database path cannot be null.");
        }

        databasePath = path.toAbsolutePath();
    }

    public static Connection getConnection() throws SQLException
    {
        createFolderIfMissing(databasePath.getParent());

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);

        try
        {
            // SQLite leaves foreign keys off unless enabled on every connection
            try (Statement statement = connection.createStatement())
            {
                statement.execute("PRAGMA foreign_keys = ON");
            }

            SchemaMigrator.migrate(connection);
        }
        catch (SQLException e)
        {
            try
            {
                connection.close();
            }
            catch (SQLException closeError)
            {
                e.addSuppressed(closeError);
            }

            throw e;
        }

        return connection;
    }

    /** Returns the ID of the row just inserted. The statement must use Statement.RETURN_GENERATED_KEYS. */
    public static int readGeneratedID(PreparedStatement statement) throws SQLException
    {
        try (ResultSet keys = statement.getGeneratedKeys())
        {
            if (!keys.next())
            {
                throw new SQLException("Insert succeeded but no ID was returned.");
            }

            return keys.getInt(1);
        }
    }

    /** Throws RecordNotFoundException unless exactly one row was changed. */
    public static void requireOneRow(int affectedRows, String message) throws RecordNotFoundException
    {
        if (affectedRows != 1)
        {
            throw new RecordNotFoundException(message);
        }
    }

    private static Path defaultDatabasePath()
    {
        String override = System.getProperty(DB_PATH_PROPERTY);

        if (override != null && !override.isBlank())
        {
            return Path.of(override).toAbsolutePath();
        }

        return Path.of(System.getProperty("user.home"), ".passwordmanager", "passwordmanager.db");
    }

    // On POSIX systems the folder is owner-only (rwx------); on Windows the home folder is already private
    private static void createFolderIfMissing(Path folder) throws SQLException
    {
        if (folder == null || Files.exists(folder))
        {
            return;
        }

        try
        {
            if (folder.getFileSystem().supportedFileAttributeViews().contains("posix"))
            {
                Files.createDirectories(folder, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            }
            else
            {
                Files.createDirectories(folder);
            }
        }
        catch (IOException e)
        {
            throw new SQLException("Could not create database folder for " + databasePath, e);
        }
    }
}
