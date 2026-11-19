package com.example.inventory.adapter;

import com.ddk.test.containers.DdkContainers;
import com.ddk.test.containers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code monitoring/} 下的 Grafana 看板和告警规则用到的每个指标名，都要出现在这个应用的 {@code /actuator/prometheus} 里：
 * 指标改名或者换了单位，看板和告警会悄悄变空，这里让它当场失败。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:inventory-monitoring;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MonitoringMetricsTest {

    private static final Path MONITORING = Path.of("../../monitoring");

    private static final Pattern METRIC = Pattern.compile("\\bddk_[a-z_]+\\b");

    /**
     * 这个应用里不会出现的指标，以及它们出现的条件。
     */
    private static final Map<String, String> ELSEWHERE = Map.of(
            "ddk_event_publish_seconds_count", "an integration event sent to RocketMQ or to local delivery",
            "ddk_event_consume_seconds_count", "a message processed by a consumer group",
            "ddk_event_consume_failures", "a RocketMQ consumer group",
            "ddk_cache_remote_errors_total", "the first failed Redis operation of a cache");

    @Container
    static final RedisContainer REDIS = DdkContainers.redis();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getPort);
    }

    @Test
    void theDashboardAndTheAlertsReadMetricsThatExist() throws Exception {
        mockMvc.perform(get("/skus/1")).andExpect(status().isOk());
        mockMvc.perform(post("/skus/1/reservations").contentType(MediaType.APPLICATION_JSON).content("{\"requestId\":\"m-1\",\"quantity\":6}"))
                .andExpect(status().isOk());
        String scrape = "";
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (!scrape.contains("ddk_projection_refresh_seconds_count") && System.nanoTime() < deadline) {
            Thread.sleep(200);
            scrape = mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        }

        Set<String> used = usedMetrics();
        assertThat(used).as("metrics found in monitoring/").contains("ddk_projection_pending", "ddk_event_publications", "ddk_cache_access_total");
        assertThat(used).containsAll(ELSEWHERE.keySet());
        for (String metric : used) {
            if (!ELSEWHERE.containsKey(metric)) {
                assertThat(scrape).as("scrape contains %s", metric).containsPattern("(?m)^" + metric + "[{ ]");
            }
        }
    }

    private static Set<String> usedMetrics() throws IOException {
        Set<String> metrics = new TreeSet<>();
        for (Path file : new Path[] {MONITORING.resolve("grafana/ddk-overview.json"), MONITORING.resolve("prometheus/ddk-alerts.yml")}) {
            Matcher matcher = METRIC.matcher(Files.readString(file));
            while (matcher.find()) {
                metrics.add(matcher.group());
            }
        }
        return metrics;
    }
}
