---
order: 20
---

# Configuration

Acme Cache is configured in code, through {@link CacheBuilder}. There are no configuration files and no
system properties.

## Settings at a glance

| Method | Default | Effect |
|--------|---------|--------|
| {@link CacheBuilder#maximumSize(long)} | unbounded | Caps the number of entries |
| {@link CacheBuilder#expireAfterWrite(java.time.Duration)} | never | Expires entries after a time to live |
| {@link CacheBuilder#evictionPolicy(EvictionPolicy)} | {@link EvictionPolicy#LRU} | Chooses the entry to evict |
| {@link CacheBuilder#recordStats()} | off | Enables {@link Cache#stats()} |

## Sizing a cache

Choose the size from the memory you can spare, not from the number of entries you *hope* to store:

1. Estimate the average size of one entry.
2. Multiply by the number of entries you want to keep.
3. Add 30% for overhead.

- [x] Set a maximum size
- [x] Record statistics in production for a week
- [ ] Tune the eviction policy based on the hit rate
