package models;

import crypto.EncryptedData;
import crypto.KdfParams;

/**
 * A row of UserVaults: what's needed to unlock a user's vault key with their master password.
 * The master password, derived key and plaintext vault key are never stored.
 */
public record UserVault(int userID, byte[] kdfSalt, KdfParams kdfParams, EncryptedData encryptedVaultKey)
{
    public UserVault
    {
        if (userID <= 0)
        {
            throw new IllegalArgumentException("User ID must be positive.");
        }

        if (kdfSalt == null || kdfSalt.length == 0)
        {
            throw new IllegalArgumentException("Salt is required.");
        }

        if (kdfParams == null || encryptedVaultKey == null)
        {
            throw new IllegalArgumentException("KDF params and encrypted vault key are required.");
        }
    }
}
