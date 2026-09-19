package dev.example.cache;

import dev.doctize.annotations.Doc;
import java.time.Duration;
import java.util.Objects;

/**
 * Configures and creates {@link Cache} instances.
 *
 * <p>Every setting is optional. A builder can be reused: each call of {@link #build()} creates an
 * independent cache.
 *
 * <pre>{@code
 * Cache<String, byte[]> thumbnails = Cache.<String, byte[]>builder()
 *         .maximumSize(500)
 *         .evictionPolicy(EvictionPolicy.LFU)
 *         .recordStats()
 *         .build();
 * }</pre>
 *
 * @param <K> the type of keys of the cache to build
 * @param <V> the type of values of the cache to build
 * @apiNote This class is not thread-safe; configure it on one thread, then share the cache.
 * @since 1.0
 */
@Doc(order = 2)
public final class CacheBuilder<K, V> {

    long maximumSize = Long.MAX_VALUE;
    Duration expireAfterWrite = Duration.ZERO;
    EvictionPolicy policy = EvictionPolicy.LRU;
    boolean recordStats;

    CacheBuilder() {}

    /**
     * Limits the number of entries. When the limit is exceeded the entry chosen by the
     * {@linkplain #evictionPolicy(EvictionPolicy) eviction policy} is removed.
     *
     * @param maximumSize the maximum number of entries, must be positive
     * @return this builder
     * @throws IllegalArgumentException if {@code maximumSize} is not positive
     */
    public CacheBuilder<K, V> maximumSize(long maximumSize) {
        if (maximumSize <= 0) {
            throw new IllegalArgumentException("maximumSize must be positive: " + maximumSize);
        }
        this.maximumSize = maximumSize;
        return this;
    }

    /**
     * Expires entries a fixed time after they were written.
     *
     * @param duration the time to live; {@link Duration#ZERO} disables expiry
     * @return this builder
     */
    public CacheBuilder<K, V> expireAfterWrite(Duration duration) {
        this.expireAfterWrite = Objects.requireNonNull(duration, "duration");
        return this;
    }

    /**
     * Chooses which entry is removed when the cache is full.
     *
     * @param policy the policy, {@link EvictionPolicy#LRU} by default
     * @return this builder
     * @see EvictionPolicy
     */
    public CacheBuilder<K, V> evictionPolicy(EvictionPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
        return this;
    }

    /**
     * Enables the counters reported by {@link Cache#stats()}. Recording costs a few nanoseconds per
     * access, so it is off by default.
     *
     * @return this builder
     */
    public CacheBuilder<K, V> recordStats() {
        this.recordStats = true;
        return this;
    }

    /**
     * Creates a cache with the configured settings.
     *
     * @return a new, empty cache
     */
    public Cache<K, V> build() {
        return new BoundedCache<>(this);
    }

    /** Kept for source compatibility with 0.x. */
    @Deprecated(since = "1.0")
    public Cache<K, V> create() {
        return build();
    }
}
