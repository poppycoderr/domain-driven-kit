package com.acme.order.eval;

import com.acme.order.business.event.OrderPlacedEvent;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hidden grader for the layer3-place-order task: checks the HTTP contract and the domain event stated in the prompt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class PlaceOrderGraderTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents events;

    @Test
    void placesReadsAndCancelsAnOrder() throws Exception {
        String id = place(7, 100);

        String found = body(mockMvc.perform(get("/orders/{id}", id)));
        assertThat(text(found, "$.data.id")).isEqualTo(id);
        assertThat(text(found, "$.data.customerId")).isEqualTo("7");
        assertThat(text(found, "$.data.amount")).isEqualTo("100");
        assertThat(text(found, "$.data.status")).isEqualTo("PLACED");

        String cancelled = body(mockMvc.perform(post("/orders/{id}/cancel", id)));
        assertThat(text(cancelled, "$.data.status")).isEqualTo("CANCELLED");
        assertThat(text(body(mockMvc.perform(get("/orders/{id}", id))), "$.data.status")).isEqualTo("CANCELLED");
    }

    @Test
    void anOrderThatIsNotPlacedCannotBeCancelled() throws Exception {
        String id = place(7, 100);
        mockMvc.perform(post("/orders/{id}/cancel", id));

        mockMvc.perform(post("/orders/{id}/cancel", id)).andExpect(jsonPath("$.code").value("ORDER_NOT_CANCELLABLE"));
        assertThat(text(body(mockMvc.perform(get("/orders/{id}", id))), "$.data.status")).isEqualTo("CANCELLED");
    }

    @Test
    void anUnknownOrderIsNotFound() throws Exception {
        mockMvc.perform(get("/orders/{id}", 424242)).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mockMvc.perform(post("/orders/{id}/cancel", 424242)).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        for (String json : List.of("{\"customerId\":7,\"amount\":0}", "{\"customerId\":7,\"amount\":-5}", "{\"customerId\":7}", "{\"amount\":100}",
                "{\"customerId\":0,\"amount\":100}")) {
            mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
    }

    @Test
    void placingAnOrderRaisesAnEventThatCarriesItsId() throws Exception {
        String id = place(8, 250);

        List<OrderPlacedEvent> raised = events.stream(OrderPlacedEvent.class).toList();
        assertThat(raised).hasSize(1);
        assertThat(raised.getFirst().orderId()).isNotNull();
        assertThat(String.valueOf(raised.getFirst().orderId().value())).isEqualTo(id);
    }

    @Test
    void ordersGetDistinctIds() throws Exception {
        assertThat(place(7, 100)).isNotEqualTo(place(7, 100));
    }

    private String place(long customerId, long amount) throws Exception {
        String json = "{\"customerId\":" + customerId + ",\"amount\":" + amount + "}";
        String response = body(mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk()));
        assertThat(text(response, "$.data.status")).isEqualTo("PLACED");
        return text(response, "$.data.id");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String text(String json, String path) {
        return String.valueOf((Object) JsonPath.read(json, path));
    }
}
