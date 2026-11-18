/*
This class has three operations:
1) insertPassword(Password entry): Adds a new password entry, stores the new ID in the entry and returns it
2) updatePassword(Password entry): Uses password id and user id to update the entry's information
3) deletePassword(int passwordID, int userID): Uses password id and user id to remove ONE entry

The password is stored ENCRYPTED: entry.getEncryptedPassword() goes into the
PasswordCiphertext + PasswordNonce columns. This class never sees plaintext.

updatePassword and deletePassword throw RecordNotFoundException if the entry doesn't exist
or belongs to a different user.

Each operation comes in two versions:
  - insertPassword(entry)              opens its own connection; saved immediately
  - insertPassword(connection, entry)  uses YOUR connection; use inside TransactionRunner.inTransaction
 */

package database;

import dataHoldingClasses.Password;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;


public class PasswordTableDML
{

    // Adding a new password entry. Returns the generated password ID (also set on the entry).

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
            // Fill the placeholders ('?') in order

            statement.setInt(1, entry.getUserID());
            statement.setString(2, entry.getTitle());
            statement.setString(3, entry.getUsername());
            statement.setBytes(4, entry.getEncryptedPassword().ciphertext());
            statement.setBytes(5, entry.getEncryptedPassword().nonce());
            statement.setString(6, entry.getUrl());
            statement.setString(7, entry.getRemarks());

            // Save the new entry

            statement.executeUpdate();

            // Ask the database which ID it just created

            int newPasswordID = SqlHelpers.readGeneratedID(statement);

            entry.setPasswordID(newPasswordID);

            return newPasswordID;
        }
    }




    // Updating an existing password entry

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
            // The new information

            statement.setString(1, entry.getTitle());
            statement.setString(2, entry.getUsername());
            statement.setBytes(3, entry.getEncryptedPassword().ciphertext());   // ciphertext and nonce are
            statement.setBytes(4, entry.getEncryptedPassword().nonce());        // always replaced together
            statement.setString(5, entry.getUrl());
            statement.setString(6, entry.getRemarks());

            // Which entry to update and who owns it

            statement.setInt(7, entry.getPasswordID());
            statement.setInt(8, entry.getUserID());

            // Save the changes and make sure exactly one entry was updated

            int affectedRows = statement.executeUpdate();

            SqlHelpers.requireOneRow(affectedRows, "No password entry " + entry.getPasswordID() + " for user " + entry.getUserID());
        }
    }




    // Removing a password entry belonging to a user

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
            // Which entry to delete and who owns it

            statement.setInt(1, passwordID);
            statement.setInt(2, userID);

            // Remove the entry and make sure exactly one entry was removed

            int affectedRows = statement.executeUpdate();

            SqlHelpers.requireOneRow(affectedRows, "No password entry " + passwordID + " for user " + userID);
        }
    }

}
