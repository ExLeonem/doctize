---
title: Quick start
order: 1
description: Add the dependency and cache your first value in five minutes.
keywords: [tutorial, hello world, getting started]
---

# Quick start

This tutorial takes about five minutes. You will add Acme Cache to a project, build a cache, and
watch it evict.

## 1. Add the dependency

```groovy tab="Gradle"
dependencies {
    implementation 'dev.example:acme-cache:1.2.0'
}
```

```kotlin tab="Gradle (Kotlin)"
dependencies {
    implementation("dev.example:acme-cache:1.2.0")
}
```

```xml tab="Maven"
<dependency>
  <groupId>dev.example</groupId>
  <artifactId>acme-cache</artifactId>
  <version>1.2.0</version>
</dependency>
```

## 2. Build a cache

Everything starts at {@link Cache#builder()}:

```java title="Main.java"
import dev.example.cache.Cache;
import dev.example.cache.EvictionPolicy;

public class Main {
    public static void main(String[] args) {
        Cache<String, Integer> lengths = Cache.<String, Integer>builder()
                .maximumSize(2)
                .evictionPolicy(EvictionPolicy.LRU)
                .recordStats()
                .build();

        lengths.put("one", 3);
        lengths.put("two", 3);
        lengths.get("one");          // "one" is now the most recently used
        lengths.put("three", 5);     // evicts "two"

        System.out.println(lengths.get("two").isPresent()); // false
        System.out.println(lengths.stats());
    }
}
```

## 3. Load values on demand

Most caches are filled lazily. {@link Cache#getOrLoad(Object, java.util.function.Function)} loads a
value once and serves it from memory afterwards:

```java
String page = pages.getOrLoad(url, httpClient::fetch);
```

> [!WARNING]
> The loader must not return `null`. A `null` result raises a {@link CacheLoadException}.

## Next steps

- Read [Installation](../setup/installation.md) for supported Java versions.
- Learn how [eviction policies](../guides/choosing-an-eviction-policy.html) differ.
- Browse the {@link CacheBuilder} reference for every setting.
