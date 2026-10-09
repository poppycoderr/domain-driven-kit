package com.example.mall.order.adapter;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单搜索查的是读模型。读模型在订单提交之后由后台刷新，所以断言都要等；每个用例用一个独立的顾客，互不干扰。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theReadModelFollowsAnOrderThroughItsLifeAndIsSearchableByProductName() throws Exception {
        long keyboards = place(61L, "{\"lines\":[{\"skuId\":\"SKU-KEYBOARD\",\"quantity\":1},{\"skuId\":\"SKU-MOUSE\",\"quantity\":2}]}");
        long mice = place(61L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> search(61L, "{\"status\":\"PENDING_PAYMENT\"}")
                .andExpect(jsonPath("$.data.total").value("2")));
        search(61L, "{\"keyword\":\"键盘\"}")
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].orderId").value(String.valueOf(keyboards)))
                .andExpect(jsonPath("$.data.records[0].productNames").value("机械键盘、无线鼠标"))
                .andExpect(jsonPath("$.data.records[0].itemCount").value(3))
                .andExpect(jsonPath("$.data.records[0].totalAmount").value(657.00));
        search(61L, "{\"keyword\":\"鼠标\"}").andExpect(jsonPath("$.data.records[0].orderId").value(String.valueOf(mice)))
                .andExpect(jsonPath("$.data.records.length()").value(2));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> mockMvc.perform(get("/payments/orders/{id}", mice)
                .header("X-Customer-Id", 61)).andExpect(jsonPath("$.data.status").value("PENDING")));
        mockMvc.perform(post("/payments/orders/{id}/pay", mice).header("X-Customer-Id", 61)).andExpect(status().isOk());
        mockMvc.perform(post("/orders/{id}/cancel", keyboards).header("X-Customer-Id", 61).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"不想要了\"}")).andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            search(61L, "{\"status\":\"PAID\"}").andExpect(jsonPath("$.data.records[0].orderId").value(String.valueOf(mice)));
            search(61L, "{\"status\":\"CANCELLED\"}").andExpect(jsonPath("$.data.records[0].orderId").value(String.valueOf(keyboards)));
            search(61L, "{\"status\":\"PENDING_PAYMENT\"}").andExpect(jsonPath("$.data.total").value("0"));
        });
    }

    @Test
    void customersOnlyFindTheirOwnOrdersAndUnknownStatusesAreRejected() throws Exception {
        place(62L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1}]}");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> search(62L, "{}").andExpect(jsonPath("$.data.total").value("1")));

        search(63L, "{}").andExpect(jsonPath("$.data.total").value("0"));
        search(62L, "{\"status\":\"SHIPPED\"}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_STATUS"));
        mockMvc.perform(post("/orders/search").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.code").value("CUSTOMER_REQUIRED"));
    }

    @Test
    void theReadModelCanBeRebuiltFromTheOrders() throws Exception {
        long orderId = place(64L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":2}]}");
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> search(64L, "{\"status\":\"PENDING_PAYMENT\"}")
                .andExpect(jsonPath("$.data.total").value("1")));
        long orders = jdbc.queryForObject("SELECT COUNT(*) FROM t_order", Long.class);
        // 读模型丢了：清空它，再留一条订单表里根本没有的脏数据
        jdbc.update("DELETE FROM t_order_search");
        search(64L, "{}").andExpect(jsonPath("$.data.total").value("0"));

        String body = mockMvc.perform(post("/orders/search/rebuild")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        // Web starter 把 long 写成字符串，前端不会丢精度
        assertThat(Long.parseLong(JsonPath.read(body, "$.data.marked"))).isEqualTo(orders);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> mockMvc.perform(get("/orders/search/status"))
                .andExpect(jsonPath("$.data.pending").value("0")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_order_search", Long.class)).isEqualTo(orders);
        search(64L, "{}").andExpect(jsonPath("$.data.records[0].orderId").value(String.valueOf(orderId)))
                .andExpect(jsonPath("$.data.records[0].status").value("PENDING_PAYMENT"));
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
