package database;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseConnectionTest
{

    @TempDir
    Path tempFolder;




    @Test
    void missingFolderIsCreated() throws SQLException
    {
        Path db = tempFolder.resolve("new-folder").resolve("test.db");

        DatabaseConnection.setDatabasePath(db);
        DatabaseConnection.getConnection().close();

        assertTrue(Files.exists(db));
    }




    // Only meaningful on Linux/macOS/WSL; skipped on Windows

    @Test
    void newFolderIsPrivateToTheOwner() throws Exception
    {
        assumeTrue(tempFolder.getFileSystem().supportedFileAttributeViews().contains("posix"));

        Path folder = tempFolder.resolve("private-folder");

        DatabaseConnection.setDatabasePath(folder.resolve("test.db"));
        DatabaseConnection.getConnection().close();

        assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(folder)));
    }

}
