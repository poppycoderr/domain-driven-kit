# DDK Tracer Starter

Distributed tracing with Micrometer Tracing and OpenTelemetry, plus one DDK addition: every HTTP response carries its trace ID.

```text
HTTP request
  ServerHttpObservationFilter   HIGHEST_PRECEDENCE + 1   Spring Boot starts the request span
  TraceIdResponseFilter         HIGHEST_PRECEDENCE + 2   writes X-Trace-Id: <traceId>
  ... controller
```

A caller reporting a problem can hand over the header value, and the whole call chain is one search away in the tracing backend.

## Usage

```xml
<dependency>
    <groupId>com.ddk</groupId>
    <artifactId>ddk-tracer-starter</artifactId>
</dependency>
```

The starter brings in Spring Boot's `spring-boot-starter-opentelemetry` (the Micrometer Tracing OTel bridge and the OTLP exporter). Sampling and export stay under Spring Boot's own properties:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces
```

## Configuration

| Property | Default | Description |
|---|---|---|
| `ddk.tracer.response-header.enabled` | `true` | Write the trace ID into the response |
| `ddk.tracer.response-header.name` | `X-Trace-Id` | Header name |

Browsers can only read the header if CORS exposes it, e.g. `ddk.web.cors.exposed-headers=X-Trace-Id`.

## Behaviour

- The filter is registered only in servlet web applications that have a `Tracer` bean.
- The header is set before the rest of the filter chain runs, because headers added after the response is committed are silently dropped.
- Requests without an active span, for example when the observation filter is disabled, get no header.
