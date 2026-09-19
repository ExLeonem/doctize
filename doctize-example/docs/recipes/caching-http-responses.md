# Caching HTTP responses

This page lives in the folder `docs/recipes/`. The section "Recipes" is **not declared** in
`doctize.yml`: Doctize created it automatically because a folder with that name exists.

```java
var responses = Cache.<URI, byte[]>builder()
        .maximumSize(200)
        .expireAfterWrite(Duration.ofSeconds(30))
        .build();

byte[] body = responses.getOrLoad(uri, u -> client.get(u).body());
```

Keep the time to live short if the server sends `Cache-Control: no-cache`.
