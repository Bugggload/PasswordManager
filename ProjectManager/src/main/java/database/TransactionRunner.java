package database;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs several operations as one all-or-nothing transaction.
 * Pass the provided connection to every call inside; methods without it open their own connection
 * and are NOT part of the transaction.
 */
public final class TransactionRunner
{
    @FunctionalInterface
    public interface SqlWork<T>
    {
        T run(Connection connection) throws SQLException;
    }

    private TransactionRunner()
    {
    }

    public static <T> T inTransaction(SqlWork<T> work) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            connection.setAutoCommit(false);

            try
            {
                T result = work.run(connection);

                connection.commit();

                return result;
            }
            catch (SQLException | RuntimeException e)
            {
                try
                {
                    connection.rollback();
                }
                catch (SQLException rollbackError)
                {
                    e.addSuppressed(rollbackError);
                }

                throw e;
            }
        }
    }
}
