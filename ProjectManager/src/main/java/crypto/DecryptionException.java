/*
Thrown when decryption fails its authentication check.

That can mean ANY of: wrong key (e.g. wrong master password), tampered ciphertext,
tampered nonce, wrong AAD, or a corrupted database. We deliberately don't say which:
telling an attacker *why* it failed gives them information.

It's a checked exception (extends Exception) so callers are forced to handle it.
 */

package crypto;

public class DecryptionException extends Exception
{
    public DecryptionException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
