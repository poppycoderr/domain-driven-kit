# DDK Tracer Starter

This starter simplifies the integration of distributed tracing into your DDK application using Micrometer Tracing and OpenTelemetry.

## Features

- Brings in essential dependencies for distributed tracing:
    - `micrometer-tracing-bridge-otel` (for OpenTelemetry bridge)
    - `opentelemetry-exporter-otlp` (for exporting traces via OTLP protocol, e.g., to Jaeger, Prometheus, or OpenTelemetry Collector)
- Relies on Spring Boot's auto-configuration for Micrometer Tracing.
- Includes a basic `TracerAutoConfiguration` class that can be expanded for DDK-specific tracing defaults if needed.

## Prerequisites

- Your application should be a Spring Boot application.

## How to Use

1.  **Include the starter in your project's `pom.xml`:**

    ```xml
    <dependency>
        <groupId>com.ddk</groupId>
        <artifactId>ddk-tracer-starter</artifactId>
        <version>${ddk.version}</version> <!-- Ensure this matches your project's ddk version -->
    </dependency>
    ```

2.  **Configure Tracing Properties:**

    Spring Boot Actuator and Micrometer Tracing provide various properties to configure tracing behavior. Add these to your `application.properties` or `application.yml`:

    **Required:**
    ```properties
    # Sets the name of your application, which will be used as the service name in traces
    spring.application.name=your-application-name
    ```

    **Exporter Configuration (OTLP - default with this starter):**
    ```properties
    # Endpoint for the OTLP exporter (e.g., OpenTelemetry Collector, Jaeger with OTLP receiver)
    management.otlp.tracing.endpoint=http://localhost:4318/v1/traces
    # management.otlp.tracing.protocol=grpc # or http/protobuf
    ```

    **Sampling:**
    ```properties
    # Probability-based sampling (1.0 means sample all traces, 0.1 means sample 10%)
    management.tracing.sampling.probability=1.0
    ```

    Refer to the [Spring Boot documentation on Observability](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.observability) and [Micrometer Tracing documentation](https://micrometer.io/docs/tracing) for more configuration options.

3.  **Verify Tracing:**

    - Run your application.
    - If correctly configured, traces should be exported to your configured OTLP endpoint.
    - You can use tools like Jaeger UI or Prometheus to view the traces.
    - Spring Boot will automatically instrument many common components like web requests (with `spring-web`), scheduled tasks, etc.

## Example: Using Tracer in your code (Optional)

While much tracing is automatic, you can inject the `io.micrometer.tracing.Tracer` to create custom spans:

```java
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MyService {

    private final Tracer tracer;

    @Autowired
    public MyService(Tracer tracer) {
        this.tracer = tracer;
    }

    public void doWork() {
        Span newSpan = this.tracer.nextSpan().name("myCustomWork").start();
        try (Tracer.SpanInScope ws = this.tracer.withSpan(newSpan)) {
            // Your custom work here
            System.out.println("Doing custom work in a new span!");
        } finally {
            newSpan.end();
        }
    }
}
```

This starter aims to provide the basic setup. For advanced configuration or specific exporter needs (e.g., Zipkin directly, Jaeger via other protocols), you might need to add more dependencies and configurations.
