package database.dql;

import database.DatabaseConnection;
import models.FieldRules;
import models.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Reads from PasswordManagerUsers. */
public final class UserTableDQL
{
    private UserTableDQL()
    {
    }

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

    /** Case-insensitive: the email is normalized the same way User.setEmail does. */
    public static Optional<User> getUserByEmail(String email) throws SQLException
    {
        String sql = "SELECT * FROM PasswordManagerUsers WHERE Email = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, FieldRules.normalizeEmail(email));

            return readOneUser(statement);
        }
    }

    private static Optional<User> readOneUser(PreparedStatement statement) throws SQLException
    {
        try (ResultSet results = statement.executeQuery())
        {
            if (!results.next())
            {
                return Optional.empty();
            }

            return Optional.of(new User(
                results.getInt("PKUserID"),
                results.getString("Email"),
                results.getString("FirstName"),
                results.getString("LastName"),
                results.getString("Remarks")));
        }
    }
}
