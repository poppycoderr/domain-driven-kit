package com.example.mall.order.infrastructure;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.ddk.test.containers.DdkContainers;
import com.example.mall.order.domain.acl.OrderSearchIndex;
import com.example.mall.order.infrastructure.search.ElasticsearchOrderSearchIndex;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单搜索换成 Elasticsearch：同样的接口、同样的投影，只换了 {@link OrderSearchIndex} 的实现。
 * 其余部分仍是默认 profile（内存 H2、进程内转发），所以这里验证的就是「换存储只换一个类」。
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest(properties = {"mall.search.store=elasticsearch", "mall.search.index=mall-orders-test"})
@AutoConfigureMockMvc
class OrderSearchElasticsearchIntegrationTest {

    @Container
    static final ElasticsearchContainer ELASTICSEARCH = DdkContainers.elasticsearch();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderSearchIndex searchIndex;

    @Autowired
    private ElasticsearchClient elasticsearch;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void elasticsearch(DynamicPropertyRegistry registry) {
        registry.add("spring.elasticsearch.uris", () -> "http://" + ELASTICSEARCH.getHttpHostAddress());
    }

    @Test
    void ordersAreIndexedSearchedByProductNameAndFollowTheirStatus() throws Exception {
        assertThat(searchIndex).isInstanceOf(ElasticsearchOrderSearchIndex.class);
        long keyboards = place(71L, "{\"lines\":[{\"skuId\":\"SKU-KEYBOARD\",\"quantity\":1},{\"skuId\":\"SKU-MOUSE\",\"quantity\":2}]}");
        long mice = place(71L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}");

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> search(71L, "{\"status\":\"PENDING_PAYMENT\"}")
                .andExpect(jsonPath("$.data.total").value("2")));
        search(71L, "{\"keyword\":\"键盘\"}")
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].orderId").value(String.valueOf(keyboards)))
                .andExpect(jsonPath("$.data.records[0].productNames").value("机械键盘、无线鼠标"))
                .andExpect(jsonPath("$.data.records[0].totalAmount").value(657.00))
                .andExpect(jsonPath("$.data.records[0].expiresAt").isNotEmpty());
        search(71L, "{\"keyword\":\"鼠标\"}").andExpect(jsonPath("$.data.records.length()").value(2))
                .andExpect(jsonPath("$.data.records[0].orderId").value(String.valueOf(mice)));
        search(71L, "{\"keyword\":\"显示器\"}").andExpect(jsonPath("$.data.total").value("0"));
        search(72L, "{}").andExpect(jsonPath("$.data.total").value("0"));
        search(71L, "{\"pageSize\":1,\"pageNum\":2}").andExpect(jsonPath("$.data.total").value("2"))
                .andExpect(jsonPath("$.data.records[0].orderId").value(String.valueOf(keyboards)));

        mockMvc.perform(post("/orders/{id}/cancel", keyboards).header("X-Customer-Id", 71).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"不想要了\"}")).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> search(71L, "{\"status\":\"CANCELLED\"}")
                .andExpect(jsonPath("$.data.records[0].orderId").value(String.valueOf(keyboards))));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_order_search WHERE customer_id = 71", Long.class))
                .as("the database read model is not written when Elasticsearch is the store").isZero();
    }

    @Test
    void theIndexCanBeRebuiltAfterItsDocumentsAreLost() throws Exception {
        long orderId = place(73L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":2}]}");
        // 等订单走到「待支付」、并且读模型没有剩余的刷新，再删文档；否则还在路上的那次刷新会把文档写回来
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> search(73L, "{\"status\":\"PENDING_PAYMENT\"}")
                .andExpect(jsonPath("$.data.total").value("1")));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> mockMvc.perform(get("/orders/search/status"))
                .andExpect(jsonPath("$.data.pending").value("0")));
        elasticsearch.deleteByQuery(request -> request.index("mall-orders-test").query(query -> query.matchAll(all -> all)).refresh(true));
        search(73L, "{}").andExpect(jsonPath("$.data.total").value("0"));

        mockMvc.perform(post("/orders/search/rebuild")).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> search(73L, "{}")
                .andExpect(jsonPath("$.data.records[0].orderId").value(String.valueOf(orderId))));
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    private long place(long customerId, String body) throws Exception {
        String response = mockMvc.perform(post("/orders").header("X-Customer-Id", customerId).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(JsonPath.read(response, "$.data.id"));
    }

    private ResultActions search(long customerId, String body) throws Exception {
        return mockMvc.perform(post("/orders/search").header("X-Customer-Id", customerId).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
