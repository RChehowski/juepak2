package eu.chakhouski.juepak.util;

import sun.reflect.Reflection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class LRU<K,V> extends LinkedHashMap<K,V> implements Map<K, V>
{
    private final int sizeLimit;

    private long LRU_DEBUG_hits = 0;
    private long LRU_DEBUG_misses = 0;

    /**
     * Debug (enabled or not?)
     */
    private static final boolean LRU_DEBUG_ENABLED = false;

    /**
     * Keeps all LRUs in application domain if {@link #LRU_DEBUG_ENABLED} is true
     */
    private static final Map<String, LRU<?, ?>> allLRU = LRU_DEBUG_ENABLED ? new ConcurrentHashMap<>() : Collections.emptyMap();

    static {
        if (LRU_DEBUG_ENABLED)
        {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                for (final Entry<String, LRU<?, ?>> entry : allLRU.entrySet())
                {
                    final String invokerClassName = entry.getKey();
                    final LRU<?, ?> lru = entry.getValue();

                    final float hitRate = (float) lru.LRU_DEBUG_hits / (lru.LRU_DEBUG_hits + lru.LRU_DEBUG_misses) * 100.0f;

                    System.out.println(String.join(System.lineSeparator(),
                            "Cache rate for \"" + invokerClassName + "\" LRU",
                            " > Hits: " + lru.LRU_DEBUG_hits + ", Misses: " + lru.LRU_DEBUG_misses,
                            " > Size: " + lru.size() + ", Limit: " + lru.sizeLimit(),
                            " > Hit rate: " + (Float.isNaN(hitRate) ? "unknown" : (hitRate + "%"))
                    ));
                }

                allLRU.clear();
            }));
        }
    }

    public LRU(int sizeLimit)
    {
        super(16, .75f, true);

        if (LRU_DEBUG_ENABLED)
        {
            final String callerClassName = Reflection.getCallerClass(2).getName();
            allLRU.put(callerClassName, this);
        }

        this.sizeLimit = sizeLimit;
    }

    @Override
    public V get(Object key)
    {
        final V got = super.get(key);

        if (LRU_DEBUG_ENABLED)
        {
            if (got == null)
                LRU_DEBUG_misses++;
            else
                LRU_DEBUG_hits++;
        }

        return got;
    }

    @Override
    public V getOrDefault(Object key, V defaultValue)
    {
        final V got = super.getOrDefault(key, defaultValue);

        if (LRU_DEBUG_ENABLED)
        {
            if (got == defaultValue)
                LRU_DEBUG_misses++;
            else
                LRU_DEBUG_hits++;
        }

        return got;
    }

    @Override
    protected final boolean removeEldestEntry(Entry eldest)
    {
        return size() > sizeLimit;
    }

    private int sizeLimit()
    {
        return sizeLimit;
    }
}
