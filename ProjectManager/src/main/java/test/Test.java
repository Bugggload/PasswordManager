package test;

import database.DatabaseConnection;
import database.UserTableDML;
import database.PasswordTableDML;
import database.PasswordTableDQL;

import dataHoldingClasses.User;
import dataHoldingClasses.Password;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.UUID;


public class Test
{

    public static void main(String[] args)
    {
        try
        {
            // INSERT a test user

            String email = "test-" + UUID.randomUUID() + "@example.com";

            User user = new User(email, "Alex", "Smith", "Test user");

            UserTableDML.insertUser(user);




            // Read the generated user ID

            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT PKUserID FROM PasswordManagerUsers WHERE Email = ?"))
            {
                statement.setString(1, email);

                try (ResultSet results = statement.executeQuery())
                {
                    if (!results.next())
                    {
                        throw new IllegalStateException("User insertion failed.");
                    }

                    user.setUserID(results.getInt("PKUserID"));
                }
            }

            System.out.println("PASS: Insert user.");




            // UPDATE the user's first name

            user.setFirstName("Jamie");

            UserTableDML.updateUser(user);

            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT FirstName FROM PasswordManagerUsers WHERE PKUserID = ?"))
            {
                statement.setInt(1, user.getUserID());

                try (ResultSet results = statement.executeQuery())
                {
                    if (!results.next() || !"Jamie".equals(results.getString("FirstName")))
                    {
                        throw new IllegalStateException("User update failed.");
                    }
                }
            }

            System.out.println("PASS: Update user.");




            // INSERT a test password entry

            Password entry = new Password(user.getUserID(), "Test website", "alex", "FakeTestPassword", "https://example.com", "Test entry");

            PasswordTableDML.insertPassword(entry);




            // READ the entry using your DQL class

            ArrayList<Password> passwords = PasswordTableDQL.getPasswordsByUserID(user.getUserID());

            if (passwords.size() != 1)
            {
                throw new IllegalStateException("Expected exactly one password entry.");
            }

            Password savedEntry = passwords.get(0);

            if (!"Test website".equals(savedEntry.getTitle()))
            {
                throw new IllegalStateException("The saved title is incorrect.");
            }

            System.out.println("PASS: Insert and read password entry.");




            // UPDATE the entry's title

            savedEntry.setTitle("Updated website");

            PasswordTableDML.updatePassword(savedEntry);

            passwords = PasswordTableDQL.getPasswordsByUserID(user.getUserID());

            if (passwords.size() != 1 || !"Updated website".equals(passwords.get(0).getTitle()))
            {
                throw new IllegalStateException("Password entry update failed.");
            }

            System.out.println("PASS: Update password entry.");




            // DELETE the password entry first

            PasswordTableDML.deletePassword(savedEntry.getPasswordID(), user.getUserID());

            passwords = PasswordTableDQL.getPasswordsByUserID(user.getUserID());

            if (!passwords.isEmpty())
            {
                throw new IllegalStateException("Password entry deletion failed.");
            }

            System.out.println("PASS: Delete password entry.");




            // DELETE the test user

            UserTableDML.deleteUser(user.getUserID());

            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement("SELECT PKUserID FROM PasswordManagerUsers WHERE PKUserID = ?"))
            {
                statement.setInt(1, user.getUserID());

                try (ResultSet results = statement.executeQuery())
                {
                    if (results.next())
                    {
                        throw new IllegalStateException("User deletion failed.");
                    }
                }
            }

            System.out.println("PASS: Delete user.");

            System.out.println("All tests passed!");
        }
        catch (SQLException | IllegalArgumentException | IllegalStateException e)
        {
            e.printStackTrace();
        }
    }

}