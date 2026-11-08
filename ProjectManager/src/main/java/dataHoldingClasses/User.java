/*
 This class stores all the data we have about a user in user table.
 Each variable here is equivalent to a column in the user table.
 It also has parameterized constructor, getters and setters for each variables.
*/

package dataHoldingClasses;

public class User {
	
	
	private int userID;
	private String email;
	private String firstName;
	private String lastName;
	private String remarks;
	
	
	// Two parameterized constructors
    //When table automatically already sets a user id (usually used)
    public User(String email, String firstName, String lastName, String remarks)
    {
        setEmail(email);
        setFirstName(firstName);
        setLastName(lastName);
        setRemarks(remarks);
    }
    
    //When user id is manually set (Usually to be used when extracting data from the database)
    public User(int userID, String email, String firstName, String lastName, String remarks)
    {
    	setUserID(userID);
        setEmail(email);
        setFirstName(firstName);
        setLastName(lastName);
        setRemarks(remarks);
    }
	
	//Getters
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
	
	
	
	
	//Setters
	public void setUserID(int userID)
	{
	    if (userID <= 0)
	    {
	        throw new IllegalArgumentException("User ID must be positive.");
	    }

	    this.userID = userID;
	}
	
	
	
	public void setEmail(String email) 
	{
		
	    if (email == null || email.isBlank()) 
	    {
	        throw new IllegalArgumentException("Email cannot be null or empty.");
	    }
	    
	    this.email = email;
	}
	
	

	public void setFirstName(String firstName) 
	{
	    
		if (firstName == null || firstName.isBlank()) 
		{
	        throw new IllegalArgumentException("First name cannot be null or empty.");
	    }
		
	    this.firstName = firstName;
	}

	
	public void setLastName(String lastName) 
	{
		
	    if (lastName == null || lastName.isBlank()) 
	    {
	        throw new IllegalArgumentException("Last name cannot be null or empty.");
	    }
	    this.lastName = lastName;
	}

	public void setRemarks(String remark) 
	{
	    this.remarks = remark;
	}
	
	
	
	
	

}
