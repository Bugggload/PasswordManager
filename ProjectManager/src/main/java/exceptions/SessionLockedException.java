package exceptions;

/** The session was locked (logout or idle timeout); the user has to log in again. */
public class SessionLockedException extends IllegalStateException
{
    public SessionLockedException()
    {
        super("Session is locked. Please log in again.");
    }
}
