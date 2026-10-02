package com.example.mall.order.adapter;

import com.example.mall.order.domain.event.OrderCancelledEvent;
import com.example.mall.order.domain.event.OrderPlacedEvent;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class OrderApiTest {

    private static final String TWO_LINES = """
            {"lines":[{"skuId":"SKU-KEYBOARD","quantity":1},{"skuId":"SKU-MOUSE","quantity":2}]}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void placeThenCancel() throws Exception {
        String body = place(7L, TWO_LINES).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.totalAmount").value(657.00))
                .andExpect(jsonPath("$.data.lines[0].productName").value("机械键盘"))
                .andExpect(jsonPath("$.data.lines[1].subtotal").value(258.00))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(JsonPath.read(body, "$.data.id"));
        assertThat(events.stream(OrderPlacedEvent.class)).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT create_by FROM t_order WHERE id = ?", Long.class, id)).isEqualTo(7L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_order_line WHERE order_id = ?", Integer.class, id)).isEqualTo(2);

        mockMvc.perform(get("/orders/{id}", id).header("X-Customer-Id", 7))
                .andExpect(jsonPath("$.data.lines.length()").value(2));

        cancel(7L, id, "不想要了").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelReason").value("不想要了"));
        assertThat(events.stream(OrderCancelledEvent.class)).hasSize(1);

        cancel(7L, id, "再取消一次").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        assertThat(events.stream(OrderCancelledEvent.class)).hasSize(1);
    }

    @Test
    void customersOnlySeeTheirOwnOrders() throws Exception {
        String body = place(8L, TWO_LINES).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(JsonPath.read(body, "$.data.id"));

        mockMvc.perform(get("/orders/{id}", id).header("X-Customer-Id", 9))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        cancel(9L, id, "不是我的").andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));

        mockMvc.perform(post("/orders/page").header("X-Customer-Id", 8).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customerId\":9}"))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].customerId").value("8"))
                .andExpect(jsonPath("$.data.records[0].lines.length()").value(2));
        mockMvc.perform(post("/orders/page").header("X-Customer-Id", 9).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.data.records.length()").value(0));
    }

    @Test
    void invalidRequestsAreRejectedWithErrorCodes() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(TWO_LINES))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CUSTOMER_REQUIRED"));
        place(7L, "{\"lines\":[]}").andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        place(7L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":0}]}").andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        place(7L, "{\"lines\":[{\"skuId\":\"SKU-GONE\",\"quantity\":1}]}").andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"));
        place(7L, "{\"lines\":[{\"skuId\":\"SKU-MOUSE\",\"quantity\":1},{\"skuId\":\"SKU-MOUSE\",\"quantity\":2}]}")
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    }

    private ResultActions place(long customerId, String body) throws Exception {
        return mockMvc.perform(post("/orders").header("X-Customer-Id", customerId).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions cancel(long customerId, long orderId, String reason) throws Exception {
        return mockMvc.perform(post("/orders/{id}/cancel", orderId).header("X-Customer-Id", customerId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + reason + "\"}"));
    }
}
