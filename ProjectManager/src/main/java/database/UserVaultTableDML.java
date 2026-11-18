/*
Writes to the UserVaults table. Pure storage: never encrypts or decrypts anything.

1) insertVault(UserVault vault): Saves a new user's vault (done once, at registration)
2) updateVault(UserVault vault): Replaces salt, KDF params and encrypted vault key
                                 (used when the master password changes)

There is no deleteVault: deleting the user deletes their vault (ON DELETE CASCADE).
updateVault throws RecordNotFoundException if the user has no vault.

Each operation comes in two versions:
  - insertVault(vault)              opens its own connection; saved immediately
  - insertVault(connection, vault)  uses YOUR connection; use inside TransactionRunner.inTransaction
 */

package database;

import dataHoldingClasses.UserVault;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;


public class UserVaultTableDML
{

    // Saving a new vault. Fails (SQLException) if the user already has one or doesn't exist.

    public static void insertVault(UserVault vault) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            insertVault(connection, vault);
        }
    }

    public static void insertVault(Connection connection, UserVault vault) throws SQLException
    {
        String sql = "INSERT INTO UserVaults (FKUserID, KdfSalt, KdfMemoryKiB, KdfIterations, KdfParallelism, VaultKeyNonce, EncryptedVaultKey) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setInt(1, vault.userID());
            statement.setBytes(2, vault.kdfSalt());
            statement.setInt(3, vault.kdfParams().memoryKiB());
            statement.setInt(4, vault.kdfParams().iterations());
            statement.setInt(5, vault.kdfParams().parallelism());
            statement.setBytes(6, vault.encryptedVaultKey().nonce());
            statement.setBytes(7, vault.encryptedVaultKey().ciphertext());

            statement.executeUpdate();
        }
    }




    // Replacing a vault's crypto data (master password change)

    public static void updateVault(UserVault vault) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            updateVault(connection, vault);
        }
    }

    public static void updateVault(Connection connection, UserVault vault) throws SQLException
    {
        String sql = "UPDATE UserVaults SET KdfSalt = ?, KdfMemoryKiB = ?, KdfIterations = ?, KdfParallelism = ?, VaultKeyNonce = ?, EncryptedVaultKey = ? WHERE FKUserID = ?";

        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setBytes(1, vault.kdfSalt());
            statement.setInt(2, vault.kdfParams().memoryKiB());
            statement.setInt(3, vault.kdfParams().iterations());
            statement.setInt(4, vault.kdfParams().parallelism());
            statement.setBytes(5, vault.encryptedVaultKey().nonce());
            statement.setBytes(6, vault.encryptedVaultKey().ciphertext());

            statement.setInt(7, vault.userID());

            int affectedRows = statement.executeUpdate();

            SqlHelpers.requireOneRow(affectedRows, "No vault for user " + vault.userID());
        }
    }

}
