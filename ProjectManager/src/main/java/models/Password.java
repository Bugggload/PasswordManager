package models;

import crypto.EncryptedData;

/** A row of PasswordManagerPasswords. The password is only ever held encrypted. */
public class Password
{
    private int passwordID;
    private int userID;
    private String title;
    private String username;
    private EncryptedData encryptedPassword;
    private String url;
    private String remarks;

    public Password(int userID, String title, String username, EncryptedData encryptedPassword, String url, String remarks)
    {
        setUserID(userID);
        setTitle(title);
        setUsername(username);
        setEncryptedPassword(encryptedPassword);
        setUrl(url);
        setRemarks(remarks);
    }

    public Password(int passwordID, int userID, String title, String username, EncryptedData encryptedPassword, String url, String remarks)
    {
        this(userID, title, username, encryptedPassword, url, remarks);
        setPasswordID(passwordID);
    }

    public int getPasswordID()
    {
        return passwordID;
    }

    public int getUserID()
    {
        return userID;
    }

    public String getTitle()
    {
        return title;
    }

    public String getUsername()
    {
        return username;
    }

    public EncryptedData getEncryptedPassword()
    {
        return encryptedPassword;
    }

    public String getUrl()
    {
        return url;
    }

    public String getRemarks()
    {
        return remarks;
    }

    public void setPasswordID(int passwordID)
    {
        if (passwordID <= 0)
        {
            throw new IllegalArgumentException("Password ID must be positive.");
        }

        this.passwordID = passwordID;
    }

    public void setUserID(int userID)
    {
        if (userID <= 0)
        {
            throw new IllegalArgumentException("User ID must be positive.");
        }

        this.userID = userID;
    }

    public void setTitle(String title)
    {
        this.title = FieldRules.requiredText(title, "Title", FieldRules.MAX_TITLE_LENGTH);
    }

    public void setUsername(String username)
    {
        this.username = FieldRules.requiredText(username, "Username", FieldRules.MAX_USERNAME_LENGTH);
    }

    public void setEncryptedPassword(EncryptedData encryptedPassword)
    {
        if (encryptedPassword == null)
        {
            throw new IllegalArgumentException("Encrypted password cannot be null.");
        }

        this.encryptedPassword = encryptedPassword;
    }

    public void setUrl(String url)
    {
        this.url = FieldRules.optionalUrl(url);
    }

    public void setRemarks(String remarks)
    {
        this.remarks = FieldRules.optionalText(remarks, "Remarks", FieldRules.MAX_REMARKS_LENGTH);
    }
}
