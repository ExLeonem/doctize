package dev.example.cache;

import dev.doctize.annotations.Doc;

/**
 * An immutable snapshot of cache counters.
 *
 * @param hits number of lookups that found a value
 * @param misses number of lookups that did not
 * @param evictions number of entries removed to make room or because they expired
 * @since 1.1
 */
@Doc(order = 4)
public record CacheStats(long hits, long misses, long evictions) {

    /**
     * The share of lookups that found a value.
     *
     * @return a number between 0 and 1, or 0 if there were no lookups yet
     */
    public double hitRate() {
        long lookups = hits + misses;
        return lookups == 0 ? 0 : (double) hits / lookups;
    }
}
