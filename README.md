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



# How to use it (start here)
A UI should only ever call the **`auth`** package. Everything below it (crypto, database) is plumbing.

```java
// Create an account
AccountService.register(new User("alex@example.com", "Alex", "Smith", null), masterPassword);

// Log in: try-with-resources locks the session (wipes the key) automatically at the end
try (AuthenticatedSession session = AccountService.login("alex@example.com", masterPassword)) {

    Password entry = VaultService.addEntry(session, "Bank", "alex", "hunter2".toCharArray(), "https://bank.example", null);

    List<Password> all = VaultService.listEntries(session);          // titles readable, passwords still encrypted

    char[] plain = VaultService.revealPassword(session, entry.getPasswordID());
    // ... show it ...
    CryptoService.wipe(plain);                                         // then wipe it

    VaultService.updateEntry(session, entry.getPasswordID(), "Bank", "alex", null, null, null);  // null password = keep
    VaultService.deleteEntry(session, entry.getPasswordID());

    AccountService.changeMasterPassword(session, masterPassword, newMasterPassword);
}
CryptoService.wipe(masterPassword);   // the caller owns password arrays and wipes them
```

| Situation | What you get |
|---|---|
| Wrong email or wrong master password | `InvalidCredentialsException` (same message for both, on purpose) |
| Session locked (logged out, or idle > 10 min) | `SessionLockedException`: log in again |
| Entry doesn't exist / belongs to someone else | `RecordNotFoundException` |
| Stored data was tampered with | `DecryptionException` |
| Bad input (invalid email/URL, too long, empty) | `IllegalArgumentException` with a message safe to show |

# Security design
- **Master password → key:** Argon2id (64 MiB, 3 passes) with a random per-user salt. The master password is never stored.
- **Envelope encryption:** each user has a random 256-bit *vault key*. It's stored encrypted with the key derived from the master password. Changing the master password only re-encrypts the vault key.
- **Passwords:** AES-256-GCM with a fresh random nonce per encryption. Tampering is detected. Ciphertext is bound to its owner (AAD), so copying it between users fails.
- **Access control:** `VaultService` takes the user ID from the session, never from a parameter.
- **Memory:** keys and passwords are `byte[]`/`char[]` and are wiped after use. Sessions wipe their key on lock/logout/idle timeout.
- **No recovery:** a forgotten master password means the vault cannot be opened. There is no backdoor.
- **Plaintext columns:** title, username, URL and remarks are NOT encrypted (only the password is).

# Project structure
All code is under `ProjectManager/src/main/java`:

```
auth/           Public API: what a UI calls
  AccountService        register, login, changeMasterPassword, deleteAccount
  VaultService          addEntry, listEntries, revealPassword, updateEntry, deleteEntry
  AuthenticatedSession  logged-in user + unlocked vault key; lock()/close() logs out

crypto/         Cryptography (no database access)
  CryptoService         Argon2id, AES-256-GCM, random bytes, wipe
  KdfParams             Argon2id cost settings
  EncryptedData         nonce + ciphertext/tag

database/       Connection, schema and transactions
  DatabaseConnection    connection, DB location, shared helpers (generated IDs, row-count checks)
  SchemaMigrator        versioned migrations (PRAGMA user_version)
  TransactionRunner     all-or-nothing groups of operations
  dml/                  UserTableDML, PasswordTableDML, UserVaultTableDML   (insert/update/delete)
  dql/                  UserTableDQL, PasswordTableDQL, UserVaultTableDQL   (select)

models/         One class per table row
  User, Password, UserVault
  FieldRules            validation (email, URL, length limits)

exceptions/     DecryptionException, InvalidCredentialsException, RecordNotFoundException, SessionLockedException
```

Notes:
- The database classes only store bytes; they never encrypt or decrypt. There is no plaintext password column.
- Every DML method has an overload that takes a `Connection` first, for use inside `TransactionRunner.inTransaction`.
- Update/delete throw `RecordNotFoundException` when no row matches (missing ID or another user's record).
- Deleting a user also deletes their vault and all their passwords (ON DELETE CASCADE).
- Emails are trimmed and lowercased, so "Alex@Example.com" and "alex@example.com" are the same account.

# Tests
Tests live in `src/test/java` and use JUnit 5. Each test runs against its own temporary database, so your real data is never touched.

- **Eclipse:** right-click the project (or a test file) → **Run As → JUnit Test**. A green bar means everything passed.
- **Terminal:** from the `ProjectManager` folder run `mvn test`. It ends with `BUILD SUCCESS` and `Tests run: N, Failures: 0, Errors: 0`.

If a test fails, Maven prints which one and why, and the build fails.

`HygieneTest` fails the build if program code uses `System.out`, `System.err` or `printStackTrace`, because console output is how secrets usually leak.

# Database location
By default the database is stored at `<your home folder>/.passwordmanager/passwordmanager.db`.
To use a different file, start Java with `-Dpasswordmanager.db=/path/to/file.db`.

