/*
Tests for CryptoService, grouped by TODO number. Work through them in order.

Run everything:        mvn test -Dtest=CryptoServiceTest
Run one group, e.g. 2: mvn test -Dtest='CryptoServiceTest$Todo2*'
(Eclipse: expand the JUnit view and right-click a group -> Run)

The "known answer" tests use fixed inputs whose correct outputs were checked against
two independent implementations (Bouncy Castle + argon2-cffi, JDK + Python cryptography).
If those pass, your code is doing the real algorithm with the right settings.
 */

package crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CryptoServiceTest
{

    // Cheap Argon2 settings so tests run fast. Real accounts use KdfParams.DEFAULT.

    static final KdfParams FAST = new KdfParams(1024, 2, 1);

    static final HexFormat HEX = HexFormat.of();

    static byte[] utf8(String s)
    {
        return s.getBytes(StandardCharsets.UTF_8);
    }




    @Nested
    class Todo1_RandomBytes
    {
        @Test
        void returnsTheRequestedLength()
        {
            assertEquals(16, CryptoService.randomBytes(16).length);
            assertEquals(CryptoService.SALT_LENGTH_BYTES, CryptoService.generateSalt().length);
            assertEquals(CryptoService.KEY_LENGTH_BYTES, CryptoService.generateKey().length);
            assertEquals(CryptoService.NONCE_LENGTH_BYTES, CryptoService.generateNonce().length);
        }

        @Test
        void twoCallsGiveDifferentBytes()
        {
            assertFalse(Arrays.equals(CryptoService.randomBytes(32), CryptoService.randomBytes(32)));
        }

        @Test
        void rejectsNonPositiveLength()
        {
            assertThrows(IllegalArgumentException.class, () -> CryptoService.randomBytes(0));
            assertThrows(IllegalArgumentException.class, () -> CryptoService.randomBytes(-1));
        }
    }




    @Nested
    class Todo2_DeriveKey
    {
        static final byte[] SALT = utf8("somesaltsomesalt");   // exactly 16 bytes

        @Test
        void knownAnswer()
        {
            byte[] key = CryptoService.deriveKey("password".toCharArray(), SALT, FAST);

            assertEquals("08a19ee7f6d7f589c2ab6af18d6e724172b19f7d6fd462b38430ab31ceabeaf0", HEX.formatHex(key),
                "Check: Argon2id (not 2i/2d), version 13, 32-byte output, and the params from KdfParams");
        }

        @Test
        void sameInputsGiveSameKey()
        {
            assertArrayEquals(
                CryptoService.deriveKey("hunter2".toCharArray(), SALT, FAST),
                CryptoService.deriveKey("hunter2".toCharArray(), SALT, FAST));
        }

        @Test
        void differentSaltGivesDifferentKey()
        {
            byte[] otherSalt = utf8("anothersaltabcde");

            assertFalse(Arrays.equals(
                CryptoService.deriveKey("hunter2".toCharArray(), SALT, FAST),
                CryptoService.deriveKey("hunter2".toCharArray(), otherSalt, FAST)));
        }

        @Test
        void differentPasswordGivesDifferentKey()
        {
            assertFalse(Arrays.equals(
                CryptoService.deriveKey("hunter2".toCharArray(), SALT, FAST),
                CryptoService.deriveKey("hunter3".toCharArray(), SALT, FAST)));
        }

        @Test
        void rejectsBadInputs()
        {
            assertThrows(IllegalArgumentException.class, () -> CryptoService.deriveKey(null, SALT, FAST));
            assertThrows(IllegalArgumentException.class, () -> CryptoService.deriveKey(new char[0], SALT, FAST));
            assertThrows(IllegalArgumentException.class, () -> CryptoService.deriveKey("pw".toCharArray(), new byte[8], FAST));
            assertThrows(IllegalArgumentException.class, () -> CryptoService.deriveKey("pw".toCharArray(), null, FAST));
        }
    }




    @Nested
    class Todo3_Encrypt
    {
        @Test
        void outputHasNonceAndCiphertextPlusTag()
        {
            byte[] plaintext = utf8("hunter2");

            EncryptedData data = CryptoService.encrypt(CryptoService.generateKey(), plaintext, utf8("aad"));

            assertEquals(CryptoService.NONCE_LENGTH_BYTES, data.nonce().length);
            assertEquals(plaintext.length + CryptoService.TAG_LENGTH_BITS / 8, data.ciphertext().length);
            assertFalse(Arrays.equals(plaintext, Arrays.copyOf(data.ciphertext(), plaintext.length)), "Ciphertext must not contain the plaintext");
        }

        @Test
        void encryptingTheSameThingTwiceGivesDifferentResults()
        {
            byte[] key = CryptoService.generateKey();

            EncryptedData first = CryptoService.encrypt(key, utf8("hunter2"), new byte[0]);
            EncryptedData second = CryptoService.encrypt(key, utf8("hunter2"), new byte[0]);

            assertFalse(Arrays.equals(first.nonce(), second.nonce()), "A fresh nonce is required for every encryption");
            assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext()));
        }

        @Test
        void rejectsWrongKeyLength()
        {
            assertThrows(IllegalArgumentException.class, () -> CryptoService.encrypt(new byte[16], utf8("x"), new byte[0]));
        }
    }




    @Nested
    class Todo4_Decrypt
    {
        // Fixed key 00 01 02 ... 1f and nonce a0 a1 ... ab

        static final byte[] FIXED_KEY = HEX.parseHex("000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f");
        static final byte[] FIXED_NONCE = HEX.parseHex("a0a1a2a3a4a5a6a7a8a9aaab");

        @Test
        void knownAnswer() throws DecryptionException
        {
            EncryptedData data = new EncryptedData(FIXED_NONCE, HEX.parseHex("8e6d125920b9309b43c0c6d9f5f3754e1abb8da0076541"));

            byte[] plaintext = CryptoService.decrypt(FIXED_KEY, data, utf8("user:7"));

            assertEquals("hunter2", new String(plaintext, StandardCharsets.UTF_8),
                "Check: AES/GCM/NoPadding, 128-bit tag, nonce from data.nonce(), AAD fed in");
        }

        @Test
        void roundTrip() throws DecryptionException
        {
            byte[] key = CryptoService.generateKey();
            byte[] aad = utf8("user:1");

            EncryptedData data = CryptoService.encrypt(key, utf8("correct horse battery staple"), aad);

            assertEquals("correct horse battery staple", new String(CryptoService.decrypt(key, data, aad), StandardCharsets.UTF_8));
        }

        @Test
        void emptyPlaintextRoundTrips() throws DecryptionException
        {
            byte[] key = CryptoService.generateKey();

            EncryptedData data = CryptoService.encrypt(key, new byte[0], new byte[0]);

            assertEquals(0, CryptoService.decrypt(key, data, new byte[0]).length);
        }

        @Test
        void wrongKeyFails()
        {
            EncryptedData data = CryptoService.encrypt(CryptoService.generateKey(), utf8("secret"), new byte[0]);

            assertThrows(DecryptionException.class, () -> CryptoService.decrypt(CryptoService.generateKey(), data, new byte[0]));
        }

        @Test
        void tamperedCiphertextFails()
        {
            byte[] key = CryptoService.generateKey();
            EncryptedData data = CryptoService.encrypt(key, utf8("secret"), new byte[0]);

            byte[] tampered = data.ciphertext().clone();
            tampered[0] ^= 1;   // flip one bit

            assertThrows(DecryptionException.class, () -> CryptoService.decrypt(key, new EncryptedData(data.nonce(), tampered), new byte[0]));
        }

        @Test
        void tamperedNonceFails()
        {
            byte[] key = CryptoService.generateKey();
            EncryptedData data = CryptoService.encrypt(key, utf8("secret"), new byte[0]);

            byte[] tampered = data.nonce().clone();
            tampered[0] ^= 1;

            assertThrows(DecryptionException.class, () -> CryptoService.decrypt(key, new EncryptedData(tampered, data.ciphertext()), new byte[0]));
        }

        @Test
        void wrongAadFails()
        {
            byte[] key = CryptoService.generateKey();
            EncryptedData data = CryptoService.encrypt(key, utf8("secret"), utf8("user:1"));

            // Same ciphertext, but claimed to belong to user 2: must be rejected

            assertThrows(DecryptionException.class, () -> CryptoService.decrypt(key, data, utf8("user:2")));
        }
    }




    @Nested
    class Todo5_Wipe
    {
        @Test
        void wipesBytesAndChars()
        {
            byte[] bytes = { 1, 2, 3 };
            char[] chars = { 'a', 'b' };

            CryptoService.wipe(bytes);
            CryptoService.wipe(chars);

            assertArrayEquals(new byte[3], bytes);
            assertArrayEquals(new char[2], chars);
        }

        @Test
        void nullIsIgnored()
        {
            CryptoService.wipe((byte[]) null);
            CryptoService.wipe((char[]) null);
        }
    }




    // When everything above passes, this one shows the whole login idea working end to end

    @Nested
    class Finale_MasterPasswordUnlocksVaultKey
    {
        @Test
        void rightPasswordUnlocksWrongPasswordDoesNot() throws DecryptionException
        {
            // --- Register ---
            byte[] salt = CryptoService.generateSalt();
            byte[] kek = CryptoService.deriveKey("my master password".toCharArray(), salt, FAST);
            byte[] vaultKey = CryptoService.generateKey();
            byte[] aad = utf8("vault:user:1");

            EncryptedData wrappedVaultKey = CryptoService.encrypt(kek, vaultKey, aad);   // this is what gets stored

            // --- Login with the right password ---
            byte[] candidate = CryptoService.deriveKey("my master password".toCharArray(), salt, FAST);

            assertArrayEquals(vaultKey, CryptoService.decrypt(candidate, wrappedVaultKey, aad));

            // --- Login with the wrong password ---
            byte[] wrong = CryptoService.deriveKey("my master passw0rd".toCharArray(), salt, FAST);

            assertThrows(DecryptionException.class, () -> CryptoService.decrypt(wrong, wrappedVaultKey, aad));
        }
    }

}
