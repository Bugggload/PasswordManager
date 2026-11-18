package exceptions;

/** Wrong email or master password. The message is identical for both so it can't reveal which accounts exist. */
public class InvalidCredentialsException extends Exception
{
    public static final String MESSAGE = "Invalid email or password.";

    public InvalidCredentialsException()
    {
        super(MESSAGE);
    }
}
