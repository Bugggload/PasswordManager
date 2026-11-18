/*
Thrown when an UPDATE or DELETE matched no row: the ID doesn't exist,
or the record belongs to a different user.

It extends SQLException so every method that already says "throws SQLException"
keeps compiling, but callers can catch this one specifically if they want.
 */

package database;

import java.sql.SQLException;

public class RecordNotFoundException extends SQLException
{
    public RecordNotFoundException(String message)
    {
        super(message);
    }
}
