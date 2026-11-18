/*
Reads from the UserVaults table. Pure storage: never decrypts anything.

1) static Optional<UserVault> getVaultByUserID(int userID): The user's vault, or Optional.empty()
 */

package database;

import crypto.EncryptedData;
import crypto.KdfParams;
import dataHoldingClasses.UserVault;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Optional;


public class UserVaultTableDQL
{

    public static Optional<UserVault> getVaultByUserID(int userID) throws SQLException
    {
        if (userID <= 0)
        {
            throw new IllegalArgumentException("User ID must be positive.");
        }

        String sql = "SELECT * FROM UserVaults WHERE FKUserID = ?";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setInt(1, userID);

            try (ResultSet results = statement.executeQuery())
            {
                if (!results.next())
                {
                    return Optional.empty();
                }

                KdfParams kdfParams = new KdfParams(
                    results.getInt("KdfMemoryKiB"),
                    results.getInt("KdfIterations"),
                    results.getInt("KdfParallelism"));

                EncryptedData encryptedVaultKey = new EncryptedData(
                    results.getBytes("VaultKeyNonce"),
                    results.getBytes("EncryptedVaultKey"));

                return Optional.of(new UserVault(userID, results.getBytes("KdfSalt"), kdfParams, encryptedVaultKey));
            }
        }
    }

}
