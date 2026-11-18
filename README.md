# Setting up everything
Before starting everything, we need to set the environment up. For this project, we are using Maven. Maven is a dependency management tool, and using it makes it easier for us to sync and work across multiple computers.

*We are using Java 21 in this project.*

When trying to change the project through eclipse, I got an error saying "Downloading external resources is disabled."
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
2) If you pulled changes that moved or renamed files and Eclipse shows errors, do the same thing. In VS Code, use **Ctrl+Shift+P → Java: Clean Java Language Server Workspace** instead.


## Running from a terminal (Ubuntu / WSL)
`pom.xml` is inside the `ProjectManager` folder, so Maven has to be run from there. In WSL, your Windows drives are under `/mnt/` (for example `D:\` is `/mnt/d/`). If the path has spaces in it, put it in quotes.
```bash
cd "/mnt/d/path/to/PasswordManager/ProjectManager"
mvn test
```



# How the program works
The program never stores your master password or your saved passwords as readable text.

1) When you **register**, your master password is turned into a key using Argon2id (a deliberately slow algorithm, so guessing passwords is expensive). That key locks a random *vault key*, and only the locked version is saved.
2) When you **log in**, the same steps are repeated with the password you typed. If it's right, the vault key unlocks. If it's wrong, the lock check fails and login is rejected. There is no stored password to compare against.
3) When you **save a password**, it's encrypted with your vault key using AES-256-GCM. If anyone edits the encrypted data in the database file, decrypting it fails instead of giving back wrong data.
4) When you **log out** (or leave it idle for 10 minutes), the vault key is wiped from memory.

**Things to know**
- If you forget your master password, your saved passwords cannot be recovered. There is no backdoor.
- Only the password is encrypted. The title, username, URL and remarks of an entry are stored as normal text.
- Changing your master password only re-locks the vault key, so your saved passwords don't need to be encrypted again.



# Packages

## 1) auth
This is the package a UI should use. Everything else is used by these classes behind the scenes.

**Available methods**
1) AccountService
	- *public static int register(User user, char[] masterPassword)*: Creates a new user and their vault. Returns the new user's ID
	- *public static AuthenticatedSession login(String email, char[] masterPassword)*: Checks the master password and returns a session. Throws *InvalidCredentialsException* if the email or password is wrong
	- *public static void changeMasterPassword(AuthenticatedSession session, char[] currentPassword, char[] newPassword)*: Changes the master password. The current password has to be entered again
	- *public static void deleteAccount(AuthenticatedSession session, char[] currentPassword)*: Deletes the user, their vault and **all** their saved passwords, then logs them out

2) VaultService
	- *public static Password addEntry(AuthenticatedSession session, String title, String username, char[] password, String url, String remarks)*: Encrypts and saves a new entry
	- *public static List\<Password\> listEntries(AuthenticatedSession session)*: Returns all of the user's entries. The passwords are still encrypted
	- *public static char[] revealPassword(AuthenticatedSession session, int passwordID)*: Decrypts one password so it can be shown
	- *public static void updateEntry(AuthenticatedSession session, int passwordID, ...)*: Updates an entry. Pass *null* as the new password to keep the old one
	- *public static void deleteEntry(AuthenticatedSession session, int passwordID)*: Deletes one entry

3) AuthenticatedSession
	- *public int getUserID()*: The ID of the logged-in user
	- *public void lock()*: Logs out and wipes the vault key from memory
	- *public boolean isLocked()*: Whether the session has been locked (logged out, or idle for more than 10 minutes)

All the methods in VaultService use the user ID from the session, so a user can never see or change someone else's entries.

**Example**
```java
AccountService.register(new User("alex@example.com", "Alex", "Smith", null), masterPassword);

try (AuthenticatedSession session = AccountService.login("alex@example.com", masterPassword))
{
    Password entry = VaultService.addEntry(session, "Bank", "alex", "hunter2".toCharArray(), "https://bank.example", null);

    char[] plain = VaultService.revealPassword(session, entry.getPasswordID());
    // ... show it ...
    CryptoService.wipe(plain);
}   // the session is locked automatically here

CryptoService.wipe(masterPassword);
```
Passwords are passed around as *char[]* instead of *String* so they can be wiped from memory with *CryptoService.wipe(...)* once you're done with them.



## 2) crypto
It handles the encryption side of the program. It never touches the database.

**Available methods**
1) CryptoService
	- *public static byte[] deriveKey(char[] password, byte[] salt, KdfParams params)*: Turns a password and salt into a 32-byte key using Argon2id
	- *public static EncryptedData encrypt(byte[] key, byte[] plaintext, byte[] aad)*: Encrypts with AES-256-GCM using a new random nonce every time
	- *public static byte[] decrypt(byte[] key, EncryptedData data, byte[] aad)*: Decrypts. Throws *DecryptionException* if the key is wrong or the data was changed
	- *public static void wipe(byte[] secret)* / *wipe(char[] secret)*: Overwrites a secret with zeros
	- *generateSalt()*, *generateKey()*, *generateNonce()*, *randomBytes(int length)*: Secure random bytes

2) KdfParams
	- Variables: memoryKiB, iterations, parallelism (the Argon2id settings)
	- *KdfParams.DEFAULT*: 64 MiB of memory, 3 passes, 1 thread

3) EncryptedData
	- Variables: nonce, ciphertext (the ciphertext also includes the 16-byte authentication tag)



## 3) database
It handles the database side of the program. These classes only store and read bytes; they never encrypt or decrypt anything.

**Available methods**
1) DatabaseConnection
	- *public static Connection getConnection()*: Connects to the database and updates the tables if needed
	- *public static void setDatabasePath(Path path)*: Uses a different database file (the tests use this)

2) TransactionRunner
	- *public static \<T\> T inTransaction(SqlWork\<T\> work)*: Runs several operations so that either all of them are saved or none of them are. Pass the given connection to every operation inside it

3) SchemaMigrator
	- Keeps the tables up to date. To change a table, add a new migration at the end of the list. **Never edit an old one**, because other people's databases have already run it

### dml (adding, changing and removing rows)
1) UserTableDML
	- *public static int insertUser(User user)*: Adds a new user, sets its new ID on the user and returns it
	- *public static void updateUser(User user)*: Uses user id to update the user's information
	- *public static void deleteUser(int userID)*: Uses user id to remove the user. **Also deletes all of that user's password entries and their vault**

2) PasswordTableDML
	- *public static int insertPassword(Password entry)*: Adds a new password entry, sets its new ID on the entry and returns it
	- *public static void updatePassword(Password entry)*: Uses password id and user id to update the entry's information
	- *public static void deletePassword(int passwordID, int userID)*: Uses password id and user id to remove one entry

3) UserVaultTableDML
	- *public static void insertVault(UserVault vault)*: Saves a new user's vault
	- *public static void updateVault(UserVault vault)*: Replaces it (used when the master password changes)

Every DML method also has a version that takes a *Connection* first (e.g. *insertUser(connection, user)*), for use inside *TransactionRunner*. Update and delete throw *RecordNotFoundException* if nothing matched.

### dql (reading rows)
1) UserTableDQL
	- *public static Optional\<User\> getUserByID(int userID)*: Finds a user by ID
	- *public static Optional\<User\> getUserByEmail(String email)*: Finds a user by email (not case-sensitive)

2) PasswordTableDQL
	- *public static ArrayList\<Password\> getPasswordsByUserID(int userID)*: Takes in the user id and returns all the password entries associated with the id
	- *public static Optional\<Password\> getPasswordByID(int passwordID, int userID)*: Finds one entry, but only if it belongs to that user

3) UserVaultTableDQL
	- *public static Optional\<UserVault\> getVaultByUserID(int userID)*: Finds a user's vault

All of them return *Optional.empty()* when nothing matches.



## 4) models
These classes hold all the data we store about a user, password or vault in the respective tables. The variables here match the columns of the tables. They also have the getters and setters for each variable, and the setters check that the values are valid.

1) User
	- Variables: userID, email, firstName, lastName, remarks
	- Methods: Getters, setters and a parameterized constructor (*User(String email, String firstName, String lastName, String remarks)*)
	- Emails are saved in lowercase, so "Alex@Example.com" and "alex@example.com" are the same account

2) Password
	- Variables: passwordID, userID, title, username, encryptedPassword, url, remarks
	- Methods: Getters, setters and a parameterized constructor (*Password(int userID, String title, String username, EncryptedData encryptedPassword, String url, String remarks)*)
	- The password is only ever stored encrypted. Use *VaultService* to create or read one

3) UserVault
	- Variables: userID, kdfSalt, kdfParams, encryptedVaultKey
	- What's needed to unlock a user's vault with their master password

4) FieldRules
	- The rules the setters use: email format, URLs must start with http:// or https://, and the maximum length of every field



## 5) exceptions
1) InvalidCredentialsException: The email or master password is wrong. It's the same message for both on purpose, so nobody can find out which emails have accounts
2) SessionLockedException: The session was logged out or timed out, so the user has to log in again
3) RecordNotFoundException: The entry doesn't exist, or it belongs to another user
4) DecryptionException: The encrypted data was changed or is corrupted



# Tests
To test if everything is working, run the tests. Each test uses its own temporary database, so your real data is never touched.

- **Eclipse:** right-click the project → **Run As → JUnit Test**. A green bar means everything passed.
- **Terminal:** from the `ProjectManager` folder run `mvn test`. If everything is working, the output ends with:
```
Tests run: 122, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

If a test fails, Maven prints which one and why.

*HygieneTest* also fails if any program code uses *System.out*, *System.err* or *printStackTrace*, because printing things is the most common way passwords end up leaking.



# Database location
By default the database is stored at `<your home folder>/.passwordmanager/passwordmanager.db`.

To use a different file, start Java with `-Dpasswordmanager.db=/path/to/file.db`.
