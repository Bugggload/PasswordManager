package auth;

import crypto.CryptoService;
import crypto.EncryptedData;
import database.dml.PasswordTableDML;
import database.dql.PasswordTableDQL;
import exceptions.DecryptionException;
import exceptions.RecordNotFoundException;
import models.Password;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * The logged-in user's saved passwords. The user ID always comes from the session, never a parameter,
 * so another user's entries are simply "not found". Plaintext passwords are char[]; callers wipe them.
 */
public final class VaultService
{
    public static final int MAX_PASSWORD_LENGTH = 1024;

    private VaultService()
    {
    }

    /** @return the saved entry with its new ID (password encrypted) */
    public static Password addEntry(AuthenticatedSession session, String title, String username,
                                    char[] plaintextPassword, String url, String remarks) throws SQLException
    {
        requireSession(session);

        Password entry = new Password(session.getUserID(), title, username, encryptPassword(session, plaintextPassword), url, remarks);

        PasswordTableDML.insertPassword(entry);

        return entry;
    }

    /** Passwords stay encrypted; use revealPassword to see one. */
    public static List<Password> listEntries(AuthenticatedSession session) throws SQLException
    {
        requireSession(session);

        return PasswordTableDQL.getPasswordsByUserID(session.getUserID());
    }

    /**
     * @return the plaintext; wipe it once shown
     * @throws DecryptionException the stored entry was tampered with or corrupted
     */
    public static char[] revealPassword(AuthenticatedSession session, int passwordID) throws SQLException, DecryptionException
    {
        requireSession(session);

        int userID = session.getUserID();
        Password entry = findOwnEntry(passwordID, userID);

        byte[] plaintextBytes = CryptoService.decrypt(session.vaultKey(), entry.getEncryptedPassword(), passwordAad(userID));

        try
        {
            return utf8ToChars(plaintextBytes);
        }
        finally
        {
            CryptoService.wipe(plaintextBytes);
        }
    }

    /** @param newPassword null keeps the current password; otherwise it's re-encrypted with a fresh nonce */
    public static void updateEntry(AuthenticatedSession session, int passwordID, String title, String username,
                                   char[] newPassword, String url, String remarks) throws SQLException
    {
        requireSession(session);

        int userID = session.getUserID();
        Password existing = findOwnEntry(passwordID, userID);

        EncryptedData encrypted = (newPassword == null)
            ? existing.getEncryptedPassword()
            : encryptPassword(session, newPassword);

        PasswordTableDML.updatePassword(new Password(passwordID, userID, title, username, encrypted, url, remarks));
    }

    public static void deleteEntry(AuthenticatedSession session, int passwordID) throws SQLException
    {
        requireSession(session);

        PasswordTableDML.deletePassword(passwordID, session.getUserID());
    }

    /** Binds a password ciphertext to its owner. Different label from AccountService.vaultKeyAad. */
    public static byte[] passwordAad(int userID)
    {
        return ("password:user:" + userID).getBytes(StandardCharsets.UTF_8);
    }

    private static Password findOwnEntry(int passwordID, int userID) throws SQLException
    {
        return PasswordTableDQL.getPasswordByID(passwordID, userID)
            .orElseThrow(() -> new RecordNotFoundException("No password entry " + passwordID + " for this user"));
    }

    private static EncryptedData encryptPassword(AuthenticatedSession session, char[] plaintextPassword)
    {
        if (plaintextPassword == null || plaintextPassword.length == 0)
        {
            throw new IllegalArgumentException("Password cannot be empty.");
        }

        if (plaintextPassword.length > MAX_PASSWORD_LENGTH)
        {
            throw new IllegalArgumentException("Password must be at most " + MAX_PASSWORD_LENGTH + " characters.");
        }

        byte[] plaintextBytes = charsToUtf8(plaintextPassword);

        try
        {
            return CryptoService.encrypt(session.vaultKey(), plaintextBytes, passwordAad(session.getUserID()));
        }
        finally
        {
            CryptoService.wipe(plaintextBytes);
        }
    }

    private static void requireSession(AuthenticatedSession session)
    {
        if (session == null)
        {
            throw new IllegalArgumentException("A logged-in session is required.");
        }
    }

    // Converts without creating a String (which can't be wiped), and wipes the temporary buffer
    private static byte[] charsToUtf8(char[] chars)
    {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        byte[] bytes = Arrays.copyOfRange(buffer.array(), buffer.position(), buffer.limit());

        Arrays.fill(buffer.array(), (byte) 0);

        return bytes;
    }

    private static char[] utf8ToChars(byte[] bytes)
    {
        CharBuffer buffer = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
        char[] chars = Arrays.copyOfRange(buffer.array(), buffer.position(), buffer.limit());

        Arrays.fill(buffer.array(), '\0');

        return chars;
    }
}
