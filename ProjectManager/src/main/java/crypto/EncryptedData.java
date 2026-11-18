/*
The result of CryptoService.encrypt, and the input to CryptoService.decrypt.

nonce      - the 12 random bytes used for this one encryption. Not secret; must be stored.
ciphertext - the encrypted bytes WITH the 16-byte authentication tag on the end
             (that's how Java's AES/GCM returns it), so it's plaintext length + 16.

Both go into the database (Step 9) as BLOB columns.
 */

package crypto;

public record EncryptedData(byte[] nonce, byte[] ciphertext)
{
    public EncryptedData
    {
        if (nonce == null || ciphertext == null)
        {
            throw new IllegalArgumentException("Nonce and ciphertext are required.");
        }
    }
}
