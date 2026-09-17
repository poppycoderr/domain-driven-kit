package com.example.user.adapter;

import com.example.user.domain.event.UserDisabledEvent;
import com.example.user.domain.event.UserRegisteredEvent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class UserApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationEvents events;

    @Test
    void userLifecycle() throws Exception {
        String body = register("dave01", "13900139001").andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.phoneNumber").value("139****9001"))
                .andExpect(jsonPath("$.data.id").isString())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(JsonPath.read(body, "$.data.id"));
        assertThat(events.stream(UserRegisteredEvent.class)).hasSize(1);

        mockMvc.perform(put("/users/{id}", "not-a-number").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(put("/users/{id}", id).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"dave@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("dave01"))
                .andExpect(jsonPath("$.data.email").value("dave@example.com"));

        mockMvc.perform(patch("/users/{id}/disable", id)).andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}", id)).andExpect(jsonPath("$.data.enabled").value(false));
        assertThat(events.stream(UserDisabledEvent.class)).singleElement().satisfies(event -> assertThat(event.reason()).isNull());

        mockMvc.perform(patch("/users/{id}/enable", id)).andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}", id)).andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(delete("/users/{id}", id)).andExpect(status().isOk());
        mockMvc.perform(get("/users/{id}", id)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
    }

    @Test
    void disableAcceptsReasonAsQueryParameter() throws Exception {
        long id = registeredId("erin01", "13900139002");

        mockMvc.perform(patch("/users/{id}/disable", id).param("reason", "spam")).andExpect(status().isOk());

        assertThat(events.stream(UserDisabledEvent.class)).singleElement().satisfies(event -> assertThat(event.reason()).isEqualTo("spam"));
    }

    @Test
    void pagesSeedDataWithConditions() throws Exception {
        mockMvc.perform(post("/users/page").contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true,\"genders\":[1],\"pageSize\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[?(@.username == 'alice')]").exists())
                .andExpect(jsonPath("$.data.records[?(@.username == 'carol')]").doesNotExist())
                .andExpect(jsonPath("$.data.records[?(@.username == 'bobby')]").doesNotExist());
    }

    @Test
    void rejectsDuplicateUsername() throws Exception {
        register("alice", "13900139003").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    void rejectsMalformedRequest() throws Exception {
        mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"ab\",\"gender\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void domainInvariantViolationIsBadRequest() throws Exception {
        register("frank01", "12345").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PHONE_NUMBER"));
    }

    private ResultActions register(String username, String phoneNumber) throws Exception {
        String json = """
                {"username":"%s","password":"password123","gender":1,"phoneNumber":"%s"}
                """.formatted(username, phoneNumber);
        return mockMvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long registeredId(String username, String phoneNumber) throws Exception {
        String body = register(username, phoneNumber).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(JsonPath.read(body, "$.data.id"));
    }
}
