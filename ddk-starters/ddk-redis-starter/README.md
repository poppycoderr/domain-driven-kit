# DDK Redis Starter

A `RedisTemplate<String, Object>` with JSON values that remember their type, and an allow-list that decides which types may be read back.

## In 30 seconds

1. Add the dependency and point Spring Boot at Redis:

   ```xml
   <dependency>
       <groupId>com.domaindrivenkit</groupId>
       <artifactId>ddk-redis-starter</artifactId>
   </dependency>
   ```

   ```yaml
   spring:
     data:
       redis:
         host: localhost
         port: 6379
   ```

2. Inject `RedisTemplate<String, Object>` and write a record from your application's package: `redis.opsForValue().set("user:1", user)`.
3. Check it: `redis-cli GET user:1` shows JSON with an `"@class"` property, and `opsForValue().get("user:1")` returns the record, not a map.

## What it does

- **Keys** and hash keys are strings; **values** and hash values are JSON written by Jackson 3. Fields are read and written directly, getters are ignored, because domain objects may not have getters and a getter may compute something.
- **Types are written into the data** as an `@class` property, except for final JDK scalars (`String`, `Long`…), enums and `JsonNode`. Records come back as the same record.
- **Reading is limited to an allow-list**:
  1. common JDK packages: `java.lang`, `java.util`, `java.time`, `java.math`; packages that can do I/O, such as `java.io` and `java.net`, are left out on purpose;
  2. your application's root package, the one with `@SpringBootApplication`, which usually covers your own DTOs without configuration;
  3. the packages in `ddk.redis.trusted-packages`.

  Any other type fails to read. Whoever can write to Redis cannot make the application instantiate an arbitrary class on the classpath through `@class`.

```text
write  User(record) -> {"@class":"com.example.app.User","id":1,...}
read   @class allowed?  -- yes --> User
                        \- no  --> SerializationException, nothing instantiated
```

The connection factory, the pool and `StringRedisTemplate` still come from Spring Boot. The starter only provides the bean named `redisTemplate`, before Spring Boot's own, and your own `redisTemplate` bean replaces it. Without a connection factory the application still starts; only code that uses `redisTemplate` fails.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `ddk.redis.trusted-packages` | empty | More packages, matched by prefix, whose types may be read, for example shared DTOs in another module |
| `spring.data.redis.*` | Spring Boot's | The connection |

`spring.jackson.*` does not apply: values in Redis need type information and the allow-list, which web JSON must not have.

## Common errors

| Symptom | Cause | Fix |
|---|---|---|
| `SerializationException` when reading a value | Its `@class` is outside the allow-list, typically a DTO from another module or library | Add the package to `ddk.redis.trusted-packages` |
| Values written before a class was renamed or moved cannot be read | The class name is part of the stored data | Migrate or expire the old keys; for long-lived or cross-language data use a serializer with an explicit value type |
| A small `Long` comes back as `Integer` | Scalars carry no type information | Read numbers as `Number` and convert |
| `List.of(...)` cannot be read back | Immutable JDK collections have no usable constructor | Store `ArrayList` / `HashMap` |
| Every read or write throws `RedisConnectionFailureException` | Redis is unreachable; connections are lazy, so startup did not fail | Check `spring.data.redis.*`; the starter has no fallback |

The [cache starter](../ddk-cache-starter) uses its own serialization, so a value cached with `@Cacheable` cannot be read through `redisTemplate`, and the other way round.
