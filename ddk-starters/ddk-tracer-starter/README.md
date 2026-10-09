# DDK Tracer Starter

Distributed tracing with Micrometer Tracing and OpenTelemetry, plus two DDK additions: every HTTP response carries its trace ID, and the trace follows work into Spring's task executors.

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

The starter brings in Spring Boot's `spring-boot-starter-opentelemetry` (the Micrometer Tracing OTel bridge and the OTLP exporter). Sampling and export stay under Spring Boot's own properties. Spring Boot 4.1 moved the OTLP trace settings to `management.opentelemetry.tracing.export.otlp.*`; the older `management.otlp.tracing.*` names are deprecated:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  opentelemetry:
    tracing:
      export:
        otlp:
          endpoint: http://otel-collector:4318/v1/traces
```

## Configuration

| Property | Default | Description |
|---|---|---|
| `ddk.tracer.response-header.enabled` | `true` | Write the trace ID into the response |
| `ddk.tracer.response-header.name` | `X-Trace-Id` | Header name |
| `ddk.tracer.async-propagation.enabled` | `true` | Carry the trace context into Spring's task executors |

Browsers can only read the header if CORS exposes it, e.g. `ddk.web.cors.exposed-headers=X-Trace-Id`.

## Traces across threads and messages

Without help, work that moves to another thread starts a new trace. The starter registers a `ContextPropagatingTaskDecorator`, which Spring Boot applies to the task executors it creates, so `@Async` methods stay on the caller's trace.

This is also what keeps integration events on the trace. `ddk-event-starter` delivers events on the task executor after the transaction commits, writes the trace context into the message headers, and picks it up again on the consuming side:

```text
POST /orders                         trace 4bf9...
  └─ order-events publish            same trace, on the task executor
       └─ order-events process       same trace, in the consuming service
            └─ stock-events publish  ...and onwards
```

An application that declares its own `TaskDecorator` keeps it; DDK then registers nothing.

## Behaviour

- The filter is registered only in servlet web applications that have a `Tracer` bean.
- The header is set before the rest of the filter chain runs, because headers added after the response is committed are silently dropped.
- Requests without an active span, for example when the observation filter is disabled, get no header.
