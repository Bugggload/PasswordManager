/*
Argon2id cost settings. These are stored next to each user's salt so that, if we ever raise
the defaults, old accounts can still be unlocked with the settings they were created with.

memoryKiB   - RAM used per guess, in kibibytes (65536 KiB = 64 MiB). The main defence against GPUs.
iterations  - how many passes over that memory. More = slower.
parallelism - how many threads Argon2 may use.
 */

package crypto;

public record KdfParams(int memoryKiB, int iterations, int parallelism)
{
    // What new accounts use. Takes well under a second on a normal laptop,
    // but makes each password guess expensive for an attacker.

    public static final KdfParams DEFAULT = new KdfParams(65536, 3, 1);


    // Compact constructor: runs on every "new KdfParams(...)" to reject nonsense values

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
