# Setting up everything
Before starting everything, we need to set the environment up. For this project, we are using Maven. Maven is a dependency management tool, and using it makes it easier for us to sync and work across multiple computers.
*We are using Java 21 in this project.*

When trying to change the project through eclipse, I got an error saying “Downloading external resources is disabled."
To resolve it:
1) Go to settings
2) Search for XML (Wild Web Developer)
3) Check on Download external resources like referenced DTD, XSD

## How you would set things up in your computer
1) After opening eclipse, go to **File → Import → Maven → Existing Maven Projects.**
2) After clicking ProjectManager, click Finish
3) If you get error that external resources cannot be installed, follow the steps mentioned in *setting up everything*

## When dependencies are missing
1) If any dependencies are missing, **right-click the project → Maven → Update Project → OK**



# Packages

## 1) database
It handles the database side of the program.
**Available methods**
1) database.DataConnection
  - *public static Connection getConnection()*: Connects to the database and creating the tables if not present

2) PasswordTableDML
	- *public void insertPassword(Password entry)*: Adds a new password entry in the database
	- *public void updatePassword(Password entry)*: Uses password id and user id to update the entry's information
	- *public void removePassword(Password entry)*: Uses password id to user id remove the entry's information


3) PasswordTableDQL
	- *public static ArrayList<Password> getPasswordsByUserID(int userID)*: Takes in the user id and returns all the password entries associated with the id in an array

5) UserTableDML
	- *public void insertUser(User user)*: Adds a new user in the database
	- *public void updateUser(User user)*: Uses user id to update the user's information
	- *public void removeUser(User user)*: Uses user id to remove the user's information
      
6) UserTableDQL
	- None for now

**UserTableDQL and PasswordTableDQL have no methods for now**

## 2) dataHoldingClasses
These classes holds all the data we store about a user or password in the respective tables. The variables here are surjective to the columns of the tables. It also has the getters and setters for each variable.

1) Password
	- Variables: passwordID, userID, title, username, password, url, remarks
    - Methods: Getters, setters and a parameterized constructor (*Password(int userID, String title, String username, String password, String url, String remarks)*)

2) User
	- Variables: userID, email, firstName, lastName, Remarks
    - Methods: Getters, setters and a parameterized constructor (*User(String email, String firstName, String lastName, String remarks)*)


## 3)test
To test if the setup is working. Just run Test.java to see everything is running fine. If it is, the output would be:
PASS: Insert user.
PASS: Update user.
PASS: Insert and read password entry.
PASS: Update password entry.
PASS: Delete password entry.
PASS: Delete user.
All tests passed!

