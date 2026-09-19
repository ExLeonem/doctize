---
title: Testing code that uses a cache
order: 20
group: How-to
---

Caches make tests flaky when state leaks between them. Give every test its own cache, or call
{@link Cache#clear()} in a setup method:

```java
class UserServiceTest {
    private final Cache<String, User> cache = Cache.<String, User>builder().build();

    @AfterEach
    void reset() {
        cache.clear();
    }
}
```

![How entries move through the cache](../images/eviction.svg)

To assert on behaviour instead of internals, enable statistics and check the counters:

```java
assertThat(cache.stats().hits()).isEqualTo(1);
```
