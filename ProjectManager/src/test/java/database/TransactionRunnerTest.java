package database;

import database.dml.PasswordTableDML;
import database.dml.UserTableDML;
import database.dql.PasswordTableDQL;
import database.dql.UserTableDQL;
import models.Password;
import models.User;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


import java.nio.file.Path;
import java.sql.SQLException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransactionRunnerTest
{

    @TempDir
    Path tempFolder;




    @BeforeEach
    void setUp()
    {
        DatabaseConnection.setDatabasePath(tempFolder.resolve("test.db"));
    }




    // Everything succeeds -> everything is saved, and the lambda's return value comes back

    @Test
    void successfulTransactionSavesEverything() throws SQLException
    {
        User user = new User("alex@example.com", "Alex", "Smith", null);

        int userID = TransactionRunner.inTransaction(connection -> {
            int id = UserTableDML.insertUser(connection, user);
            PasswordTableDML.insertPassword(connection, new Password(id, "Bank", "alex", DatabaseCrudTest.FAKE_ENCRYPTED, null, null));
            return id;
        });

        assertEquals(user.getUserID(), userID);
        assertTrue(UserTableDQL.getUserByID(userID).isPresent());
        assertEquals(1, PasswordTableDQL.getPasswordsByUserID(userID).size());
    }




    // Second operation fails (SQL error) -> the first one is undone too

    @Test
    void sqlErrorRollsBackEarlierWork()
    {
        User user = new User("alex@example.com", "Alex", "Smith", null);

        assertThrows(SQLException.class, () -> TransactionRunner.inTransaction(connection -> {
            UserTableDML.insertUser(connection, user);
            UserTableDML.insertUser(connection, new User("alex@example.com", "Dup", "Email", null)); // UNIQUE fails
            return null;
        }));

        assertNoUser("alex@example.com");
    }




    // Java code (not SQL) fails mid-way -> still rolled back

    @Test
    void runtimeErrorRollsBackEarlierWork()
    {
        User user = new User("alex@example.com", "Alex", "Smith", null);

        assertThrows(IllegalStateException.class, () -> TransactionRunner.inTransaction(connection -> {
            UserTableDML.insertUser(connection, user);
            throw new IllegalStateException("Simulated crash after the first insert");
        }));

        assertNoUser("alex@example.com");
    }




    // Using the no-connection version inside a transaction is NOT part of it.
    // This test documents that trap so nobody is surprised by it.

    @Test
    void operationsOnAnotherConnectionAreNotRolledBack() throws SQLException
    {
        assertThrows(IllegalStateException.class, () -> TransactionRunner.inTransaction(connection -> {
            // WRONG on purpose: no 'connection' argument, so this opens and commits its own connection
            UserTableDML.insertUser(new User("leaked@example.com", "Leaked", "User", null));
            throw new IllegalStateException("Simulated crash");
        }));

        // The insert escaped the rollback, which is exactly why you must pass the connection

        assertTrue(UserTableDQL.getUserByEmail("leaked@example.com").isPresent());
    }




    private void assertNoUser(String email)
    {
        try
        {
            assertTrue(UserTableDQL.getUserByEmail(email).isEmpty(), "The insert should have been rolled back");
        }
        catch (SQLException e)
        {
            throw new AssertionError(e);
        }
    }

}
