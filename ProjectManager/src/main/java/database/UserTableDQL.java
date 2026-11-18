/*
 The function of this class is to retrieve user data from the tables.
 Methods defined:
 1) static Optional<User> getUserByID(int userID): Finds a user by their ID
 2) static Optional<User> getUserByEmail(String email): Finds a user by their email (used for login)

 Both return Optional.empty() when no user matches, instead of null.
*/

package database;

import dataHoldingClasses.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Optional;


public class UserTableDQL
{

    // Find one user by their ID

    public static Optional<User> getUserByID(int userID) throws SQLException
    {
        if (userID <= 0)
        {
            throw new IllegalArgumentException("User ID must be positive.");
        }

        String sql = "SELECT * FROM PasswordManagerUsers WHERE PKUserID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setInt(1, userID);

            return readOneUser(statement);
        }
    }




    // Find one user by their email (emails are UNIQUE, so there is at most one)

    public static Optional<User> getUserByEmail(String email) throws SQLException
    {
        if (email == null || email.isBlank())
        {
            throw new IllegalArgumentException("Email cannot be blank.");
        }

        String sql = "SELECT * FROM PasswordManagerUsers WHERE Email = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, email);

            return readOneUser(statement);
        }
    }




    // Runs the query and turns the first row (if any) into a User object

    private static Optional<User> readOneUser(PreparedStatement statement) throws SQLException
    {
        try (ResultSet results = statement.executeQuery())
        {
            if (!results.next())
            {
                return Optional.empty();
            }

            User user = new User(
                results.getInt("PKUserID"),
                results.getString("Email"),
                results.getString("FirstName"),
                results.getString("LastName"),
                results.getString("Remarks")
            );

            return Optional.of(user);
        }
    }

}
