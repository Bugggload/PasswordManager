package crypto;

/** Output of AES-GCM: the nonce (not secret) and the ciphertext with its 16-byte tag appended. */
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
