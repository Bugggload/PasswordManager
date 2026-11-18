package database.dql;

import crypto.EncryptedData;
import database.DatabaseConnection;
import models.Password;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Optional;

/** Reads from PasswordManagerPasswords. Passwords are returned still encrypted. */
public final class PasswordTableDQL
{
    private PasswordTableDQL()
    {
    }

    public static ArrayList<Password> getPasswordsByUserID(int userID) throws SQLException
    {
        if (userID <= 0)
        {
            throw new IllegalArgumentException("User ID must be positive.");
        }

        ArrayList<Password> passwords = new ArrayList<>();

        String sql = "SELECT * FROM PasswordManagerPasswords WHERE FKUserID = ? ORDER BY PKPasswordID";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setInt(1, userID);

            try (ResultSet results = statement.executeQuery())
            {
                while (results.next())
                {
                    passwords.add(readRow(results));
                }
            }
        }

        return passwords;
    }

    /** Empty unless the entry exists AND belongs to that user. */
    public static Optional<Password> getPasswordByID(int passwordID, int userID) throws SQLException
    {
        if (passwordID <= 0 || userID <= 0)
        {
            throw new IllegalArgumentException("IDs must be positive.");
        }

        String sql = "SELECT * FROM PasswordManagerPasswords WHERE PKPasswordID = ? AND FKUserID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setInt(1, passwordID);
            statement.setInt(2, userID);

            try (ResultSet results = statement.executeQuery())
            {
                return results.next() ? Optional.of(readRow(results)) : Optional.empty();
            }
        }
    }

    private static Password readRow(ResultSet results) throws SQLException
    {
        EncryptedData encryptedPassword = new EncryptedData(
            results.getBytes("PasswordNonce"),
            results.getBytes("PasswordCiphertext"));

        return new Password(
            results.getInt("PKPasswordID"),
            results.getInt("FKUserID"),
            results.getString("Title"),
            results.getString("Username"),
            encryptedPassword,
            results.getString("URL"),
            results.getString("Remarks"));
    }
}
