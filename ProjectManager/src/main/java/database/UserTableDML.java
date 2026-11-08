/*
This class has three methods: 
1) public void insertUser(User user): Adds a new user in the database
2) public void updateUser(User user): Uses user id to update the user's information
3) public void removeUser(User user): Uses user id to remove the user's information
 */

package database;

import dataHoldingClasses.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public class UserTableDML
{

    // Inserting a new user
    public static void insertUser(User user) throws SQLException
    {
        String sql = "INSERT INTO PasswordManagerUsers (Email, FirstName, LastName, Remarks) VALUES (?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            // Filling the placeholders in order
            statement.setString(1, user.getEmail());
            statement.setString(2, user.getFirstName());
            statement.setString(3, user.getLastName());
            statement.setString(4, user.getRemarks());

            // Save the new user

            statement.executeUpdate();
        }
    }




    // Changing an existing user's information
    public static void updateUser(User user) throws SQLException
    {
        String sql = "UPDATE PasswordManagerUsers SET Email = ?, FirstName = ?, LastName = ?, Remarks = ? WHERE PKUserID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            // The new information

            statement.setString(1, user.getEmail());
            statement.setString(2, user.getFirstName());
            statement.setString(3, user.getLastName());
            statement.setString(4, user.getRemarks());

            // Which user to update

            statement.setInt(5, user.getUserID());

            // Save the changes

            statement.executeUpdate();
        }
    }




    // Removing a user using their ID

    public static void deleteUser(int userID) throws SQLException
    {
        String sql = "DELETE FROM PasswordManagerUsers WHERE PKUserID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            // Which user to delete

            statement.setInt(1, userID);

            // Remove the user

            statement.executeUpdate();
        }
    }

}