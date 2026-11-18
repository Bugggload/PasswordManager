package exceptions;

/** Decryption failed its authentication check: wrong key, tampered data or wrong AAD. */
public class DecryptionException extends Exception
{
    public DecryptionException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
