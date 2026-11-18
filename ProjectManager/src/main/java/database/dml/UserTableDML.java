package database.dml;

import database.DatabaseConnection;
import models.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/** Writes to PasswordManagerUsers. Each method has an overload taking a Connection for use in transactions. */
public final class UserTableDML
{
    private UserTableDML()
    {
    }

    /** Returns the new ID and sets it on the user. */
    public static int insertUser(User user) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            return insertUser(connection, user);
        }
    }

    public static int insertUser(Connection connection, User user) throws SQLException
    {
        String sql = "INSERT INTO PasswordManagerUsers (Email, FirstName, LastName, Remarks) VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
        {
            statement.setString(1, user.getEmail());
            statement.setString(2, user.getFirstName());
            statement.setString(3, user.getLastName());
            statement.setString(4, user.getRemarks());

            statement.executeUpdate();

            int newUserID = DatabaseConnection.readGeneratedID(statement);

            user.setUserID(newUserID);

            return newUserID;
        }
    }

    /** Throws RecordNotFoundException if no user has that ID. */
    public static void updateUser(User user) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            updateUser(connection, user);
        }
    }

    public static void updateUser(Connection connection, User user) throws SQLException
    {
        String sql = "UPDATE PasswordManagerUsers SET Email = ?, FirstName = ?, LastName = ?, Remarks = ? WHERE PKUserID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setString(1, user.getEmail());
            statement.setString(2, user.getFirstName());
            statement.setString(3, user.getLastName());
            statement.setString(4, user.getRemarks());
            statement.setInt(5, user.getUserID());

            DatabaseConnection.requireOneRow(statement.executeUpdate(), "No user with ID " + user.getUserID());
        }
    }

    /** Also permanently deletes the user's vault and all their passwords (ON DELETE CASCADE). */
    public static void deleteUser(int userID) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            deleteUser(connection, userID);
        }
    }

    public static void deleteUser(Connection connection, int userID) throws SQLException
    {
        String sql = "DELETE FROM PasswordManagerUsers WHERE PKUserID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setInt(1, userID);

            DatabaseConnection.requireOneRow(statement.executeUpdate(), "No user with ID " + userID);
        }
    }
}
