package exceptions;

import java.sql.SQLException;

/** An update, delete or lookup matched no row: the ID doesn't exist or belongs to another user. */
public class RecordNotFoundException extends SQLException
{
    public RecordNotFoundException(String message)
    {
        super(message);
    }
}
