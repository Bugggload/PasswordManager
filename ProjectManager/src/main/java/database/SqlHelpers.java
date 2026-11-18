/*
Small helpers shared by the DML classes. Package-private: only code in "database" can use them.

1) readGeneratedID(statement): after an INSERT, returns the new row's AUTOINCREMENT ID
2) requireOneRow(affectedRows, message): makes UPDATE/DELETE fail loudly if nothing changed
 */

package database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

final class SqlHelpers
{
    private SqlHelpers()
    {
        // Utility class: no objects
    }




    // The statement must have been created with Statement.RETURN_GENERATED_KEYS

    static int readGeneratedID(PreparedStatement statement) throws SQLException
    {
        try (ResultSet keys = statement.getGeneratedKeys())
        {
            if (!keys.next())
            {
                throw new SQLException("Insert succeeded but no ID was returned.");
            }

            return keys.getInt(1);
        }
    }




    // executeUpdate() returns how many rows were changed. For "by ID" updates/deletes that must be exactly 1.

    static void requireOneRow(int affectedRows, String message) throws RecordNotFoundException
    {
        if (affectedRows != 1)
        {
            throw new RecordNotFoundException(message);
        }
    }
}
