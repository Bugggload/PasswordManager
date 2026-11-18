package models;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Validation shared by the models. Error messages are safe to show to users. */
public final class FieldRules
{
    public static final int MAX_EMAIL_LENGTH = 254;
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_USERNAME_LENGTH = 254;
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MAX_REMARKS_LENGTH = 1000;

    // Intentionally loose: catches typos, not every RFC edge case
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private FieldRules()
    {
    }

    /** Trims and lowercases; used for both saving and lookups so they always match. */
    public static String normalizeEmail(String email)
    {
        if (email == null || email.isBlank())
        {
            throw new IllegalArgumentException("Email cannot be empty.");
        }

        String normalized = email.trim().toLowerCase(Locale.ROOT);

        if (normalized.length() > MAX_EMAIL_LENGTH)
        {
            throw new IllegalArgumentException("Email must be at most " + MAX_EMAIL_LENGTH + " characters.");
        }

        if (!EMAIL.matcher(normalized).matches())
        {
            throw new IllegalArgumentException("Email address is not valid.");
        }

        return normalized;
    }

    public static String requiredText(String value, String fieldName, int maxLength)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " cannot be empty.");
        }

        String trimmed = value.strip();

        if (trimmed.length() > maxLength)
        {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters.");
        }

        return trimmed;
    }

    /** Blank becomes null. */
    public static String optionalText(String value, String fieldName, int maxLength)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }

        if (value.length() > maxLength)
        {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters.");
        }

        return value;
    }

    /** Blank becomes null; otherwise must be an http(s) URL with a host. */
    public static String optionalUrl(String url)
    {
        if (url == null || url.isBlank())
        {
            return null;
        }

        String trimmed = url.strip();

        if (trimmed.length() > MAX_URL_LENGTH)
        {
            throw new IllegalArgumentException("URL must be at most " + MAX_URL_LENGTH + " characters.");
        }

        try
        {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();

            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null)
            {
                throw new IllegalArgumentException("URL must start with http:// or https:// and include a website.");
            }
        }
        catch (URISyntaxException e)
        {
            throw new IllegalArgumentException("URL is not valid.");
        }

        return trimmed;
    }
}
