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
	- *public static int insertPassword(Password entry)*: Adds a new password entry, sets its new ID on the entry and returns it
	- *public static void updatePassword(Password entry)*: Uses password id and user id to update the entry's information
	- *public static void deletePassword(int passwordID, int userID)*: Uses password id and user id to remove one entry
	- update/delete throw *RecordNotFoundException* if the entry doesn't exist or belongs to another user


3) PasswordTableDQL
	- *public static ArrayList<Password> getPasswordsByUserID(int userID)*: Takes in the user id and returns all the password entries associated with the id in an array

5) UserTableDML
	- *public static int insertUser(User user)*: Adds a new user, sets its new ID on the user and returns it
	- *public static void updateUser(User user)*: Uses user id to update the user's information
	- *public static void deleteUser(int userID)*: Uses user id to remove the user. **Also deletes all of that user's password entries** (ON DELETE CASCADE)
	- update/delete throw *RecordNotFoundException* if no user has that ID
      
*Every DML method also has a version that takes a `Connection` first (e.g. `insertUser(connection, user)`), for use inside a transaction.*

6) UserTableDQL
	- *public static Optional<User> getUserByID(int userID)*: Finds a user by ID
	- *public static Optional<User> getUserByEmail(String email)*: Finds a user by email (used for login)
	- Both return *Optional.empty()* when no user matches

7) TransactionRunner
	- *public static <T> T inTransaction(SqlWork<T> work)*: Runs several operations as one all-or-nothing transaction. Pass the given connection to every operation inside it.

8) UserVaultTableDML / UserVaultTableDQL
	- *insertVault(UserVault vault)*: Saves a new user's vault (salt, KDF settings, encrypted vault key)
	- *updateVault(UserVault vault)*: Replaces it (used when the master password changes)
	- *getVaultByUserID(int userID)*: Returns *Optional<UserVault>*
	- Deleting a user deletes their vault automatically

**These classes only store bytes. Encryption/decryption is done by `crypto.CryptoService`, never by the database classes. Passwords are stored as `PasswordCiphertext` + `PasswordNonce`; there is no plaintext password column.**


## 2) dataHoldingClasses
These classes holds all the data we store about a user or password in the respective tables. The variables here are surjective to the columns of the tables. It also has the getters and setters for each variable.

1) Password
	- Variables: passwordID, userID, title, username, password, url, remarks
    - Methods: Getters, setters and a parameterized constructor (*Password(int userID, String title, String username, String password, String url, String remarks)*)

2) User
	- Variables: userID, email, firstName, lastName, Remarks
    - Methods: Getters, setters and a parameterized constructor (*User(String email, String firstName, String lastName, String remarks)*)


## 3) Tests
Tests live in `src/test/java` and use JUnit 5. Each test runs against its own temporary database, so your real data is never touched.

- **Eclipse:** right-click the project (or a test file) → **Run As → JUnit Test**. A green bar means everything passed.
- **Terminal:** from the `ProjectManager` folder run `mvn test`. It ends with `BUILD SUCCESS` and `Tests run: N, Failures: 0, Errors: 0`.

If a test fails, Maven prints which one and why, and the build fails.

## Database location
By default the database is stored at `<your home folder>/.passwordmanager/passwordmanager.db`.
To use a different file, start Java with `-Dpasswordmanager.db=/path/to/file.db`.

