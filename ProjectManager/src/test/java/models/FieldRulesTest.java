package models;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class FieldRulesTest
{

    @Test
    void emailIsTrimmedAndLowercased()
    {
        assertEquals("alex@example.com", FieldRules.normalizeEmail("  Alex@EXAMPLE.com "));
        assertEquals("alex@example.com", new User("ALEX@example.com", "A", "S", null).getEmail());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "alex", "alex@", "@example.com", "alex@example", "al ex@example.com", "a@b@c.com" })
    void badEmailsAreRejected(String email)
    {
        assertThrows(IllegalArgumentException.class, () -> FieldRules.normalizeEmail(email));
    }

    @Test
    void overlongEmailIsRejected()
    {
        String longEmail = "a".repeat(FieldRules.MAX_EMAIL_LENGTH) + "@example.com";

        assertThrows(IllegalArgumentException.class, () -> FieldRules.normalizeEmail(longEmail));
    }

    @ParameterizedTest
    @ValueSource(strings = { "https://example.com", "http://example.com/login?x=1", "HTTPS://Example.com" })
    void goodUrlsAreAccepted(String url)
    {
        assertEquals(url, FieldRules.optionalUrl(url));
    }

    @ParameterizedTest
    @ValueSource(strings = { "example.com", "ftp://example.com", "javascript:alert(1)", "https://", "http://exa mple.com" })
    void badUrlsAreRejected(String url)
    {
        assertThrows(IllegalArgumentException.class, () -> FieldRules.optionalUrl(url));
    }

    @Test
    void blankOptionalFieldsBecomeNull()
    {
        assertNull(FieldRules.optionalUrl("  "));
        assertNull(FieldRules.optionalText("", "Remarks", 10));
    }

    @Test
    void lengthLimitsAreEnforced()
    {
        assertThrows(IllegalArgumentException.class, () -> new User("a@example.com", "x".repeat(FieldRules.MAX_NAME_LENGTH + 1), "S", null));
        assertThrows(IllegalArgumentException.class, () -> FieldRules.optionalText("x".repeat(11), "Remarks", 10));
        assertEquals("ok", FieldRules.requiredText("  ok  ", "Title", 10), "Required text is trimmed");
    }

}
