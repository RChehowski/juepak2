package com.vizor.unreal.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A container collection, representing a preempting least-recently use cache.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Cache_replacement_policies">LRU Cache</a>
 * @param <K> Key type.
 * @param <V> Value type.
 */
public final class LRU<K,V> extends LinkedHashMap<K,V> implements Map<K, V>
{
    /**
     * Debug info item.
     */
    private static final class LRUDebugHitMissInfo
    {
        private long hits = 0;
        private long misses = 0;

        private void hit()
        {
            ++hits;
        }

        private void miss()
        {
            ++misses;
        }

        private float getHitRatePercent()
        {
            final long totalGets = hits + misses;

            if (totalGets > 0)
            {
                final double hitRate = (double) hits / (totalGets);
                return (float) ((double) Math.round(hitRate * 100.0 * 100.0) / 100.0);
            }

            return Float.NaN;
        }
    }

    /**
     * Size limit for cache, old values will be preempted.
     */
    private final int sizeLimit;

    /**
     * Chaining can be avoided and branch prediction exploited by predicting if a bucket is empty or not.
     * A bucket is probably empty if the probability of it being empty exceeds .5.
     *
     * Let us represent the size and n the number of keys added. Using the binomial theorem, the probability
     * of a bucket being empty is:
     * P(0) = C(n, 0) * (1/s)^0 * (1 - 1/s)^(n - 0)
     *
     * Thus, a bucket is probably empty if there are less than
     * log(2)/log(s/(s - 1)) keys
     *
     * As s reaches infinity and if the number of keys added is such that P(0) = .5, then n/s approaches
     * log(2) rapidly:
     * lim (log(2)/log(s/(s - 1)))/s as s -> infinity = log(2) ~ 0.693...
     */
    private static final float LRU_LOAD_FACTOR = (float)Math.log(2);

    /**
     * Debug (enabled or not?) collect some debug info if caching is enabled.
     */
    private static final boolean LRU_DEBUG_ENABLED = false;

    /**
     * ! DEBUG ONLY
     *
     * Keeps all LRUs in application domain if {@link #LRU_DEBUG_ENABLED} is true.
     */
    private static final Map<String, LRU<?, ?>> allLRU =
            LRU_DEBUG_ENABLED ? new ConcurrentHashMap<>() : Collections.emptyMap();

    /**
     * ! DEBUG ONLY
     *
     * Maps each LRU onto a special {@link LRUDebugHitMissInfo} debug info.
     * Used to minimize memory overhead if debugging is disabled (normal conditions).
     */
    private static final Map<LRU<?, ?>, LRUDebugHitMissInfo> lruDebugInfo =
            LRU_DEBUG_ENABLED ? new ConcurrentHashMap<>() : Collections.emptyMap();

    static {
        if (LRU_DEBUG_ENABLED)
        {
            Runtime.getRuntime().addShutdownHook(new Thread(LRU::finalizeAll));
        }
    }

    public LRU(int sizeLimit)
    {
        super(16, LRU_LOAD_FACTOR, true);

        if (LRU_DEBUG_ENABLED)
        {
            final StackTraceElement[] trace = new Throwable().getStackTrace();

            String lruDebugName = "unknown";
            if (trace.length > 1)
            {
                final StackTraceElement caller = trace[1];
                lruDebugName = caller.getFileName() + ':' + caller.getLineNumber();
            }

            allLRU.put(lruDebugName, this);
            lruDebugInfo.put(this, new LRUDebugHitMissInfo());
        }

        this.sizeLimit = sizeLimit;
    }

    @Override
    public V get(Object key)
    {
        final V got = super.get(key);

        if (LRU_DEBUG_ENABLED)
            DEBUG_updateLruHitRate(got != null);

        return got;
    }

    @Override
    public V getOrDefault(Object key, V defaultValue)
    {
        final V got = super.getOrDefault(key, defaultValue);

        if (LRU_DEBUG_ENABLED)
            DEBUG_updateLruHitRate(got != defaultValue);

        return got;
    }

    private void DEBUG_updateLruHitRate(boolean hit)
    {
        final LRUDebugHitMissInfo di;

        if ((di = lruDebugInfo.get(this)) != null)
        {
            if (hit)
                di.hit();
            else
                di.miss();
        }
    }

    @Override
    protected final boolean removeEldestEntry(Entry eldest)
    {
        return size() > sizeLimit;
    }

    @Override
    public final int hashCode()
    {
        // Otherwise we can't add an LRU (actually a map) as key into an another map
        if (LRU_DEBUG_ENABLED)
            return System.identityHashCode(this);

        return super.hashCode();
    }

    private static void finalizeAll()
    {
        for (final Entry<String, LRU<?, ?>> entry : allLRU.entrySet())
        {
            final LRU<?, ?> lru = entry.getValue();
            final LRUDebugHitMissInfo hm = lruDebugInfo.get(lru);

            final float hitRate = hm.getHitRatePercent();

            System.out.println(String.join(System.lineSeparator(),
                    "Cache rate for \"" + entry.getKey() + "\" LRU",
                    " > Hits: " + hm.hits + ", Misses: " + hm.misses,
                    " > Size: " + lru.size() + ", Limit: " + lru.sizeLimit,
                    " > Hit rate: " + (Float.isNaN(hitRate) ? "not enough data" : (hitRate + "%"))
            ));
        }

        allLRU.clear();
        lruDebugInfo.clear();
    }
}
