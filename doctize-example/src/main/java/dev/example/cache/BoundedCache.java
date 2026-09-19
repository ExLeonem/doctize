package dev.example.cache;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** A simple lock-based implementation, good enough to demonstrate the documentation. */
final class BoundedCache<K, V> implements Cache<K, V> {

    private record Entry<V>(V value, long writtenAt, long accesses) {

        Entry<V> touched() {
            return new Entry<>(value, writtenAt, accesses + 1);
        }
    }

    private final long maximumSize;
    private final Duration timeToLive;
    private final EvictionPolicy policy;
    private final boolean recordStats;
    private final Map<K, Entry<V>> entries;
    private long hits;
    private long misses;
    private long evictions;

    BoundedCache(CacheBuilder<K, V> builder) {
        this.maximumSize = builder.maximumSize;
        this.timeToLive = builder.expireAfterWrite;
        this.policy = builder.policy;
        this.recordStats = builder.recordStats;
        this.entries = new LinkedHashMap<>(16, 0.75f, builder.policy == EvictionPolicy.LRU);
    }

    @Override
    public synchronized Optional<V> get(K key) {
        Entry<V> entry = entries.get(key);
        if (entry == null || isExpired(entry)) {
            if (entry != null) {
                entries.remove(key);
                evictions++;
            }
            misses++;
            return Optional.empty();
        }
        entries.put(key, entry.touched());
        hits++;
        return Optional.of(entry.value());
    }

    @Override
    public synchronized V getOrLoad(K key, Function<? super K, ? extends V> loader) {
        Optional<V> cached = get(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        V loaded;
        try {
            loaded = loader.apply(key);
        } catch (RuntimeException e) {
            throw new CacheLoadException("Loader failed for key " + key, e);
        }
        if (loaded == null) {
            throw new CacheLoadException("Loader returned null for key " + key, null);
        }
        put(key, loaded);
        return loaded;
    }

    @Override
    public synchronized void put(K key, V value) {
        entries.put(key, new Entry<>(value, System.nanoTime(), 0));
        while (entries.size() > maximumSize) {
            evictOne();
        }
    }

    @Override
    public synchronized boolean invalidate(K key) {
        return entries.remove(key) != null;
    }

    @Override
    public synchronized void clear() {
        entries.clear();
    }

    @Override
    public synchronized long size() {
        return entries.size();
    }

    @Override
    public synchronized CacheStats stats() {
        return recordStats ? new CacheStats(hits, misses, evictions) : new CacheStats(0, 0, 0);
    }

    private boolean isExpired(Entry<V> entry) {
        return !timeToLive.isZero() && System.nanoTime() - entry.writtenAt() > timeToLive.toNanos();
    }

    private void evictOne() {
        K victim = policy == EvictionPolicy.LFU ? leastFrequentlyUsed() : entries.keySet().iterator().next();
        entries.remove(victim);
        evictions++;
    }

    private K leastFrequentlyUsed() {
        return new HashMap<>(entries).entrySet().stream()
                .min(Comparator.comparingLong(entry -> entry.getValue().accesses()))
                .orElseThrow()
                .getKey();
    }
}
