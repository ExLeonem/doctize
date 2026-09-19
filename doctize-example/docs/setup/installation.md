---
order: 10
---

# Installation

## Requirements

| Requirement | Version |
|-------------|---------|
| Java        | 17 or newer |
| Build tool  | Gradle 7+ or Maven 3.8+ |
| Runtime dependencies | none |

## Add the library

```groovy tab="Gradle"
implementation 'dev.example:acme-cache:1.2.0'
```

```xml tab="Maven"
<dependency>
  <groupId>dev.example</groupId>
  <artifactId>acme-cache</artifactId>
  <version>1.2.0</version>
</dependency>
```

> [!TIP]
> Not sure which version is the latest? The badge in the top bar of this site always shows the version
> these docs describe.

## Verify the installation

```java
System.out.println(Cache.builder().build().size()); // 0
```

If this prints `0`, you are ready for the [Quick start](../quick-start/quick-start.md).
