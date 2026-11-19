package com.example.inventory.adapter;

import com.ddk.test.containers.DdkContainers;
import com.ddk.test.containers.RedisContainer;
import com.example.inventory.adapter.web.SalesChannelResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code tenant} profile：销售渠道作为租户。与原生镜像冒烟测试的第二段检查同样的行为。
 * 用自己的内存库：同一个 JVM 里 {@code InventoryApiTest} 已经改过默认库里的库存。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:inventory-tenant;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("tenant")
@Testcontainers(disabledWithoutDocker = true)
class InventoryTenantApiTest {

    @Container
    static final RedisContainer REDIS = DdkContainers.redis();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redis;

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getPort);
    }

    @Test
    void requestIdsArePerChannelWhileStockIsShared() throws Exception {
        reserve("web", "r-1").andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(9));
        reserve("app", "r-1").andExpect(status().isOk()).andExpect(jsonPath("$.data.available").value(8));
        reserve("web", "r-1").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_REQUEST"));

        mockMvc.perform(as("app", get("/skus/1"))).andExpect(jsonPath("$.data.available").value(8));
        mockMvc.perform(as("web", get("/skus/1"))).andExpect(jsonPath("$.data.available").value(8));
        assertThat(redis.keys("inventory:idempotent:*")).anyMatch(key -> key.endsWith(":@web:r-1")).anyMatch(key -> key.endsWith(":@app:r-1"));
        assertThat(redis.keys("inventory:cache:skus::*")).containsExactly("inventory:cache:skus::1");
    }

    @Test
    void availabilityQuotasArePerChannel() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(as("web", get("/skus/2/availability"))).andExpect(status().isOk());
        }
        mockMvc.perform(as("web", get("/skus/2/availability"))).andExpect(status().isTooManyRequests());
        mockMvc.perform(as("app", get("/skus/2/availability"))).andExpect(status().isOk());
        // 没有渠道的调用共用一份额度
        mockMvc.perform(get("/skus/2/availability")).andExpect(status().isOk());
    }

    private ResultActions reserve(String channel, String requestId) throws Exception {
        return mockMvc.perform(as(channel, post("/skus/1/reservations")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + requestId + "\",\"quantity\":1}"));
    }

    private static MockHttpServletRequestBuilder as(String channel, MockHttpServletRequestBuilder request) {
        return request.header(SalesChannelResolver.HEADER, channel);
    }
}
