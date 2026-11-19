package com.acme.order.eval;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hidden grader for the layer4-status-shortcut task. The architecture test, which the runner keeps unchanged, decides whether the shortcut was taken.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderStatusGraderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsTheStatusOfAnOrder() throws Exception {
        String placed = mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":7,\"amount\":100}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String id = String.valueOf((Object) JsonPath.read(placed, "$.data.id"));

        mockMvc.perform(get("/orders/{id}/status", id)).andExpect(jsonPath("$.data").value("PLACED"));
        mockMvc.perform(post("/orders/{id}/cancel", id));
        mockMvc.perform(get("/orders/{id}/status", id)).andExpect(jsonPath("$.data").value("CANCELLED"));
    }

    @Test
    void anUnknownOrderIsNotFound() throws Exception {
        mockMvc.perform(get("/orders/{id}/status", 424242)).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }
}
