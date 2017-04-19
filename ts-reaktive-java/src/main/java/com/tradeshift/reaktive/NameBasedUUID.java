package com.tradeshift.reaktive;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;

/**
 * This class implements name-based (version 5) UUIDs according to RFC 4122.
 *
 * We prefer this over the version 3 name-based UUID that UUID.nameBasedUUID generates, since version 5 uses SHA-1 over MD5.
 */
public class NameBasedUUID {
    /** The Leach-Salz variant, standardized by RFC 4122. */
    private static final byte UUID_VARIANT = 2;

    /** Name-based UUID are version 5 of the Leach-Salz variant. */
    private static final byte UUID_VERSION = 5;

    /** The charset used by this namespace to convert strings into bytes. */
    private static final Charset CHARSET = Charset.forName("UTF-8");

    /**
     * Returns a name-based UUID using an implementation of the algorithm defined by RFC 4122 for name-based (version 5) UUIDs.
     */
    public static UUID create(UUID namespace, String name) {
        // The code below is meant to be readable, not the fastest:
        try {
            final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            // Compute the hash for the concatenation of namespace and name.
            update(sha1, namespace.getMostSignificantBits());
            update(sha1, namespace.getLeastSignificantBits());
            sha1.update(name.getBytes(CHARSET));

            // Take the final hash and truncate to 128 bits.
            byte[] sha1hash = sha1.digest();
            byte[] uuid = Arrays.copyOf(sha1hash, 16);

            // Fix the version and variant fields
            // xxxxxxxx-xxxx-Mxxx-Nxxx-xxxxxxxxxxxx (M = version, N = variant)
            uuid[6] = (byte) ((UUID_VERSION << 4) | (uuid[6] & 0x0F));
            uuid[8] = (byte) ((UUID_VARIANT << 6) | (uuid[8] & 0x3F));

            // Now we have a valid name-based (version 5) UUID.
            return new UUID(toLong(uuid, 0), toLong(uuid, 8));
        } catch (NoSuchAlgorithmException e) {
            // This should never happen, all Java implementations support SHA-1.
            throw new UnsupportedOperationException(e);
        }
    }

    private static long toLong(byte[] bytes, int offset) {
        return ((long) bytes[offset + 0] & 0xFF) << 56
            | ((long) bytes[offset + 1] & 0xFF) << 48
            | ((long) bytes[offset + 2] & 0xFF) << 40
            | ((long) bytes[offset + 3] & 0xFF) << 32
            | ((long) bytes[offset + 4] & 0xFF) << 24
            | ((long) bytes[offset + 5] & 0xFF) << 16
            | ((long) bytes[offset + 6] & 0xFF) << 8
            | ((long) bytes[offset + 7] & 0xFF);
    }

    private static void update(MessageDigest sha1, long value) {
        sha1.update((byte) (value >> 56 & 0xFF));
        sha1.update((byte) (value >> 48 & 0xFF));
        sha1.update((byte) (value >> 40 & 0xFF));
        sha1.update((byte) (value >> 32 & 0xFF));
        sha1.update((byte) (value >> 24 & 0xFF));
        sha1.update((byte) (value >> 16 & 0xFF));
        sha1.update((byte) (value >> 8 & 0xFF));
        sha1.update((byte) (value & 0xFF));
    }

}
