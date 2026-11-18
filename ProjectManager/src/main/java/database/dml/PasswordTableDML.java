package database.dml;

import database.DatabaseConnection;
import models.Password;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Writes to PasswordManagerPasswords. Stores the password as ciphertext + nonce; never sees plaintext.
 * Update/delete match on both password ID and owner ID.
 */
public final class PasswordTableDML
{
    private PasswordTableDML()
    {
    }

    /** Returns the new ID and sets it on the entry. */
    public static int insertPassword(Password entry) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            return insertPassword(connection, entry);
        }
    }

    public static int insertPassword(Connection connection, Password entry) throws SQLException
    {
        String sql = "INSERT INTO PasswordManagerPasswords (FKUserID, Title, Username, PasswordCiphertext, PasswordNonce, URL, Remarks) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            statement.setInt(1, entry.getUserID());
            statement.setString(2, entry.getTitle());
            statement.setString(3, entry.getUsername());
            statement.setBytes(4, entry.getEncryptedPassword().ciphertext());
            statement.setBytes(5, entry.getEncryptedPassword().nonce());
            statement.setString(6, entry.getUrl());
            statement.setString(7, entry.getRemarks());

            statement.executeUpdate();

            int newPasswordID = DatabaseConnection.readGeneratedID(statement);

            entry.setPasswordID(newPasswordID);

            return newPasswordID;
        }
    }

    /** Throws RecordNotFoundException if the entry doesn't exist or belongs to another user. */
    public static void updatePassword(Password entry) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            updatePassword(connection, entry);
        }
    }

    public static void updatePassword(Connection connection, Password entry) throws SQLException
    {
        String sql = "UPDATE PasswordManagerPasswords SET Title = ?, Username = ?, PasswordCiphertext = ?, PasswordNonce = ?, URL = ?, Remarks = ? WHERE PKPasswordID = ? AND FKUserID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, entry.getTitle());
            statement.setString(2, entry.getUsername());
            statement.setBytes(3, entry.getEncryptedPassword().ciphertext());
            statement.setBytes(4, entry.getEncryptedPassword().nonce());
            statement.setString(5, entry.getUrl());
            statement.setString(6, entry.getRemarks());
            statement.setInt(7, entry.getPasswordID());
            statement.setInt(8, entry.getUserID());

            DatabaseConnection.requireOneRow(statement.executeUpdate(),
                "No password entry " + entry.getPasswordID() + " for user " + entry.getUserID());
        }
    }

    /** Deletes one entry. Throws RecordNotFoundException if it doesn't exist or belongs to another user. */
    public static void deletePassword(int passwordID, int userID) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            deletePassword(connection, passwordID, userID);
        }
    }

    public static void deletePassword(Connection connection, int passwordID, int userID) throws SQLException
    {
        String sql = "DELETE FROM PasswordManagerPasswords WHERE PKPasswordID = ? AND FKUserID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setInt(1, passwordID);
            statement.setInt(2, userID);

            DatabaseConnection.requireOneRow(statement.executeUpdate(),
                "No password entry " + passwordID + " for user " + userID);
        }
    }
}
