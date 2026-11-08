/*
This class define one method:
1) public static Connection getConnection(): Connects to the passwordmanager database and 
					creates a user and password table if they are not already there
 */

package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseConnection {

    private static final String URL = "jdbc:sqlite:passwordmanager.db";

    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);

        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS PasswordManagerUsers (" +
                "PKUserID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "Email TEXT NOT NULL UNIQUE, " +
                "FirstName TEXT NOT NULL, " +
                "LastName TEXT NOT NULL, " +
                "Remarks TEXT" +
                ")"
            );

            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS PasswordManagerPasswords (" +
                "PKPasswordID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "FKUserID INTEGER NOT NULL, " +
                "Title TEXT NOT NULL, " +
                "Username TEXT NOT NULL, " +
                "Password TEXT NOT NULL, " +
                "URL TEXT, " +
                "Remarks TEXT, " +
                "FOREIGN KEY (FKUserID) " +
                "REFERENCES PasswordManagerUsers(PKUserID)" +
                ")"
            );

        } catch (SQLException e) {
            try {
                connection.close();
            } catch (SQLException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }

        return connection;
    }
}