package com.example.inventory.adapter;

import com.ddk.test.containers.DdkContainers;
import com.ddk.test.containers.RedisContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 与原生镜像冒烟测试（{@code scripts/native-smoke-inventory.sh}）检查同样的行为，这里跑在 JVM 上。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InventoryApiTest {

    @Container
    static final RedisContainer REDIS = DdkContainers.redis();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getPort);
    }

    @Test
    void reservationsAreLockedDeduplicatedCachedAndProjected() throws Exception {
        mockMvc.perform(get("/skus/1")).andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(10));
        jdbc.update("UPDATE t_sku SET name = 'renamed' WHERE id = 1");
        mockMvc.perform(get("/skus/1")).andExpect(jsonPath("$.data.name").value("apple"));

        reserve(1, "r-1", 6).andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(4));
        reserve(1, "r-1", 6).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));
        reserve(1, "r-2", 5).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("OUT_OF_STOCK"));
        reserve(1, "r-3", 0).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        reserve(9, "r-4", 1).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SKU_NOT_FOUND"));

        mockMvc.perform(get("/skus/1")).andExpect(jsonPath("$.data.name").value("renamed")).andExpect(jsonPath("$.data.available").value(4));
        eventually(() -> body(get("/low-stock")).contains("\"skuId\":\"1\""));
        eventually(() -> body(get("/skus/1/reservations")).contains("r-1"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL", Long.class)).isZero();
    }

    @Test
    void availabilityChecksAreRateLimitedPerSku() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/skus/2/availability")).andExpect(status().isOk());
        }
        mockMvc.perform(get("/skus/2/availability")).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }

    private ResultActions reserve(long sku, String requestId, int quantity) throws Exception {
        return mockMvc.perform(post("/skus/" + sku + "/reservations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + requestId + "\",\"quantity\":" + quantity + "}"));
    }

    private String body(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getContentAsString();
    }

    private static void eventually(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
        while (!condition.call()) {
            assertThat(System.nanoTime()).as("condition not met within 15 seconds").isLessThan(deadline);
            Thread.sleep(100);
        }
    }
}
