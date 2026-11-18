import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class HygieneTest
{

    private static final List<String> FORBIDDEN = List.of("System.out", "System.err", "printStackTrace");




    @Test
    void programCodeNeverPrintsToConsole() throws IOException
    {
        Path sources = Path.of("src", "main", "java");   // Maven runs tests from the project folder

        assertTrue(Files.isDirectory(sources), "Run tests from the ProjectManager folder");

        try (Stream<Path> files = Files.walk(sources))
        {
            List<String> offenders = files
                .filter(file -> file.toString().endsWith(".java"))
                .filter(HygieneTest::containsForbiddenCall)
                .map(Path::toString)
                .toList();

            assertTrue(offenders.isEmpty(), "Remove console output / stack traces from: " + offenders);
        }
    }




    private static boolean containsForbiddenCall(Path file)
    {
        try
        {
            String code = Files.readString(file);

            return FORBIDDEN.stream().anyMatch(code::contains);
        }
        catch (IOException e)
        {
            throw new IllegalStateException(e);
        }
    }

}
