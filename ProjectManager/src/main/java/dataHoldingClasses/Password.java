/*
 This class stores all the data we have about a password in password table.
 Each variable here is equivalent to a column in the password table.
 It also has parameterized constructor, getters and setters for each variables.
*/

package dataHoldingClasses;


public class Password
{

    // Columns from the Password table
    private int passwordID;
    private int userID;
    private String title;
    private String username;
    private String password;
    private String url;
    private String remarks;


    
    // Two parameterized constructors
    //When table automatically already sets a password id (usually used)
    public Password(int userID, String title, String username, String password, String url, String remarks)
    {
        setUserID(userID);
        setTitle(title);
        setUsername(username);
        setPassword(password);
        setUrl(url);
        setRemarks(remarks);
    }
    
    //When user id is manually set (Usually to be used when extracting data from the database)
    public Password(int passwordID, int userID, String title, String username, String password, String url, String remarks)
    {
    	setPasswordID(passwordID);
        setUserID(userID);
        setTitle(title);
        setUsername(username);
        setPassword(password);
        setUrl(url);
        setRemarks(remarks);
    }
    
    
    


    // Getters
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

    public String getPassword()
    {
        return password;
    }

    public String getUrl()
    {
        return url;
    }

    public String getRemarks()
    {
        return remarks;
    }




    // Setters
    
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
        if (title == null || title.isBlank())
        {
            throw new IllegalArgumentException("Title cannot be null or blank.");
        }

        this.title = title;
    }

    public void setUsername(String username)
    {
        if (username == null || username.isBlank())
        {
            throw new IllegalArgumentException("Username cannot be null or blank.");
        }

        this.username = username;
    }

    public void setPassword(String password)
    {
        if (password == null || password.isEmpty())
        {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }

        this.password = password;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public void setRemarks(String remarks)
    {
        this.remarks = remarks;
    }

}