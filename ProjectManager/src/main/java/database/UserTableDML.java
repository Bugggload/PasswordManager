/*
This class has three methods:
1) public static int insertUser(User user): Adds a new user, stores the new ID in the user object and returns it
2) public static void updateUser(User user): Uses user id to update the user's information
3) public static void deleteUser(int userID): Uses user id to remove the user.
   WARNING: this also permanently deletes ALL of that user's password entries (ON DELETE CASCADE).

updateUser and deleteUser throw RecordNotFoundException if no user has that ID.
 */

package database;

import dataHoldingClasses.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;


public class UserTableDML
{

    // Inserting a new user. Returns the generated user ID (also set on the user object).
    public static int insertUser(User user) throws SQLException
    {
        String sql = "INSERT INTO PasswordManagerUsers (Email, FirstName, LastName, Remarks) VALUES (?, ?, ?, ?)";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            // Filling the placeholders in order
            statement.setString(1, user.getEmail());
            statement.setString(2, user.getFirstName());
            statement.setString(3, user.getLastName());
            statement.setString(4, user.getRemarks());

            // Save the new user

            statement.executeUpdate();

            // Ask the database which ID it just created

            int newUserID = SqlHelpers.readGeneratedID(statement);

            user.setUserID(newUserID);

            return newUserID;
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

            // Save the changes and make sure exactly one user was updated

            int affectedRows = statement.executeUpdate();

            SqlHelpers.requireOneRow(affectedRows, "No user with ID " + user.getUserID());
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

            // Remove the user and make sure exactly one user was removed

            int affectedRows = statement.executeUpdate();

            SqlHelpers.requireOneRow(affectedRows, "No user with ID " + userID);
        }
    }

}
