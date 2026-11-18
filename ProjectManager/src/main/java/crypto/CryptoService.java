package crypto;

import exceptions.DecryptionException;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Argon2id key derivation and AES-256-GCM encryption. No database access. */
public final class CryptoService
{
    public static final int KEY_LENGTH_BYTES = 32;
    public static final int SALT_LENGTH_BYTES = 16;
    public static final int NONCE_LENGTH_BYTES = 12;
    public static final int TAG_LENGTH_BITS = 128;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private CryptoService()
    {
    }

    public static byte[] randomBytes(int length)
    {
        if (length <= 0)
        {
            throw new IllegalArgumentException("Length must be greater than 0");
        }

        byte[] bytes = new byte[length];
        SECURE_RANDOM.nextBytes(bytes);

        return bytes;
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

    /** Same password + salt + params always gives the same 32-byte key. */
    public static byte[] deriveKey(char[] password, byte[] salt, KdfParams params)
    {
        if (password == null || password.length == 0)
        {
            throw new IllegalArgumentException("Password cannot be empty or null");
        }

        if (salt == null || salt.length < SALT_LENGTH_BYTES)
        {
            throw new IllegalArgumentException("Salt must be " + SALT_LENGTH_BYTES + " long");
        }

        if (params == null)
        {
            throw new IllegalArgumentException("KDF params are required");
        }

        Argon2Parameters argon2Params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(params.memoryKiB())
            .withIterations(params.iterations())
            .withParallelism(params.parallelism())
            .build();

        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(argon2Params);

        byte[] key = new byte[KEY_LENGTH_BYTES];
        generator.generateBytes(password, key);

        return key;
    }

    /** Uses a fresh random nonce every call. The ciphertext includes the 16-byte tag. */
    public static EncryptedData encrypt(byte[] key, byte[] plaintext, byte[] aad)
    {
        requireValidKey(key);

        if (plaintext == null || aad == null)
        {
            throw new IllegalArgumentException("Plaintext and AAD are required (use an empty array, not null)");
        }

        byte[] nonce = generateNonce();

        try
        {
            return new EncryptedData(nonce, initCipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext));
        }
        catch (GeneralSecurityException e)
        {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    /** Returns the plaintext only if the authentication tag matches. */
    public static byte[] decrypt(byte[] key, EncryptedData data, byte[] aad) throws DecryptionException
    {
        requireValidKey(key);

        if (data == null || aad == null)
        {
            throw new IllegalArgumentException("Encrypted data and AAD are required");
        }

        try
        {
            return initCipher(Cipher.DECRYPT_MODE, key, data.nonce(), aad).doFinal(data.ciphertext());
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

    /** Overwrites a secret with zeros. Null-safe. */
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

    // AAD must be supplied before doFinal
    private static Cipher initCipher(int mode, byte[] key, byte[] nonce, byte[] aad) throws GeneralSecurityException
    {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
        cipher.updateAAD(aad);

        return cipher;
    }

    private static void requireValidKey(byte[] key)
    {
        if (key == null || key.length != KEY_LENGTH_BYTES)
        {
            throw new IllegalArgumentException("Key must be " + KEY_LENGTH_BYTES + " long");
        }
    }
}
