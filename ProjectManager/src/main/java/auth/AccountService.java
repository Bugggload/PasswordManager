package auth;

import crypto.KdfParams;
import dataHoldingClasses.User;
import crypto.CryptoService;
import crypto.EncryptedData;
import dataHoldingClasses.UserVault;
import database.TransactionRunner;
import database.UserTableDML;
import database.UserVaultTableDML;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;

public final class AccountService
{
    public static final int MIN_MASTER_PASSWORD_LENGTH = 8;

    private AccountService()
    {

    }


    public static int register(User user, char[] masterPassword) throws SQLException
    {
        return register(user, masterPassword, KdfParams.DEFAULT);
    }


    public static int register(User user, char[] masterPassword, KdfParams kdfParams) throws SQLException
    {
        if (user == null || masterPassword == null || kdfParams == null) {
            throw new IllegalArgumentException("User, master password, and KDF parameters must not be null");
        }
        if (masterPassword.length < MIN_MASTER_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Master password must be at least " + MIN_MASTER_PASSWORD_LENGTH + " characters long");
        }

        byte[] kek = null;
        byte[] vaultKey = null;

        try
        {
            byte[] salt = CryptoService.generateSalt();
            kek = CryptoService.deriveKey(masterPassword, salt, kdfParams);
            vaultKey = CryptoService.generateKey();

            byte[] finalKek = kek;
            byte[] finalVaultKey = vaultKey;

            return TransactionRunner.inTransaction(connection -> {
                int userID = UserTableDML.insertUser(connection, user);
                EncryptedData encryptedVaultKey = CryptoService.encrypt(finalKek, finalVaultKey, vaultKeyAad(userID));
                
                UserVaultTableDML.insertVault(connection, new UserVault(userID, salt, kdfParams, encryptedVaultKey));

                return userID;
            });

        } 
        finally
        {
            CryptoService.wipe(kek);
            CryptoService.wipe(vaultKey);
        }
    }
    
    public static byte[] vaultKeyAad(int userID)
    {
        return ("vault-key:user:" + userID).getBytes(StandardCharsets.UTF_8);
    }

}
