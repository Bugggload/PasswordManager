package database.dml;

import database.DatabaseConnection;
import models.UserVault;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Writes to UserVaults. Vaults are deleted with their user (ON DELETE CASCADE). */
public final class UserVaultTableDML
{
    private UserVaultTableDML()
    {
    }

    /** Fails with SQLException if the user already has a vault or doesn't exist. */
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

    /** Replaces salt, KDF params and encrypted vault key (master password change). */
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

            DatabaseConnection.requireOneRow(statement.executeUpdate(), "No vault for user " + vault.userID());
        }
    }
}
