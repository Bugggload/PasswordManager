package models;

/** A row of PasswordManagerUsers. Setters validate and normalize their input. */
public class User
{
    private int userID;
    private String email;
    private String firstName;
    private String lastName;
    private String remarks;

    public User(String email, String firstName, String lastName, String remarks)
    {
        setEmail(email);
        setFirstName(firstName);
        setLastName(lastName);
        setRemarks(remarks);
    }

    public User(int userID, String email, String firstName, String lastName, String remarks)
    {
        this(email, firstName, lastName, remarks);
        setUserID(userID);
    }

    public int getUserID()
    {
        return userID;
    }

    public String getEmail()
    {
        return email;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public String getLastName()
    {
        return lastName;
    }

    public String getRemarks()
    {
        return remarks;
    }

    public void setUserID(int userID)
    {
        if (userID <= 0)
        {
            throw new IllegalArgumentException("User ID must be positive.");
        }

        this.userID = userID;
    }

    /** Stored trimmed and lowercased, so emails are case-insensitive. */
    public void setEmail(String email)
    {
        this.email = FieldRules.normalizeEmail(email);
    }

    public void setFirstName(String firstName)
    {
        this.firstName = FieldRules.requiredText(firstName, "First name", FieldRules.MAX_NAME_LENGTH);
    }

    public void setLastName(String lastName)
    {
        this.lastName = FieldRules.requiredText(lastName, "Last name", FieldRules.MAX_NAME_LENGTH);
    }

    public void setRemarks(String remarks)
    {
        this.remarks = FieldRules.optionalText(remarks, "Remarks", FieldRules.MAX_REMARKS_LENGTH);
    }
}
