package auth;

import crypto.CryptoService;
import exceptions.SessionLockedException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * A logged-in user and their unlocked vault key. Locks itself (wiping the key) on lock(), close()
 * or after the idle timeout. Use with try-with-resources so it always gets locked.
 */
public final class AuthenticatedSession implements AutoCloseable
{
    public static final Duration DEFAULT_IDLE_TIMEOUT = Duration.ofMinutes(10);

    private final int userID;
    private final byte[] vaultKey;
    private final Duration idleTimeout;
    private final Clock clock;

    private Instant lastUsed;
    private boolean locked;

    // Only AccountService creates sessions; the Clock lets tests fast-forward time
    AuthenticatedSession(int userID, byte[] vaultKey, Duration idleTimeout, Clock clock)
    {
        if (userID <= 0 || vaultKey == null || vaultKey.length != CryptoService.KEY_LENGTH_BYTES
            || idleTimeout == null || idleTimeout.isNegative() || idleTimeout.isZero() || clock == null)
        {
            throw new IllegalArgumentException("Invalid session values.");
        }

        this.userID = userID;
        this.vaultKey = vaultKey.clone();
        this.idleTimeout = idleTimeout;
        this.clock = clock;
        this.lastUsed = clock.instant();
    }

    public int getUserID()
    {
        requireUnlocked();
        return userID;
    }

    // Package-private so only auth classes touch the raw key. Returns the live array so there's one copy to wipe.
    byte[] vaultKey()
    {
        requireUnlocked();
        return vaultKey;
    }

    public synchronized boolean isLocked()
    {
        lockIfIdle();
        return locked;
    }

    /** Wipes the vault key. Safe to call more than once. */
    public synchronized void lock()
    {
        CryptoService.wipe(vaultKey);
        locked = true;
    }

    @Override
    public void close()
    {
        lock();
    }

    @Override
    public String toString()
    {
        return "AuthenticatedSession[userID=" + userID + ", locked=" + locked + "]";
    }

    private synchronized void requireUnlocked()
    {
        lockIfIdle();

        if (locked)
        {
            throw new SessionLockedException();
        }

        lastUsed = clock.instant();
    }

    private void lockIfIdle()
    {
        if (!locked && Duration.between(lastUsed, clock.instant()).compareTo(idleTimeout) > 0)
        {
            lock();
        }
    }
}
