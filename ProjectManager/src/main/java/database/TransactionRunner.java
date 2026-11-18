/*
Runs several database operations as ONE transaction: either all of them are saved, or none are.

Usage:
    int userID = TransactionRunner.inTransaction(connection -> {
        int id = UserTableDML.insertUser(connection, user);      // pass the SAME connection
        PasswordTableDML.insertPassword(connection, entry);      // to every operation
        return id;
    });

If the code inside throws ANY exception, everything it did is rolled back and the
exception is passed on to the caller. If it finishes normally, everything is committed.

Important: inside the lambda, always use the methods that take a Connection.
The versions without a Connection open their own separate connection, which is NOT
part of this transaction.
 */

package database;

import java.sql.Connection;
import java.sql.SQLException;

public final class TransactionRunner
{

    // A piece of database work that receives the shared connection and returns a result.
    // (Like java.util.function.Function, but allowed to throw SQLException.)

    @FunctionalInterface
    public interface SqlWork<T>
    {
        T run(Connection connection) throws SQLException;
    }




    private TransactionRunner()
    {
        // Utility class: no objects
    }




    public static <T> T inTransaction(SqlWork<T> work) throws SQLException
    {
        try (Connection connection = DatabaseConnection.getConnection())
        {
            // Turn off auto-commit: from now on nothing is saved until commit()

            connection.setAutoCommit(false);

            try
            {
                T result = work.run(connection);

                connection.commit();

                return result;
            }
            catch (SQLException | RuntimeException e)
            {
                // Something failed: undo every change made inside this transaction

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
