package com.tradeshift.reaktive.akka;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;

// source: https://github.com/datastax/java-driver/blob/3.1.x/driver-core/src/main/java/com/datastax/driver/core/utils/UUIDs.java

/**
 * Time-based UUID logic lifted from cassandra, in order to be able to create akka query UUIDs without having
 * a compile-time cassandra dependency.
 */
public class UUIDs {
    /*
     * The min and max possible lsb for a UUID.
     * Note that his is not 0 and all 1's because Cassandra TimeUUIDType
     * compares the lsb parts as a signed byte array comparison. So the min
     * value is 8 times -128 and the max is 8 times +127.
     *
     * Note that we ignore the uuid variant (namely, MIN_CLOCK_SEQ_AND_NODE
     * have variant 2 as it should, but MAX_CLOCK_SEQ_AND_NODE have variant 0)
     * because I don't trust all uuid implementation to have correctly set
     * those (pycassa don't always for instance).
     */
    private static final long MIN_CLOCK_SEQ_AND_NODE = 0x8080808080808080L;
    
    private static final long START_EPOCH = makeEpoch();
    
    private static long makeEpoch() {
        // UUID v1 timestamp must be in 100-nanoseconds interval since 00:00:00.000 15 Oct 1582.
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("GMT-0"));
        c.set(Calendar.YEAR, 1582);
        c.set(Calendar.MONTH, Calendar.OCTOBER);
        c.set(Calendar.DAY_OF_MONTH, 15);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
    
    /**
     * Creates a "fake" time-based UUID that sorts as the smallest possible
     * version 1 UUID generated at the provided timestamp.
     * <p/>
     * Such created UUIDs are useful in queries to select a time range of a
     * {@code timeuuid} column.
     * <p/>
     * The UUIDs created by this method <b>are not unique</b> and as such are
     * <b>not</b> suitable for anything else than querying a specific time
     * range. In particular, you should not insert such UUIDs. "True" UUIDs from
     * user-provided timestamps are not supported (see {@link #timeBased()}
     * for more explanations).
     * <p/>
     * Also, the timestamp to provide as a parameter must be a Unix timestamp (as
     * returned by {@link System#currentTimeMillis} or {@link java.util.Date#getTime}), and
     * <em>not</em> a count of 100-nanosecond intervals since 00:00:00.00, 15 October 1582 (as required by RFC-4122).
     * <p/>
     * In other words, given a UUID {@code uuid}, you should never call
     * {@code startOf(uuid.timestamp())} but rather
     * {@code startOf(unixTimestamp(uuid))}.
     * <p/>
     * Lastly, please note that Cassandra's {@code timeuuid} sorting is not compatible
     * with {@link UUID#compareTo} and hence the UUIDs created by this method
     * are not necessarily lower bound for that latter method.
     *
     * @param timestamp the Unix timestamp for which the created UUID must be a
     *                  lower bound.
     * @return the smallest (for Cassandra {@code timeuuid} sorting) UUID of {@code timestamp}.
     */
    public static UUID startOf(long timestamp) {
        return new UUID(makeMSB(fromUnixTimestamp(timestamp)), MIN_CLOCK_SEQ_AND_NODE);
    }
    
    /**
     * Return the Unix timestamp contained by the provided time-based UUID.
     * <p/>
     * This method is not equivalent to {@link UUID#timestamp()}. More
     * precisely, a version 1 UUID stores a timestamp that represents the
     * number of 100-nanoseconds intervals since midnight, 15 October 1582 and
     * that is what {@link UUID#timestamp()} returns. This method however
     * converts that timestamp to the equivalent Unix timestamp in
     * milliseconds, i.e. a timestamp representing a number of milliseconds
     * since midnight, January 1, 1970 UTC. In particular, the timestamps
     * returned by this method are comparable to the timestamps returned by
     * {@link System#currentTimeMillis}, {@link java.util.Date#getTime}, etc.
     *
     * @param uuid the UUID to return the timestamp of.
     * @return the Unix timestamp of {@code uuid}.
     * @throws IllegalArgumentException if {@code uuid} is not a version 1 UUID.
     */
    public static long unixTimestamp(UUID uuid) {
        if (uuid.version() != 1)
            throw new IllegalArgumentException(String.format("Can only retrieve the unix timestamp for version 1 uuid (provided version %d)", uuid.version()));

        long timestamp = uuid.timestamp();
        return (timestamp / 10000) + START_EPOCH;
    }
    
    private static long fromUnixTimestamp(long tstamp) {
        return (tstamp - START_EPOCH) * 10000;
    }

    private static long makeMSB(long timestamp) {
        long msb = 0L;
        msb |= (0x00000000ffffffffL & timestamp) << 32;
        msb |= (0x0000ffff00000000L & timestamp) >>> 16;
        msb |= (0x0fff000000000000L & timestamp) >>> 48;
        msb |= 0x0000000000001000L; // sets the version to 1.
        return msb;
    }
}
