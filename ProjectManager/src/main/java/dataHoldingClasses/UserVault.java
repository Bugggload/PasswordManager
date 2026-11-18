/*
 One row of the UserVaults table: everything needed to unlock a user's vault key
 with their master password. Every user has exactly one vault (FKUserID is the primary key).

 userID            - which user this vault belongs to
 kdfSalt           - random Argon2id salt (not secret)
 kdfParams         - Argon2id memory / iterations / parallelism used for THIS user
 encryptedVaultKey - the random vault key, encrypted with the key derived from the master password

 Stored:     all of the above.
 NOT stored: the master password, the derived key (KEK), or the plaintext vault key.
*/

package dataHoldingClasses;

import crypto.EncryptedData;
import crypto.KdfParams;

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
