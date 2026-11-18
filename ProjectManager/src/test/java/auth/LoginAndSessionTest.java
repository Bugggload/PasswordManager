package auth;

import database.dql.PasswordTableDQL;
import database.dql.UserTableDQL;
import database.dql.UserVaultTableDQL;
import exceptions.InvalidCredentialsException;
import exceptions.SessionLockedException;
import models.User;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import crypto.KdfParams;
import database.DatabaseConnection;

import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoginAndSessionTest
{

    static final KdfParams FAST = new KdfParams(1024, 2, 1);
    static final String EMAIL = "alex@example.com";
    static final String PASSWORD = "correct horse battery";

    @TempDir
    Path tempFolder;

    int userID;




    @BeforeEach
    void setUp() throws SQLException
    {
        DatabaseConnection.setDatabasePath(tempFolder.resolve("test.db"));

        userID = AccountService.register(new User(EMAIL, "Alex", "Smith", null), PASSWORD.toCharArray(), FAST);
    }




    @Nested
    class Login
    {
        @Test
        void rightPasswordGivesSessionForThatUser() throws Exception
        {
            try (AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray()))
            {
                assertEquals(userID, session.getUserID());
                assertFalse(session.isLocked());
            }
        }

        @Test
        void wrongPasswordIsRejected()
        {
            assertThrows(InvalidCredentialsException.class, () -> AccountService.login(EMAIL, "correct horse batterY".toCharArray()));
        }

        @Test
        void unknownEmailAndWrongPasswordGiveTheSameMessage()
        {
            var unknown = assertThrows(InvalidCredentialsException.class, () -> AccountService.login("nobody@example.com", PASSWORD.toCharArray()));
            var wrong = assertThrows(InvalidCredentialsException.class, () -> AccountService.login(EMAIL, "wrong password!".toCharArray()));

            assertEquals(unknown.getMessage(), wrong.getMessage(), "Must not reveal which emails have accounts");
        }

        @Test
        void badInputsAreJustInvalidCredentials()
        {
            assertThrows(InvalidCredentialsException.class, () -> AccountService.login(null, PASSWORD.toCharArray()));
            assertThrows(InvalidCredentialsException.class, () -> AccountService.login("not-an-email", PASSWORD.toCharArray()));
            assertThrows(InvalidCredentialsException.class, () -> AccountService.login(EMAIL, null));
            assertThrows(InvalidCredentialsException.class, () -> AccountService.login(EMAIL, new char[0]));
        }

        @Test
        void emailIsCaseInsensitive() throws Exception
        {
            try (AuthenticatedSession session = AccountService.login("  Alex@EXAMPLE.com ", PASSWORD.toCharArray()))
            {
                assertEquals(userID, session.getUserID());
            }

            assertThrows(SQLException.class,
                () -> AccountService.register(new User("ALEX@example.com", "Other", "Person", null), "another password".toCharArray(), FAST),
                "Same email in different case must count as a duplicate");
        }

        @Test
        void loginWorksAfterReopeningTheDatabase() throws Exception
        {
            Path file = DatabaseConnection.getDatabasePath();

            DatabaseConnection.setDatabasePath(tempFolder.resolve("somewhere-else.db"));
            DatabaseConnection.setDatabasePath(file);   // "restart" pointing at the same file

            try (AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray()))
            {
                assertEquals(userID, session.getUserID());
            }
        }

        @Test
        void loginDoesNotWipeTheCallersPassword() throws Exception
        {
            char[] password = PASSWORD.toCharArray();

            AccountService.login(EMAIL, password).close();

            assertArrayEquals(PASSWORD.toCharArray(), password);
        }
    }




    @Nested
    class LockAndTimeout
    {
        @Test
        void lockWipesTheKeyAndBlocksUse() throws Exception
        {
            AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray());

            byte[] key = session.vaultKey();   // the live array inside the session

            session.lock();

            assertTrue(session.isLocked());
            assertArrayEquals(new byte[key.length], key, "Key bytes must be zeroed");
            assertThrows(SessionLockedException.class, session::getUserID);
            assertThrows(SessionLockedException.class, () -> VaultService.listEntries(session));
        }

        @Test
        void tryWithResourcesLocksAutomatically() throws Exception
        {
            AuthenticatedSession outside;

            try (AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray()))
            {
                outside = session;
            }

            assertTrue(outside.isLocked());
        }

        @Test
        void idleSessionLocksItself() throws Exception
        {
            MutableClock clock = new MutableClock();

            AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray(), Duration.ofMinutes(10), clock);

            clock.advance(Duration.ofMinutes(9));
            assertFalse(session.isLocked(), "Still within the timeout");

            session.getUserID();                  // using it resets the idle timer
            clock.advance(Duration.ofMinutes(9));
            assertFalse(session.isLocked());

            clock.advance(Duration.ofMinutes(2)); // 11 minutes idle
            assertTrue(session.isLocked());
            assertThrows(SessionLockedException.class, () -> VaultService.listEntries(session));
        }

        @Test
        void toStringNeverShowsTheKey() throws Exception
        {
            try (AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray()))
            {
                assertFalse(session.toString().contains("vaultKey"));
                assertFalse(session.toString().contains("[B@"), "No raw byte arrays");
            }
        }
    }




    @Nested
    class ChangeMasterPassword
    {
        @Test
        void newPasswordWorksOldDoesNotAndEntriesSurvive() throws Exception
        {
            int entryID;

            try (AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray()))
            {
                entryID = VaultService.addEntry(session, "Bank", "alex", "bank-secret".toCharArray(), null, null).getPasswordID();

                AccountService.changeMasterPassword(session, PASSWORD.toCharArray(), "brand new password".toCharArray(), FAST);
            }

            assertThrows(InvalidCredentialsException.class, () -> AccountService.login(EMAIL, PASSWORD.toCharArray()));

            try (AuthenticatedSession session = AccountService.login(EMAIL, "brand new password".toCharArray()))
            {
                assertArrayEquals("bank-secret".toCharArray(), VaultService.revealPassword(session, entryID),
                    "Same vault key, so old entries still decrypt without re-encryption");
            }
        }

        @Test
        void wrongCurrentPasswordChangesNothing() throws Exception
        {
            try (AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray()))
            {
                assertThrows(InvalidCredentialsException.class,
                    () -> AccountService.changeMasterPassword(session, "not my password".toCharArray(), "brand new password".toCharArray(), FAST));
            }

            AccountService.login(EMAIL, PASSWORD.toCharArray()).close();   // old password still works
        }

        @Test
        void newPasswordMustBeLongEnough() throws Exception
        {
            try (AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray()))
            {
                assertThrows(IllegalArgumentException.class,
                    () -> AccountService.changeMasterPassword(session, PASSWORD.toCharArray(), "short".toCharArray(), FAST));
            }
        }

        @Test
        void newSaltIsGenerated() throws Exception
        {
            byte[] oldSalt = UserVaultTableDQL.getVaultByUserID(userID).orElseThrow().kdfSalt();

            try (AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray()))
            {
                AccountService.changeMasterPassword(session, PASSWORD.toCharArray(), "brand new password".toCharArray(), FAST);
            }

            assertFalse(java.util.Arrays.equals(oldSalt, UserVaultTableDQL.getVaultByUserID(userID).orElseThrow().kdfSalt()));
        }
    }




    @Nested
    class DeleteAccount
    {
        @Test
        void wrongPasswordDeletesNothing() throws Exception
        {
            try (AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray()))
            {
                assertThrows(InvalidCredentialsException.class, () -> AccountService.deleteAccount(session, "not my password".toCharArray()));
            }

            assertTrue(UserTableDQL.getUserByID(userID).isPresent());
        }

        @Test
        void rightPasswordDeletesEverythingAndLocks() throws Exception
        {
            AuthenticatedSession session = AccountService.login(EMAIL, PASSWORD.toCharArray());

            VaultService.addEntry(session, "Bank", "alex", "bank-secret".toCharArray(), null, null);

            AccountService.deleteAccount(session, PASSWORD.toCharArray());

            assertTrue(session.isLocked());
            assertTrue(UserTableDQL.getUserByID(userID).isEmpty());
            assertTrue(UserVaultTableDQL.getVaultByUserID(userID).isEmpty());
            assertTrue(PasswordTableDQL.getPasswordsByUserID(userID).isEmpty());
            assertThrows(InvalidCredentialsException.class, () -> AccountService.login(EMAIL, PASSWORD.toCharArray()));
        }
    }




    // A clock the test can move forward by hand

    static final class MutableClock extends Clock
    {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration)
        {
            now = now.plus(duration);
        }

        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
    }

}
