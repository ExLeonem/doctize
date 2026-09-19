/// # Choosing an eviction policy
///
/// A bounded cache must decide what to throw away when it is full. Acme Cache offers three
/// policies, selected with {@link dev.example.cache.CacheBuilder#evictionPolicy}.
///
/// | Policy | Evicts | Best for |
/// |--------|--------|----------|
/// | {@link EvictionPolicy#LRU} | the entry not used for the longest time | most workloads |
/// | {@link EvictionPolicy#LFU} | the entry used least often | stable hot sets with bursts of one-off reads |
/// | {@link EvictionPolicy#FIFO} | the oldest entry | data with a natural time-to-live |
///
/// ## How to decide
///
/// Start with LRU. Record statistics and look at the hit rate:
///
/// ```java
/// var cache = Cache.<String, Page>builder().maximumSize(1_000).recordStats().build();
/// // ... run your workload ...
/// System.out.printf("hit rate: %.1f%%%n", cache.stats().hitRate() * 100);
/// ```
///
/// If a full table scan pushes the hot entries out, try LFU.
///
/// ## Expiry is independent
///
/// Expiry ([time to live][CacheBuilder#expireAfterWrite(Duration)]) is checked on every read and works
/// with every policy.
@DocPage(section = "Guides", title = "Choosing an eviction policy", order = 10, keywords = {"lru", "lfu", "fifo"})
package dev.example.cache;

import dev.doctize.annotations.DocPage;
