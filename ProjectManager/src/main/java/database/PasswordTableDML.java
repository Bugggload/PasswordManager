/*
This class has three methods: 
1) public void insertPassword(Password entry): Adds a new password entry in the database
2) public void updatePassword(Password entry): Uses password id and user id to update the entry's information
3) public void removePassword(Password entry): Uses password id to user id remove the entry's information
 */

package database;

import dataHoldingClasses.Password;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public class PasswordTableDML
{

    // Adding a new password entry

    public static void insertPassword(Password entry) throws SQLException
    {
        String sql = "INSERT INTO PasswordManagerPasswords (FKUserID, Title, Username, Password, URL, Remarks) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            // Fill the placeholders ('?') in order

            statement.setInt(1, entry.getUserID());
            statement.setString(2, entry.getTitle());
            statement.setString(3, entry.getUsername());
            statement.setString(4, entry.getPassword());
            statement.setString(5, entry.getUrl());
            statement.setString(6, entry.getRemarks());

            // Save the new entry

            statement.executeUpdate();
        }
    }




    // Updating an existing password entry

    public static void updatePassword(Password entry) throws SQLException
    {
        String sql = "UPDATE PasswordManagerPasswords SET Title = ?, Username = ?, Password = ?, URL = ?, Remarks = ? WHERE PKPasswordID = ? AND FKUserID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            // The new information

            statement.setString(1, entry.getTitle());
            statement.setString(2, entry.getUsername());
            statement.setString(3, entry.getPassword());
            statement.setString(4, entry.getUrl());
            statement.setString(5, entry.getRemarks());

            // Which entry to update and who owns it

            statement.setInt(6, entry.getPasswordID());
            statement.setInt(7, entry.getUserID());

            // Save the changes

            statement.executeUpdate();
        }
    }




    // Removing a password entry belonging to a user

    public static void deletePassword(int passwordID, int userID) throws SQLException
    {
        String sql = "DELETE FROM PasswordManagerPasswords WHERE PKPasswordID = ? AND FKUserID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            // Which entry to delete and who owns it

            statement.setInt(1, passwordID);
            statement.setInt(2, userID);

            // Remove the entry

            statement.executeUpdate();
        }
    }

}