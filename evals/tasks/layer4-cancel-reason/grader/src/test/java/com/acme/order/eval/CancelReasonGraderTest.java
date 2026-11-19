package com.acme.order.eval;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Hidden grader for the layer4-cancel-reason task.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CancelReasonGraderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aCancelReasonIsStoredAndReturned() throws Exception {
        String id = place();

        String cancelled = body(mockMvc.perform(post("/orders/{id}/cancel", id).param("reason", "  ordered by mistake ")));
        assertThat(text(cancelled, "$.data.status")).isEqualTo("CANCELLED");
        assertThat(text(cancelled, "$.data.cancelReason")).isEqualTo("ordered by mistake");

        String found = body(mockMvc.perform(get("/orders/{id}", id)));
        assertThat(text(found, "$.data.cancelReason")).isEqualTo("ordered by mistake");
    }

    @Test
    void aMissingOrBlankReasonIsNull() throws Exception {
        String first = place();
        String placed = body(mockMvc.perform(get("/orders/{id}", first)));
        assertThat(text(placed, "$.data.cancelReason")).isNull();
        mockMvc.perform(post("/orders/{id}/cancel", first));
        assertThat(text(body(mockMvc.perform(get("/orders/{id}", first))), "$.data.cancelReason")).isNull();

        String second = place();
        mockMvc.perform(post("/orders/{id}/cancel", second).param("reason", "   "));
        String found = body(mockMvc.perform(get("/orders/{id}", second)));
        assertThat(text(found, "$.data.status")).isEqualTo("CANCELLED");
        assertThat(text(found, "$.data.cancelReason")).isNull();
    }

    @Test
    void aReasonOfTwoHundredCharactersIsAcceptedAndOneMoreIsNot() throws Exception {
        String id = place();
        String tooLong = body(mockMvc.perform(post("/orders/{id}/cancel", id).param("reason", "x".repeat(201))));
        assertThat(text(tooLong, "$.code")).isEqualTo("CANCEL_REASON_TOO_LONG");
        assertThat(text(body(mockMvc.perform(get("/orders/{id}", id))), "$.data.status")).isEqualTo("PLACED");

        String accepted = body(mockMvc.perform(post("/orders/{id}/cancel", id).param("reason", " " + "y".repeat(200) + " ")));
        assertThat(text(accepted, "$.data.cancelReason")).isEqualTo("y".repeat(200));
        assertThat(text(body(mockMvc.perform(get("/orders/{id}", id))), "$.data.cancelReason")).isEqualTo("y".repeat(200));
    }

    private String place() throws Exception {
        String response = body(mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":7,\"amount\":100}"))
                .andExpect(status().isOk()));
        return text(response, "$.data.id");
    }

    private static String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    private static String text(String json, String path) {
        try {
            Object value = JsonPath.read(json, path);
            return value == null ? null : String.valueOf(value);
        } catch (PathNotFoundException e) {
            return null;
        }
    }
}
