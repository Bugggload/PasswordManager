package crypto;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

public final class CryptoService
{

    public static final int KEY_LENGTH_BYTES = 32;     // 256-bit AES key
    public static final int SALT_LENGTH_BYTES = 16;    // 128-bit Argon2 salt
    public static final int NONCE_LENGTH_BYTES = 12;   // 96-bit GCM nonce (the standard size)
    public static final int TAG_LENGTH_BITS = 128;     // 16-byte GCM authentication tag

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();


    private CryptoService()
    {

    }


    /**
     * Returns {@code length} cryptographically secure random bytes.
     */
    public static byte[] randomBytes(int length)
    {
        if (length <= 0) {
            throw new IllegalArgumentException("Length must be greater than 0");
        }

        byte[] randomBytes = new byte[length];
        SECURE_RANDOM.nextBytes(randomBytes);

        return randomBytes;
    }

    public static byte[] generateSalt()
    {
        return randomBytes(SALT_LENGTH_BYTES);
    }

    public static byte[] generateKey()
    {
        return randomBytes(KEY_LENGTH_BYTES);
    }

    public static byte[] generateNonce()
    {
        return randomBytes(NONCE_LENGTH_BYTES);
    }

    /**
     * Argon2id: turns a master password + salt into a 32-byte key.
     * Same password + same salt + same params -> always the same key.
     */
    public static byte[] deriveKey(char[] password, byte[] salt, KdfParams params)
    {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password cannot be empty or null");
        }
        else if (salt == null || salt.length < SALT_LENGTH_BYTES) {
            throw new IllegalArgumentException("Salt must be " + SALT_LENGTH_BYTES + " long");
        }

        Argon2Parameters argon2Params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(params.memoryKiB())
            .withIterations(params.iterations())
            .withParallelism(params.parallelism())
            .build();
        
        Argon2BytesGenerator argonByteGenerator = new Argon2BytesGenerator();
        argonByteGenerator.init(argon2Params);

        byte[] key = new byte[KEY_LENGTH_BYTES];
        argonByteGenerator.generateBytes(password, key);

        return key;
    }


    /**
     * AES-256-GCM encryption with a brand-new random nonce.
     *
     * @param key       32-byte key
     * @param plaintext bytes to protect
     * @param aad       extra data that is NOT encrypted but must match on decrypt (may be empty, not null)
     */
    public static EncryptedData encrypt(byte[] key, byte[] plaintext, byte[] aad)
    {
        if (key == null || key.length != KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("Key must be " + KEY_LENGTH_BYTES + " long");
        }

        byte[] nonce = generateNonce();
        
        try 
        {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BITS, nonce);

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            cipher.updateAAD(aad);
            byte[] cipherText = cipher.doFinal(plaintext);

            return new EncryptedData(nonce, cipherText);
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /**
     * AES-256-GCM decryption. Returns the plaintext only if the tag checks out.
     *
     * @throws DecryptionException wrong key, tampered data, or wrong AAD
     */
    public static byte[] decrypt(byte[] key, EncryptedData data, byte[] aad) throws DecryptionException
    {
        if (key.length < KEY_LENGTH_BYTES) {
            throw new IllegalArgumentException("Key must be " + KEY_LENGTH_BYTES + " long");
        }

        try 
        {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(TAG_LENGTH_BITS, data.nonce());

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            cipher.updateAAD(aad);
            byte[] plainText = cipher.doFinal(data.ciphertext());

            return plainText;
        }
        catch (AEADBadTagException e)
        {
            throw new DecryptionException("Decryption failed", e);
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    public static void wipe(byte[] secret)
    {
        if (secret != null)
        {
            Arrays.fill(secret, (byte) 0);
        }
    }

    public static void wipe(char[] secret)
    {
        if (secret != null)
        {
            Arrays.fill(secret, '\0');
        }
    }

}
