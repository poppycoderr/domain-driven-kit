package com.ddk.tracer.starter;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 链路真的导出到了追踪后端：一个 Web 应用把 span 经 OTLP/HTTP 发给真实的 OpenTelemetry Collector，collector 收到的
 * span 里有请求本身和 {@code @Async} 方法里的那一段，都属于响应头 {@code X-Trace-Id} 给出的同一条链路，并带着应用名。
 * collector 的 debug 导出器把收到的每个 span 打印到日志里，测试读日志核对。
 */
@DisplayName("导出到 OpenTelemetry Collector")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = OtlpExportTest.TestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.application.name=ddk-tracer-otlp-test",
        "management.tracing.sampling.probability=1.0"
})
class OtlpExportTest {

    @Container
    static final GenericContainer<?> COLLECTOR = new GenericContainer<>("otel/opentelemetry-collector-contrib:0.162.0")
            .withCopyFileToContainer(MountableFile.forClasspathResource("otel-collector.yaml"), "/etc/otelcol-contrib/config.yaml")
            .withExposedPorts(4318)
            .waitingFor(Wait.forLogMessage(".*Everything is ready.*", 1));

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void collector(DynamicPropertyRegistry registry) {
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint",
                () -> "http://" + COLLECTOR.getHost() + ":" + COLLECTOR.getMappedPort(4318) + "/v1/traces");
    }

    @Test
    @DisplayName("请求和它的 @Async 工作导出为同一条链路，链路 ID 就是响应头里的那个")
    void theRequestAndItsAsyncWorkArriveAsOneTrace() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/orders/42")).build(),
                HttpResponse.BodyHandlers.ofString());
        String traceId = response.headers().firstValue("X-Trace-Id").orElseThrow();

        assertThat(response.body()).isEqualTo("order 42 checked");
        assertThat(traceId).matches("[0-9a-f]{32}");
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).ignoreExceptions().untilAsserted(() -> {
            String spans = COLLECTOR.getLogs();
            assertThat(occurrences(spans, "Trace ID       : " + traceId)).isGreaterThanOrEqualTo(2);
            assertThat(spans).contains("Name           : check-stock").contains("service.name: Str(ddk-tracer-otlp-test)");
        });
    }

    private static int occurrences(String text, String part) {
        return (int) Pattern.compile(Pattern.quote(part)).matcher(text).results().count();
    }

    @RestController
    static class OrderController {

        private final StockCheck stockCheck;

        OrderController(StockCheck stockCheck) {
            this.stockCheck = stockCheck;
        }

        @GetMapping("/orders/{id}")
        String check(@PathVariable("id") long id) {
            return stockCheck.check(id).join();
        }
    }

    static class StockCheck {

        private final ObservationRegistry observations;

        StockCheck(ObservationRegistry observations) {
            this.observations = observations;
        }

        @Async
        public CompletableFuture<String> check(long id) {
            return CompletableFuture.completedFuture(Observation.createNotStarted("check-stock", observations)
                    .observe(() -> "order " + id + " checked"));
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableAsync
    static class TestApplication {

        @Bean
        StockCheck stockCheck(ObservationRegistry observations) {
            return new StockCheck(observations);
        }

        @Bean
        OrderController orderController(StockCheck stockCheck) {
            return new OrderController(stockCheck);
        }
    }
}
