package auth;

import database.dql.PasswordTableDQL;
import exceptions.DecryptionException;
import exceptions.InvalidCredentialsException;
import exceptions.RecordNotFoundException;
import models.Password;
import models.User;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import crypto.KdfParams;
import database.DatabaseConnection;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VaultServiceTest
{

    static final KdfParams FAST = new KdfParams(1024, 2, 1);

    @TempDir
    Path tempFolder;

    AuthenticatedSession alex;
    AuthenticatedSession sam;




    @BeforeEach
    void setUp() throws Exception
    {
        DatabaseConnection.setDatabasePath(tempFolder.resolve("test.db"));

        AccountService.register(new User("alex@example.com", "Alex", "Smith", null), "alex master pw".toCharArray(), FAST);
        AccountService.register(new User("sam@example.com", "Sam", "Lee", null), "sam master pw!".toCharArray(), FAST);

        alex = AccountService.login("alex@example.com", "alex master pw".toCharArray());
        sam = AccountService.login("sam@example.com", "sam master pw!".toCharArray());
    }


    @AfterEach
    void tearDown()
    {
        alex.lock();
        sam.lock();
    }




    @Nested
    class BasicUse
    {
        @Test
        void addThenRevealGivesBackThePassword() throws Exception
        {
            Password saved = VaultService.addEntry(alex, "Bank", "alex", "hunter2 ✓ ünïcode".toCharArray(), "https://bank.example", "main account");

            assertTrue(saved.getPasswordID() > 0);
            assertArrayEquals("hunter2 ✓ ünïcode".toCharArray(), VaultService.revealPassword(alex, saved.getPasswordID()));
        }

        @Test
        void passwordIsNotStoredInPlaintext() throws Exception
        {
            Password saved = VaultService.addEntry(alex, "Bank", "alex", "hunter2-plaintext".toCharArray(), null, null);

            byte[] stored = saved.getEncryptedPassword().ciphertext();

            assertFalse(new String(stored, StandardCharsets.ISO_8859_1).contains("hunter2"), "Ciphertext must not contain the plaintext");
        }

        @Test
        void listShowsOnlyMyEntriesStillEncrypted() throws Exception
        {
            VaultService.addEntry(alex, "Bank", "alex", "one".toCharArray(), null, null);
            VaultService.addEntry(alex, "Email", "alex", "two".toCharArray(), null, null);
            VaultService.addEntry(sam, "Sam's", "sam", "three".toCharArray(), null, null);

            List<Password> mine = VaultService.listEntries(alex);

            assertEquals(2, mine.size());
            assertEquals("Bank", mine.get(0).getTitle());
            assertTrue(mine.stream().allMatch(p -> p.getUserID() == alex.getUserID()));
        }

        @Test
        void updateWithNewPasswordReEncryptsWithFreshNonce() throws Exception
        {
            Password saved = VaultService.addEntry(alex, "Bank", "alex", "old".toCharArray(), null, null);
            byte[] oldNonce = saved.getEncryptedPassword().nonce();

            VaultService.updateEntry(alex, saved.getPasswordID(), "Bank (new)", "alex", "new password".toCharArray(), null, null);

            Password reloaded = PasswordTableDQL.getPasswordByID(saved.getPasswordID(), alex.getUserID()).orElseThrow();

            assertEquals("Bank (new)", reloaded.getTitle());
            assertFalse(Arrays.equals(oldNonce, reloaded.getEncryptedPassword().nonce()), "A changed password needs a new nonce");
            assertArrayEquals("new password".toCharArray(), VaultService.revealPassword(alex, saved.getPasswordID()));
        }

        @Test
        void updateWithNullPasswordKeepsTheOldOne() throws Exception
        {
            Password saved = VaultService.addEntry(alex, "Bank", "alex", "keep me".toCharArray(), null, null);

            VaultService.updateEntry(alex, saved.getPasswordID(), "Renamed", "alex2", null, "https://x.example", null);

            assertArrayEquals("keep me".toCharArray(), VaultService.revealPassword(alex, saved.getPasswordID()));
        }

        @Test
        void deleteRemovesOnlyThatEntry() throws Exception
        {
            Password keep = VaultService.addEntry(alex, "Keep", "alex", "a".toCharArray(), null, null);
            Password gone = VaultService.addEntry(alex, "Gone", "alex", "b".toCharArray(), null, null);

            VaultService.deleteEntry(alex, gone.getPasswordID());

            List<Password> left = VaultService.listEntries(alex);

            assertEquals(1, left.size());
            assertEquals(keep.getPasswordID(), left.get(0).getPasswordID());
        }

        @Test
        void emptyOrHugePasswordsAreRejected()
        {
            assertThrows(IllegalArgumentException.class, () -> VaultService.addEntry(alex, "T", "u", new char[0], null, null));
            assertThrows(IllegalArgumentException.class, () -> VaultService.addEntry(alex, "T", "u", null, null, null));
            assertThrows(IllegalArgumentException.class,
                () -> VaultService.addEntry(alex, "T", "u", new char[VaultService.MAX_PASSWORD_LENGTH + 1], null, null));
        }
    }




    // Edge case: one user must never be able to touch another user's entries

    @Nested
    class Isolation
    {
        @Test
        void anotherUserCannotRevealUpdateOrDeleteMyEntry() throws Exception
        {
            Password mine = VaultService.addEntry(alex, "Bank", "alex", "secret".toCharArray(), null, null);
            int id = mine.getPasswordID();

            assertThrows(RecordNotFoundException.class, () -> VaultService.revealPassword(sam, id));
            assertThrows(RecordNotFoundException.class, () -> VaultService.updateEntry(sam, id, "Hacked", "sam", "x".toCharArray(), null, null));
            assertThrows(RecordNotFoundException.class, () -> VaultService.deleteEntry(sam, id));
            assertTrue(VaultService.listEntries(sam).isEmpty());

            assertArrayEquals("secret".toCharArray(), VaultService.revealPassword(alex, id), "My entry is untouched");
        }
    }




    // Edge case: changes made directly to the database file must be detected, never silently decrypted

    @Nested
    class Tampering
    {
        @Test
        void flippedCiphertextBitIsDetected() throws Exception
        {
            Password saved = VaultService.addEntry(alex, "Bank", "alex", "secret".toCharArray(), null, null);

            byte[] tampered = saved.getEncryptedPassword().ciphertext().clone();
            tampered[0] ^= 1;

            setColumn("PasswordCiphertext", tampered, saved.getPasswordID());

            assertThrows(DecryptionException.class, () -> VaultService.revealPassword(alex, saved.getPasswordID()));
        }

        @Test
        void ciphertextCopiedFromAnotherUserDoesNotDecrypt() throws Exception
        {
            Password samsEntry = VaultService.addEntry(sam, "Sam's bank", "sam", "sam-secret".toCharArray(), null, null);
            Password alexEntry = VaultService.addEntry(alex, "Bank", "alex", "alex-secret".toCharArray(), null, null);

            // Attacker with file access copies Sam's encrypted password into Alex's row

            setColumn("PasswordCiphertext", samsEntry.getEncryptedPassword().ciphertext(), alexEntry.getPasswordID());
            setColumn("PasswordNonce", samsEntry.getEncryptedPassword().nonce(), alexEntry.getPasswordID());

            assertThrows(DecryptionException.class, () -> VaultService.revealPassword(alex, alexEntry.getPasswordID()));
        }

        @Test
        void tamperedVaultMeansLoginFailsGenerically() throws Exception
        {
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                     "UPDATE UserVaults SET EncryptedVaultKey = zeroblob(48) WHERE FKUserID = ?"))
            {
                statement.setInt(1, alex.getUserID());
                statement.executeUpdate();
            }

            assertThrows(InvalidCredentialsException.class, () -> AccountService.login("alex@example.com", "alex master pw".toCharArray()));
        }

        private void setColumn(String column, byte[] value, int passwordID) throws SQLException
        {
            // column comes from the test itself, never from user input
            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                     "UPDATE PasswordManagerPasswords SET " + column + " = ? WHERE PKPasswordID = ?"))
            {
                statement.setBytes(1, value);
                statement.setInt(2, passwordID);
                statement.executeUpdate();
            }
        }
    }




    @Nested
    class FieldValidation
    {
        @Test
        void blankUrlBecomesNull() throws Exception
        {
            Password saved = VaultService.addEntry(alex, "Bank", "alex", "pw".toCharArray(), "   ", null);

            assertNull(PasswordTableDQL.getPasswordByID(saved.getPasswordID(), alex.getUserID()).orElseThrow().getUrl());
        }

        @Test
        void badUrlIsRejected()
        {
            assertThrows(IllegalArgumentException.class, () -> VaultService.addEntry(alex, "Bank", "alex", "pw".toCharArray(), "javascript:alert(1)", null));
        }
    }

}
