package database;

import database.dml.PasswordTableDML;
import database.dml.UserTableDML;
import database.dql.PasswordTableDQL;
import database.dql.UserTableDQL;
import exceptions.RecordNotFoundException;
import models.Password;
import models.User;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import crypto.EncryptedData;

import java.nio.file.Path;
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

    // The database layer only STORES encrypted bytes, so tests use fixed fake bytes.
    // (Real encryption is CryptoService's job and is tested in CryptoServiceTest.)
    // The values include 0x00 and 0xFF to prove arbitrary binary survives the round trip.

    static final EncryptedData FAKE_ENCRYPTED = new EncryptedData(
        new byte[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 },
        new byte[] { (byte) 0xFF, 0x00, 0x7F, (byte) 0x80, 42 });




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

        assertEquals("Jamie", UserTableDQL.getUserByID(user.getUserID()).orElseThrow().getFirstName());
    }




    @Test
    void deleteUserRemovesTheUser() throws SQLException
    {
        UserTableDML.deleteUser(user.getUserID());

        assertTrue(UserTableDQL.getUserByID(user.getUserID()).isEmpty());
    }




    // Deleting a user CASCADES: their passwords go too, other users' passwords stay

    @Test
    void deleteUserAlsoDeletesTheirPasswordsButNoOneElses() throws SQLException
    {
        PasswordTableDML.insertPassword(newEntry(user));
        PasswordTableDML.insertPassword(newEntry(user));

        User other = new User("sam@example.com", "Sam", "Lee", null);

        UserTableDML.insertUser(other);
        PasswordTableDML.insertPassword(newEntry(other));

        UserTableDML.deleteUser(user.getUserID());

        assertTrue(PasswordTableDQL.getPasswordsByUserID(user.getUserID()).isEmpty());
        assertEquals(1, PasswordTableDQL.getPasswordsByUserID(other.getUserID()).size());
    }




    // The foreign key still does its normal job: no passwords for users that don't exist

    @Test
    void cannotInsertPasswordForMissingUser()
    {
        Password orphan = new Password(9999, "Orphan", "x", FAKE_ENCRYPTED, null, null);

        assertThrows(SQLException.class, () -> PasswordTableDML.insertPassword(orphan));
    }




    // ---------- Users: lookups ----------

    @Test
    void getUserByIDReturnsAllFields() throws SQLException
    {
        User found = UserTableDQL.getUserByID(user.getUserID()).orElseThrow();

        assertEquals(user.getUserID(), found.getUserID());
        assertEquals("alex@example.com", found.getEmail());
        assertEquals("Alex", found.getFirstName());
        assertEquals("Smith", found.getLastName());
        assertEquals("Test user", found.getRemarks());
    }




    @Test
    void getUserByEmailFindsTheRightUser() throws SQLException
    {
        UserTableDML.insertUser(new User("sam@example.com", "Sam", "Lee", null));

        User found = UserTableDQL.getUserByEmail("alex@example.com").orElseThrow();

        assertEquals(user.getUserID(), found.getUserID());
        assertEquals("Alex", found.getFirstName());
    }




    @Test
    void lookupsReturnEmptyWhenNoUserMatches() throws SQLException
    {
        assertTrue(UserTableDQL.getUserByID(9999).isEmpty());
        assertTrue(UserTableDQL.getUserByEmail("nobody@example.com").isEmpty());
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
        assertArrayEquals(FAKE_ENCRYPTED.nonce(), saved.getEncryptedPassword().nonce(), "Nonce must come back byte-for-byte");
        assertArrayEquals(FAKE_ENCRYPTED.ciphertext(), saved.getEncryptedPassword().ciphertext(), "Ciphertext must come back byte-for-byte");
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
    void updatePasswordReplacesCiphertextAndNonceTogether() throws SQLException
    {
        Password entry = newEntry(user);

        PasswordTableDML.insertPassword(entry);

        EncryptedData replacement = new EncryptedData(new byte[12], new byte[] { 9, 9, 9 });

        entry.setEncryptedPassword(replacement);

        PasswordTableDML.updatePassword(entry);

        Password saved = PasswordTableDQL.getPasswordsByUserID(user.getUserID()).get(0);

        assertArrayEquals(replacement.nonce(), saved.getEncryptedPassword().nonce());
        assertArrayEquals(replacement.ciphertext(), saved.getEncryptedPassword().ciphertext());
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
        Password ghost = new Password(9999, user.getUserID(), "Ghost", "x", FAKE_ENCRYPTED, null, null);

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

        Password hijack = new Password(mine.getPasswordID(), other.getUserID(), "Hacked", "x", FAKE_ENCRYPTED, null, null);

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
        return new Password(owner.getUserID(), "Test website", "alex", FAKE_ENCRYPTED, "https://example.com", "Test entry");
    }

}
