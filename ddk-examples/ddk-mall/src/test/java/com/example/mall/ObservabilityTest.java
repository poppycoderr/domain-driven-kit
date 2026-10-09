package com.example.mall;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 默认 profile 不向外上报，但链路和指标照常产生：响应带着 traceId，上下文之间的每一条消息都计入指标。
 * 链路跨消息的接续在 DDK 的事件 starter 里用真实的 RocketMQ 验证过，这里不重复。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void responsesCarryTheirTraceIdAndMessagesBetweenContextsAreMeasured() throws Exception {
        MvcResult placed = mockMvc.perform(post("/orders").header("X-Customer-Id", 51).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(placed.getResponse().getHeader("X-Trace-Id")).matches("[0-9a-f]{32}");
        long orderId = Long.parseLong(JsonPath.read(placed.getResponse().getContentAsString(), "$.data.id"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/orders/{id}", orderId).header("X-Customer-Id", 51))
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT")));

        mockMvc.perform(get("/actuator/metrics/ddk.event.consume").param("tag", "messaging.destination.name:mall-order-events")
                        .param("tag", "messaging.consumer.group.name:mall-inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[?(@.statistic == 'COUNT')].value").value(org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(1.0))));
        mockMvc.perform(get("/actuator/metrics/ddk.event.publish").param("tag", "messaging.destination.name:mall-inventory-events"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics/ddk.cache.access").param("tag", "cache:product")).andExpect(status().isOk());
    }
}
