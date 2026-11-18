/*
Tests for UserVaultTableDML / UserVaultTableDQL: storing and loading a user's vault.
Uses fake bytes: this layer only stores data, it never encrypts.
 */

package database;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import crypto.EncryptedData;
import crypto.KdfParams;
import dataHoldingClasses.User;
import dataHoldingClasses.UserVault;

import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UserVaultTableTest
{

    @TempDir
    Path tempFolder;

    private User user;




    @BeforeEach
    void setUp() throws SQLException
    {
        DatabaseConnection.setDatabasePath(tempFolder.resolve("test.db"));

        user = new User("alex@example.com", "Alex", "Smith", null);

        UserTableDML.insertUser(user);
    }




    @Test
    void insertedVaultReadsBackExactly() throws SQLException
    {
        UserVault vault = fakeVault(user.getUserID(), (byte) 1);

        UserVaultTableDML.insertVault(vault);

        UserVault loaded = UserVaultTableDQL.getVaultByUserID(user.getUserID()).orElseThrow();

        assertEquals(user.getUserID(), loaded.userID());
        assertArrayEquals(vault.kdfSalt(), loaded.kdfSalt());
        assertEquals(vault.kdfParams(), loaded.kdfParams());   // records compare field by field
        assertArrayEquals(vault.encryptedVaultKey().nonce(), loaded.encryptedVaultKey().nonce());
        assertArrayEquals(vault.encryptedVaultKey().ciphertext(), loaded.encryptedVaultKey().ciphertext());
    }




    @Test
    void userWithoutVaultGivesEmpty() throws SQLException
    {
        assertTrue(UserVaultTableDQL.getVaultByUserID(user.getUserID()).isEmpty());
    }




    @Test
    void aUserCannotHaveTwoVaults() throws SQLException
    {
        UserVaultTableDML.insertVault(fakeVault(user.getUserID(), (byte) 1));

        assertThrows(SQLException.class, () -> UserVaultTableDML.insertVault(fakeVault(user.getUserID(), (byte) 2)));
    }




    @Test
    void vaultForMissingUserIsRejected()
    {
        assertThrows(SQLException.class, () -> UserVaultTableDML.insertVault(fakeVault(9999, (byte) 1)));
    }




    // Changing the master password replaces salt, params and the encrypted vault key

    @Test
    void updateVaultReplacesEverything() throws SQLException
    {
        UserVaultTableDML.insertVault(fakeVault(user.getUserID(), (byte) 1));

        UserVault replacement = new UserVault(user.getUserID(), new byte[] { 5, 5, 5, 5 }, new KdfParams(2048, 4, 2),
            new EncryptedData(new byte[] { 6, 6 }, new byte[] { 7, 7, 7 }));

        UserVaultTableDML.updateVault(replacement);

        UserVault loaded = UserVaultTableDQL.getVaultByUserID(user.getUserID()).orElseThrow();

        assertArrayEquals(replacement.kdfSalt(), loaded.kdfSalt());
        assertEquals(replacement.kdfParams(), loaded.kdfParams());
        assertArrayEquals(replacement.encryptedVaultKey().nonce(), loaded.encryptedVaultKey().nonce());
        assertArrayEquals(replacement.encryptedVaultKey().ciphertext(), loaded.encryptedVaultKey().ciphertext());
    }




    @Test
    void updatingMissingVaultThrows()
    {
        assertThrows(RecordNotFoundException.class, () -> UserVaultTableDML.updateVault(fakeVault(user.getUserID(), (byte) 1)));
    }




    @Test
    void deletingUserDeletesTheirVault() throws SQLException
    {
        UserVaultTableDML.insertVault(fakeVault(user.getUserID(), (byte) 1));

        UserTableDML.deleteUser(user.getUserID());

        assertTrue(UserVaultTableDQL.getVaultByUserID(user.getUserID()).isEmpty());
    }




    // 'seed' makes each fake vault's bytes different, so tests can tell them apart

    private UserVault fakeVault(int userID, byte seed)
    {
        byte[] salt = new byte[16];
        java.util.Arrays.fill(salt, seed);

        return new UserVault(userID, salt, new KdfParams(1024, 2, 1),
            new EncryptedData(new byte[] { seed, 0, (byte) 0xFF }, new byte[] { seed, seed, 0, (byte) 0x80 }));
    }

}
