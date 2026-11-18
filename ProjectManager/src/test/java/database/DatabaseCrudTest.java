/*
JUnit 5 tests for the user and password tables.

Run all tests:   mvn test        (or in Eclipse: right-click this file -> Run As -> JUnit Test)

Each test gets its own brand-new, empty database in a temporary folder,
so tests can't affect each other or your real database.
 */

package database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dataHoldingClasses.Password;
import dataHoldingClasses.User;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseCrudTest
{

    // JUnit creates a fresh temporary folder for every test and deletes it afterwards

    @TempDir
    Path tempFolder;

    private User user;




    // Runs before EACH @Test: new empty database + one saved user to work with

    @BeforeEach
    void setUp() throws SQLException
    {
        DatabaseConnection.setDatabasePath(tempFolder.resolve("test.db"));

        user = new User("alex@example.com", "Alex", "Smith", "Test user");

        UserTableDML.insertUser(user);
    }




    // ---------- Users: happy paths ----------

    @Test
    void insertUserReturnsTheNewIDAndSetsItOnTheObject() throws SQLException
    {
        User second = new User("sam@example.com", "Sam", "Lee", null);

        int returnedID = UserTableDML.insertUser(second);

        assertTrue(returnedID > 0);
        assertEquals(returnedID, second.getUserID());
        assertNotEquals(user.getUserID(), second.getUserID(), "Each user gets their own ID");
    }




    @Test
    void updateUserChangesTheFirstName() throws SQLException
    {
        user.setFirstName("Jamie");

        UserTableDML.updateUser(user);

        assertEquals("Jamie", readFirstName(user.getUserID()));
    }




    @Test
    void deleteUserRemovesTheUser() throws SQLException
    {
        UserTableDML.deleteUser(user.getUserID());

        assertFalse(userExists(user.getUserID()));
    }




    // ---------- Users: failure paths ----------

    @Test
    void updatingAMissingUserThrows()
    {
        User ghost = new User(9999, "ghost@example.com", "No", "One", null);

        assertThrows(RecordNotFoundException.class, () -> UserTableDML.updateUser(ghost));
    }




    @Test
    void deletingAMissingUserThrows()
    {
        assertThrows(RecordNotFoundException.class, () -> UserTableDML.deleteUser(9999));
    }




    // ---------- Passwords: happy paths ----------

    @Test
    void insertPasswordReturnsTheNewIDAndCanBeReadBack() throws SQLException
    {
        Password entry = newEntry(user);

        int returnedID = PasswordTableDML.insertPassword(entry);

        assertTrue(returnedID > 0);
        assertEquals(returnedID, entry.getPasswordID());

        ArrayList<Password> passwords = PasswordTableDQL.getPasswordsByUserID(user.getUserID());

        assertEquals(1, passwords.size());

        Password saved = passwords.get(0);

        assertEquals(returnedID, saved.getPasswordID());
        assertEquals("Test website", saved.getTitle());
        assertEquals("alex", saved.getUsername());
        assertEquals("FakeTestPassword", saved.getPassword());
        assertEquals("https://example.com", saved.getUrl());
        assertEquals("Test entry", saved.getRemarks());
    }




    @Test
    void updatePasswordChangesTheTitle() throws SQLException
    {
        Password entry = newEntry(user);

        PasswordTableDML.insertPassword(entry);

        entry.setTitle("Updated website");

        PasswordTableDML.updatePassword(entry);

        ArrayList<Password> passwords = PasswordTableDQL.getPasswordsByUserID(user.getUserID());

        assertEquals(1, passwords.size());
        assertEquals("Updated website", passwords.get(0).getTitle());
    }




    @Test
    void deletePasswordRemovesOnlyThatEntry() throws SQLException
    {
        Password keep = newEntry(user);
        Password remove = newEntry(user);

        PasswordTableDML.insertPassword(keep);
        PasswordTableDML.insertPassword(remove);

        PasswordTableDML.deletePassword(remove.getPasswordID(), user.getUserID());

        ArrayList<Password> passwords = PasswordTableDQL.getPasswordsByUserID(user.getUserID());

        assertEquals(1, passwords.size());
        assertEquals(keep.getPasswordID(), passwords.get(0).getPasswordID());
    }




    // ---------- Passwords: failure paths ----------

    @Test
    void updatingAMissingPasswordThrows()
    {
        Password ghost = new Password(9999, user.getUserID(), "Ghost", "x", "x", null, null);

        assertThrows(RecordNotFoundException.class, () -> PasswordTableDML.updatePassword(ghost));
    }




    @Test
    void deletingAMissingPasswordThrows()
    {
        assertThrows(RecordNotFoundException.class, () -> PasswordTableDML.deletePassword(9999, user.getUserID()));
    }




    @Test
    void anotherUserCannotUpdateOrDeleteMyPassword() throws SQLException
    {
        Password mine = newEntry(user);

        PasswordTableDML.insertPassword(mine);

        User other = new User("sam@example.com", "Sam", "Lee", null);

        UserTableDML.insertUser(other);

        // Same password ID, but the other user's ID: must not match any row

        Password hijack = new Password(mine.getPasswordID(), other.getUserID(), "Hacked", "x", "x", null, null);

        assertThrows(RecordNotFoundException.class, () -> PasswordTableDML.updatePassword(hijack));
        assertThrows(RecordNotFoundException.class, () -> PasswordTableDML.deletePassword(mine.getPasswordID(), other.getUserID()));

        // And my entry is untouched

        ArrayList<Password> passwords = PasswordTableDQL.getPasswordsByUserID(user.getUserID());

        assertEquals(1, passwords.size());
        assertEquals("Test website", passwords.get(0).getTitle());
    }




    // ---------- Helpers ----------

    private Password newEntry(User owner)
    {
        return new Password(owner.getUserID(), "Test website", "alex", "FakeTestPassword", "https://example.com", "Test entry");
    }


    // TODO (Step 4): replace these raw queries with UserTableDQL methods

    private boolean userExists(int userID) throws SQLException
    {
        return readFirstName(userID) != null;
    }


    private String readFirstName(int userID) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT FirstName FROM PasswordManagerUsers WHERE PKUserID = ?"))
        {
            statement.setInt(1, userID);

            try (ResultSet results = statement.executeQuery())
            {
                return results.next() ? results.getString("FirstName") : null;
            }
        }
    }

}
