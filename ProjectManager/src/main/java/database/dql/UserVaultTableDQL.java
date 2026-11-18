package database.dql;

import crypto.EncryptedData;
import crypto.KdfParams;
import database.DatabaseConnection;
import models.UserVault;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Reads from UserVaults. Never decrypts. */
public final class UserVaultTableDQL
{
    private UserVaultTableDQL()
    {
    }

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
