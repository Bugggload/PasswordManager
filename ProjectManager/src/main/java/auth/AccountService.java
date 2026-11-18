package auth;

import crypto.CryptoService;
import crypto.EncryptedData;
import crypto.KdfParams;
import database.TransactionRunner;
import database.dml.UserTableDML;
import database.dml.UserVaultTableDML;
import database.dql.UserTableDQL;
import database.dql.UserVaultTableDQL;
import exceptions.DecryptionException;
import exceptions.InvalidCredentialsException;
import models.User;
import models.UserVault;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Register, login, change master password and delete account.
 * Callers own (and wipe) the master password arrays they pass in; keys created here are wiped here.
 */
public final class AccountService
{
    public static final int MIN_MASTER_PASSWORD_LENGTH = 8;
    public static final int MAX_MASTER_PASSWORD_LENGTH = 1024;

    // Lets a login for an unknown email take as long as a wrong password, so timing can't reveal accounts
    private static final byte[] DUMMY_SALT = CryptoService.generateSalt();

    private AccountService()
    {
    }

    public static int register(User user, char[] masterPassword) throws SQLException
    {
        return register(user, masterPassword, KdfParams.DEFAULT);
    }

    /**
     * Creates the user and their vault in one transaction.
     * @return the new user's ID
     * @throws SQLException e.g. the email is already registered (nothing is saved)
     */
    public static int register(User user, char[] masterPassword, KdfParams kdfParams) throws SQLException
    {
        if (user == null || masterPassword == null || kdfParams == null)
        {
            throw new IllegalArgumentException("User, master password, and KDF parameters must not be null");
        }

        requireAcceptableMasterPassword(masterPassword);

        byte[] kek = null;
        byte[] vaultKey = null;

        try
        {
            // Argon2id runs before the transaction so the database isn't locked while it works
            byte[] salt = CryptoService.generateSalt();
            kek = CryptoService.deriveKey(masterPassword, salt, kdfParams);
            vaultKey = CryptoService.generateKey();

            byte[] finalKek = kek;
            byte[] finalVaultKey = vaultKey;

            return TransactionRunner.inTransaction(connection -> {
                int userID = UserTableDML.insertUser(connection, user);
                EncryptedData encryptedVaultKey = CryptoService.encrypt(finalKek, finalVaultKey, vaultKeyAad(userID));

                UserVaultTableDML.insertVault(connection, new UserVault(userID, salt, kdfParams, encryptedVaultKey));

                return userID;
            });
        }
        finally
        {
            CryptoService.wipe(kek);
            CryptoService.wipe(vaultKey);
        }
    }

    /**
     * @throws InvalidCredentialsException unknown email or wrong password (deliberately indistinguishable)
     */
    public static AuthenticatedSession login(String email, char[] masterPassword) throws SQLException, InvalidCredentialsException
    {
        return login(email, masterPassword, AuthenticatedSession.DEFAULT_IDLE_TIMEOUT, Clock.systemUTC());
    }

    static AuthenticatedSession login(String email, char[] masterPassword, Duration idleTimeout, Clock clock)
        throws SQLException, InvalidCredentialsException
    {
        if (email == null || email.isBlank() || masterPassword == null || masterPassword.length == 0
            || masterPassword.length > MAX_MASTER_PASSWORD_LENGTH)
        {
            throw new InvalidCredentialsException();
        }

        Optional<UserVault> vault = findVaultByEmail(email);

        if (vault.isEmpty())
        {
            CryptoService.wipe(CryptoService.deriveKey(masterPassword, DUMMY_SALT, KdfParams.DEFAULT));
            throw new InvalidCredentialsException();
        }

        byte[] vaultKey = unlockVaultKey(vault.get(), masterPassword);

        try
        {
            return new AuthenticatedSession(vault.get().userID(), vaultKey, idleTimeout, clock);
        }
        finally
        {
            CryptoService.wipe(vaultKey);
        }
    }

    public static void changeMasterPassword(AuthenticatedSession session, char[] currentPassword, char[] newPassword)
        throws SQLException, InvalidCredentialsException
    {
        changeMasterPassword(session, currentPassword, newPassword, KdfParams.DEFAULT);
    }

    /**
     * Re-encrypts the same vault key under a new master password, so saved entries stay as they are.
     * The current password is required again so an unattended unlocked session can't take over the account.
     */
    public static void changeMasterPassword(AuthenticatedSession session, char[] currentPassword, char[] newPassword, KdfParams kdfParams)
        throws SQLException, InvalidCredentialsException
    {
        if (session == null || currentPassword == null || newPassword == null || kdfParams == null)
        {
            throw new IllegalArgumentException("Session, passwords and KDF parameters are required.");
        }

        requireAcceptableMasterPassword(newPassword);

        int userID = session.getUserID();
        UserVault oldVault = UserVaultTableDQL.getVaultByUserID(userID).orElseThrow(InvalidCredentialsException::new);

        byte[] vaultKey = null;
        byte[] newKek = null;

        try
        {
            vaultKey = unlockVaultKey(oldVault, currentPassword);

            if (!MessageDigest.isEqual(vaultKey, session.vaultKey()))
            {
                throw new InvalidCredentialsException();
            }

            byte[] newSalt = CryptoService.generateSalt();
            newKek = CryptoService.deriveKey(newPassword, newSalt, kdfParams);

            EncryptedData rewrapped = CryptoService.encrypt(newKek, vaultKey, vaultKeyAad(userID));

            UserVaultTableDML.updateVault(new UserVault(userID, newSalt, kdfParams, rewrapped));
        }
        finally
        {
            CryptoService.wipe(vaultKey);
            CryptoService.wipe(newKek);
        }
    }

    /** Permanently deletes the user, their vault and all saved passwords, then locks the session. */
    public static void deleteAccount(AuthenticatedSession session, char[] currentPassword)
        throws SQLException, InvalidCredentialsException
    {
        if (session == null || currentPassword == null)
        {
            throw new IllegalArgumentException("Session and password are required.");
        }

        int userID = session.getUserID();
        UserVault vault = UserVaultTableDQL.getVaultByUserID(userID).orElseThrow(InvalidCredentialsException::new);

        CryptoService.wipe(unlockVaultKey(vault, currentPassword));

        UserTableDML.deleteUser(userID);

        session.lock();
    }

    /** Binds an encrypted vault key to its user. Must be identical everywhere it's used. */
    public static byte[] vaultKeyAad(int userID)
    {
        return ("vault-key:user:" + userID).getBytes(StandardCharsets.UTF_8);
    }

    // A badly formatted email is treated the same as an unknown one
    private static Optional<UserVault> findVaultByEmail(String email) throws SQLException
    {
        Optional<User> user;

        try
        {
            user = UserTableDQL.getUserByEmail(email);
        }
        catch (IllegalArgumentException badlyFormattedEmail)
        {
            return Optional.empty();
        }

        return user.isPresent() ? UserVaultTableDQL.getVaultByUserID(user.get().getUserID()) : Optional.empty();
    }

    // The GCM tag check is the password check: wrong password, tampering and corruption all fail the same way
    private static byte[] unlockVaultKey(UserVault vault, char[] masterPassword) throws InvalidCredentialsException
    {
        byte[] kek = null;

        try
        {
            kek = CryptoService.deriveKey(masterPassword, vault.kdfSalt(), vault.kdfParams());

            return CryptoService.decrypt(kek, vault.encryptedVaultKey(), vaultKeyAad(vault.userID()));
        }
        catch (DecryptionException | IllegalArgumentException e)
        {
            throw new InvalidCredentialsException();
        }
        finally
        {
            CryptoService.wipe(kek);
        }
    }

    private static void requireAcceptableMasterPassword(char[] masterPassword)
    {
        if (masterPassword.length < MIN_MASTER_PASSWORD_LENGTH)
        {
            throw new IllegalArgumentException("Master password must be at least " + MIN_MASTER_PASSWORD_LENGTH + " characters long");
        }

        if (masterPassword.length > MAX_MASTER_PASSWORD_LENGTH)
        {
            throw new IllegalArgumentException("Master password must be at most " + MAX_MASTER_PASSWORD_LENGTH + " characters long");
        }
    }
}
