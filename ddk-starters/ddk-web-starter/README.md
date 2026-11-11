# DDK Web Starter

Web-layer defaults for Spring MVC: global exception handling into `ApiResponse`, Jackson defaults applied as a customizer, opt-in CORS, the error contract in the OpenAPI docs, and the operator of each request.

## In 30 seconds

1. Add the dependency (the version comes from the `ddk-dependencies` BOM):

   ```xml
   <dependency>
       <groupId>com.domaindrivenkit</groupId>
       <artifactId>ddk-web-starter</artifactId>
   </dependency>
   ```

2. No configuration is needed.
3. Check it: throw `new BusinessException(CommonError.VALIDATION_ERROR)` from a controller, or call an endpoint with a broken JSON body. The response is `400` with an `ApiResponse` body, `{"code":"VALIDATION_ERROR","message":"...","data":null,"timestamp":...}` (or `MALFORMED_REQUEST` for the broken body), instead of Spring's default error page.

## What it does

| Exception | Status | `code` |
|---|---|---|
| `BusinessException` | 400 | the business error code, message filled from its arguments |
| `ConcurrentUpdateException`, `AggregateBusyException`, `DuplicateRequestException` | 409 | `CONCURRENT_UPDATE`, `AGGREGATE_BUSY`, `DUPLICATE_REQUEST` |
| `RateLimitedException` | 429 | `RATE_LIMITED` |
| `CancellationException`, for example a thread interrupted while it waits for an aggregate lock during shutdown | 503 | `REQUEST_CANCELLED`; the request itself was fine and can be retried |
| `SystemException` | 500 | the system error code |
| `BindException` (including `@Valid` bodies), `ConstraintViolationException`, `TypeMismatchException` | 400 | `VALIDATION_ERROR`, naming each field |
| `HttpMessageNotReadableException` (broken JSON) | 400 | `MALFORMED_REQUEST` |
| Spring MVC's own `ErrorResponse` (404, 405, 415…) | its own status | the status name, for example `METHOD_NOT_ALLOWED` |
| anything else | 500 | `SYSTEM_ERROR`, with a generic message; the stack trace goes to the log |

- **Jackson.** A `JsonMapperBuilderCustomizer`, so `spring.jackson.*` still applies. Dates are ISO-8601 strings, unknown request fields are ignored, and `Long` / `long` are written as JSON strings, because snowflake IDs above 2^53 lose precision in JavaScript.
- **Error contract in the API docs.** With springdoc on the classpath, every operation gets `400`, `409`, `429`, `500` and `503` responses with the `ApiErrorResponse` schema, and `components.schemas.ErrorCode` lists every error code: `CommonError` plus the `ErrorCode` enums in your application's packages.
- **Operator of the request.** Declare an `OperatorResolver` bean and the starter sets `OperatorContext` for each request and clears it afterwards. The MyBatis starter's audit fields and multi-tenancy read it from there.
- **CORS** is off until `ddk.web.cors.enabled=true`.

The starter brings in `spring-boot-starter-webmvc`, `spring-boot-starter-validation` and `ddk-core`. It does not support WebFlux.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `ddk.web.exception-handler` | `true` | Register `BaseExceptionHandler` |
| `ddk.web.jackson` | `true` | Apply the Jackson defaults |
| `ddk.web.write-long-as-string` | `true` | Write `Long` / `long` as strings; needs `ddk.web.jackson` |
| `ddk.web.openapi` | `true` | With springdoc present, add the error responses and the error code list to the docs |
| `ddk.web.cors.enabled` | `false` | Turn on CORS |
| `ddk.web.cors.path-pattern` | `/**` | Paths it applies to |
| `ddk.web.cors.allowed-origins` | empty | Allowed origins; patterns such as `https://*.acme.com` work |
| `ddk.web.cors.allowed-methods` | `GET, POST, PUT, PATCH, DELETE, OPTIONS` | Allowed methods |
| `ddk.web.cors.allowed-headers` | `*` | Allowed request headers |
| `ddk.web.cors.exposed-headers` | empty | Response headers the browser may read, for example `X-Trace-Id` |
| `ddk.web.cors.allow-credentials` | `false` | Allow credentials |
| `ddk.web.cors.max-age` | `1h` | How long the browser caches a preflight result |

Your own `BaseExceptionHandler` bean replaces DDK's. The auto-configuration is `com.ddk.web.config.DdkWebAutoConfiguration`.

## Common errors

| Symptom | Cause | Fix |
|---|---|---|
| Startup fails: `ddk.web.cors 同时设置了 allow-credentials=true 和匹配任意主机的来源 ...` | An origin pattern whose host is only wildcards, such as `*`, `https://*` or `*://*`, together with credentials: any site could call the API with the user's credentials | List the origins, or use patterns that fix the domain, such as `https://*.example.com` |
| The frontend receives IDs as strings | `write-long-as-string` is on by default, for every `Long` field | Parse them as strings, or set `ddk.web.write-long-as-string=false` if no ID exceeds 2^53 |
| The API docs show only `200` responses | springdoc is not on the classpath, or `ddk.web.openapi=false` | Add `springdoc-openapi-starter-webmvc-ui`; the BOM manages its version |
| A request returns `500 SYSTEM_ERROR` with no detail | An exception that DDK does not map; the response hides it on purpose | Read the stack trace in the log; throw `BusinessException` with an error code for expected failures |
| `OperatorContext.current()` is empty in a request | No `OperatorResolver` bean, or it returns `null` | Declare one that reads the user from Spring Security, a gateway header or a JWT |
