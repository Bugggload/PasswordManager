/*
 The function of this class is to retrieve password data from the tables. We will update it as per the need.
 Methods defined:
 1) static ArrayList<Password> getPasswordsByUserID(int userID): Takes in the user id and returns all the password entries associated with the id in an array.
    The passwords come back still ENCRYPTED; decrypt one only when the user asks to see it.
*/



package database;

import crypto.EncryptedData;
import dataHoldingClasses.Password;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.ArrayList;


public class PasswordTableDQL
{

    // Get all password entries belonging to one user

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
                    // Read the values from one database row

                    int passwordID = results.getInt("PKPasswordID");
                    String title = results.getString("Title");
                    String username = results.getString("Username");
                    EncryptedData encryptedPassword = new EncryptedData(
                        results.getBytes("PasswordNonce"),
                        results.getBytes("PasswordCiphertext"));
                    String url = results.getString("URL");
                    String remarks = results.getString("Remarks");

                    // Use the constructor that includes the password ID

                    Password entry = new Password(passwordID, userID, title, username, encryptedPassword, url, remarks);

                    passwords.add(entry);
                }
            }
        }

        return passwords;
    }

}