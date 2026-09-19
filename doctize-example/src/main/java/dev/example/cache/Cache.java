package dev.example.cache;

import dev.doctize.annotations.Doc;
import java.util.Optional;
import java.util.function.Function;

/// A cache maps keys to values and evicts entries once it is full or they have expired.
///
/// Instances are created with a [builder][#builder()] and are safe for concurrent use:
///
/// ```java
/// Cache<String, User> users = Cache.<String, User>builder()
///         .maximumSize(10_000)
///         .expireAfterWrite(Duration.ofMinutes(10))
///         .build();
///
/// User admin = users.getOrLoad("admin", repository::findByName);
/// ```
///
/// > [!TIP]
/// > Prefer {@link #getOrLoad(Object, Function)} over a `get` followed by a `put`:
/// > it guarantees that concurrent callers load a missing value only once.
///
/// @param <K> the type of keys
/// @param <V> the type of values
/// @since 1.0
/// @see CacheBuilder
@Doc(order = 1, keywords = {"memoize", "lookup", "store"})
public interface Cache<K, V> {

    /// Starts building a cache.
    ///
    /// @param <K> the type of keys
    /// @param <V> the type of values
    /// @return a builder with default settings: unbounded size, no expiry, [EvictionPolicy#LRU]
    static <K, V> CacheBuilder<K, V> builder() {
        return new CacheBuilder<>();
    }

    /// Looks up a value without loading it.
    ///
    /// @param key the key to look up, never `null`
    /// @return the cached value, or an empty optional if there is none or it has expired
    Optional<V> get(K key);

    /// Returns the cached value, loading and storing it first if it is missing.
    ///
    /// The `loader` runs at most once per key, even if many threads ask for the same missing key.
    ///
    /// @param key the key to look up, never `null`
    /// @param loader computes the value for a missing key; must not return `null`
    /// @return the cached or freshly loaded value
    /// @throws CacheLoadException if the loader fails or returns `null`
    V getOrLoad(K key, Function<? super K, ? extends V> loader);

    /// Stores a value, replacing any previous one for the key.
    ///
    /// @param key the key, never `null`
    /// @param value the value, never `null`
    void put(K key, V value);

    /// Removes the entry for a key.
    ///
    /// @param key the key to remove
    /// @return `true` if an entry was removed
    boolean invalidate(K key);

    /// Removes all entries. Statistics are kept.
    void clear();

    /// The number of entries currently stored, including entries that have expired but not yet been purged.
    ///
    /// @return the approximate number of entries
    long size();

    /// A snapshot of the hit, miss and eviction counters.
    ///
    /// @return the current statistics; all zero unless [CacheBuilder#recordStats()] was requested
    CacheStats stats();
}
