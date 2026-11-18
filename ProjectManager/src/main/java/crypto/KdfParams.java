package crypto;

/** Argon2id cost settings, stored per user so defaults can be raised without breaking old accounts. */
public record KdfParams(int memoryKiB, int iterations, int parallelism)
{
    /** 64 MiB, 3 passes, 1 thread. */
    public static final KdfParams DEFAULT = new KdfParams(65536, 3, 1);

    public KdfParams
    {
        if (parallelism < 1)
        {
            throw new IllegalArgumentException("Parallelism must be at least 1.");
        }

        if (iterations < 1)
        {
            throw new IllegalArgumentException("Iterations must be at least 1.");
        }

        if (memoryKiB < 8 * parallelism)
        {
            throw new IllegalArgumentException("Argon2 needs at least 8 KiB of memory per lane.");
        }
    }
}
