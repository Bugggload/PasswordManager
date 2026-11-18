/*
Tests for AccountService.register (Step 10).

Run everything:        mvn test -Dtest=AccountServiceTest
Run one group:         mvn test -Dtest='AccountServiceTest$Validation*'

Suggested order: TODO 5 (vaultKeyAad) first since it's tiny, then the register groups.
 */

package auth;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import crypto.CryptoService;
import crypto.DecryptionException;
import crypto.KdfParams;
import dataHoldingClasses.User;
import dataHoldingClasses.UserVault;
import database.DatabaseConnection;
import database.UserTableDQL;
import database.UserVaultTableDQL;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountServiceTest
{

    // Cheap Argon2 settings so tests run fast

    static final KdfParams FAST = new KdfParams(1024, 2, 1);

    @TempDir
    Path tempFolder;




    @BeforeEach
    void setUp()
    {
        DatabaseConnection.setDatabasePath(tempFolder.resolve("test.db"));
    }


    static User newUser(String email)
    {
        return new User(email, "Alex", "Smith", null);
    }


    // Unlocks a stored vault the way login will: re-derive the KEK, decrypt with the same AAD

    static byte[] unlock(UserVault vault, String masterPassword) throws DecryptionException
    {
        byte[] kek = CryptoService.deriveKey(masterPassword.toCharArray(), vault.kdfSalt(), vault.kdfParams());

        return CryptoService.decrypt(kek, vault.encryptedVaultKey(), AccountService.vaultKeyAad(vault.userID()));
    }




    @Nested
    class Todo5_VaultKeyAad
    {
        @Test
        void sameUserGivesSameBytes()
        {
            assertArrayEquals(AccountService.vaultKeyAad(7), AccountService.vaultKeyAad(7));
        }

        @Test
        void differentUsersGiveDifferentBytes()
        {
            assertFalse(Arrays.equals(AccountService.vaultKeyAad(7), AccountService.vaultKeyAad(8)));
        }

        @Test
        void isNotJustTheNumber()
        {
            // A bare "7" could collide with some other AAD that also happens to be "7"
            assertFalse(Arrays.equals("7".getBytes(), AccountService.vaultKeyAad(7)), "Include a label, not just the ID");
        }
    }




    @Nested
    class Validation
    {
        @Test
        void rejectsNullInputs()
        {
            assertThrows(IllegalArgumentException.class, () -> AccountService.register(null, "longenough".toCharArray(), FAST));
            assertThrows(IllegalArgumentException.class, () -> AccountService.register(newUser("a@example.com"), null, FAST));
            assertThrows(IllegalArgumentException.class, () -> AccountService.register(newUser("a@example.com"), "longenough".toCharArray(), null));
        }

        @Test
        void rejectsShortMasterPasswordAndSavesNothing() throws SQLException
        {
            assertThrows(IllegalArgumentException.class, () -> AccountService.register(newUser("a@example.com"), "short".toCharArray(), FAST));

            assertTrue(UserTableDQL.getUserByEmail("a@example.com").isEmpty());
        }
    }




    @Nested
    class Register
    {
        @Test
        void createsUserAndVault() throws SQLException
        {
            User user = newUser("alex@example.com");

            int userID = AccountService.register(user, "correct horse".toCharArray(), FAST);

            assertEquals(userID, user.getUserID(), "The user object should get its new ID");
            assertTrue(UserTableDQL.getUserByID(userID).isPresent());
            assertTrue(UserVaultTableDQL.getVaultByUserID(userID).isPresent());
        }

        @Test
        void vaultHasTheRightShape() throws SQLException
        {
            int userID = AccountService.register(newUser("alex@example.com"), "correct horse".toCharArray(), FAST);

            UserVault vault = UserVaultTableDQL.getVaultByUserID(userID).orElseThrow();

            assertEquals(CryptoService.SALT_LENGTH_BYTES, vault.kdfSalt().length);
            assertEquals(FAST, vault.kdfParams(), "The params used must be stored so login can repeat them");
            assertEquals(CryptoService.NONCE_LENGTH_BYTES, vault.encryptedVaultKey().nonce().length);
            assertEquals(CryptoService.KEY_LENGTH_BYTES + 16, vault.encryptedVaultKey().ciphertext().length, "32-byte vault key + 16-byte tag");
        }

        @Test
        void masterPasswordIsNotWipedByRegister() throws SQLException
        {
            char[] password = "correct horse".toCharArray();

            AccountService.register(newUser("alex@example.com"), password, FAST);

            assertArrayEquals("correct horse".toCharArray(), password, "The caller owns the password array");
        }
    }




    @Nested
    class Unlocking
    {
        @Test
        void rightPasswordUnlocksA32ByteVaultKey() throws Exception
        {
            int userID = AccountService.register(newUser("alex@example.com"), "correct horse".toCharArray(), FAST);

            byte[] vaultKey = unlock(UserVaultTableDQL.getVaultByUserID(userID).orElseThrow(), "correct horse");

            assertEquals(CryptoService.KEY_LENGTH_BYTES, vaultKey.length);
        }

        @Test
        void wrongPasswordCannotUnlock() throws SQLException
        {
            int userID = AccountService.register(newUser("alex@example.com"), "correct horse".toCharArray(), FAST);

            UserVault vault = UserVaultTableDQL.getVaultByUserID(userID).orElseThrow();

            assertThrows(DecryptionException.class, () -> unlock(vault, "correct horsE"));
        }

        @Test
        void vaultIsBoundToItsUser() throws SQLException
        {
            int userID = AccountService.register(newUser("alex@example.com"), "correct horse".toCharArray(), FAST);

            UserVault real = UserVaultTableDQL.getVaultByUserID(userID).orElseThrow();

            // Same bytes, but pretending they belong to a different user: AAD mismatch must fail

            UserVault moved = new UserVault(userID + 1, real.kdfSalt(), real.kdfParams(), real.encryptedVaultKey());

            assertThrows(DecryptionException.class, () -> unlock(moved, "correct horse"));
        }

        @Test
        void samePasswordDifferentUsersGetDifferentSaltsAndKeys() throws Exception
        {
            int a = AccountService.register(newUser("a@example.com"), "same password".toCharArray(), FAST);
            int b = AccountService.register(newUser("b@example.com"), "same password".toCharArray(), FAST);

            UserVault vaultA = UserVaultTableDQL.getVaultByUserID(a).orElseThrow();
            UserVault vaultB = UserVaultTableDQL.getVaultByUserID(b).orElseThrow();

            assertFalse(Arrays.equals(vaultA.kdfSalt(), vaultB.kdfSalt()), "Each user needs their own random salt");
            assertFalse(Arrays.equals(unlock(vaultA, "same password"), unlock(vaultB, "same password")), "Each user needs their own random vault key");
        }
    }




    @Nested
    class AllOrNothing
    {
        @Test
        void duplicateEmailFailsAndLeavesFirstAccountWorking() throws Exception
        {
            int first = AccountService.register(newUser("alex@example.com"), "first password".toCharArray(), FAST);

            assertThrows(SQLException.class, () -> AccountService.register(newUser("alex@example.com"), "second password".toCharArray(), FAST));

            unlock(UserVaultTableDQL.getVaultByUserID(first).orElseThrow(), "first password");   // still unlocks
        }

        @Test
        void ifSavingTheVaultFailsTheUserIsNotSavedEither() throws SQLException
        {
            // Sabotage: remove the vault table so the SECOND insert fails

            try (Connection connection = DatabaseConnection.getConnection();
                 Statement statement = connection.createStatement())
            {
                statement.executeUpdate("DROP TABLE UserVaults");
            }

            assertThrows(SQLException.class, () -> AccountService.register(newUser("alex@example.com"), "correct horse".toCharArray(), FAST));

            assertTrue(UserTableDQL.getUserByEmail("alex@example.com").isEmpty(),
                "User row must be rolled back: did you pass 'connection' to insertUser and run both inserts in one transaction?");
        }
    }

}
