package com.ddk.example.langchain4j.controller;

import com.ddk.example.langchain4j.service.ChatAssistant;
import com.ddk.example.langchain4j.structured.Person;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static dev.langchain4j.model.chat.request.ResponseFormatType.JSON;

/**
 * @author Elijah Du
 * @date 2025/3/17
 */
@RequestMapping("/chat/structured")
@RequiredArgsConstructor
@RestController
public class ChatStructuredController {

    private final ChatLanguageModel chatModel;
    private final ChatAssistant chatAssistant;

    @GetMapping("/v1")
    public String chatV1(String message) {
        ResponseFormat responseFormat = ResponseFormat.builder()
                .type(JSON)
                .jsonSchema(JsonSchema.builder()
                        .name("Person")
                        .rootElement(JsonObjectSchema.builder()
                                .addStringProperty("name")
                                .addIntegerProperty("age")
                                .addNumberProperty("height")
                                .addBooleanProperty("married")
                                .required("name", "age", "height", "married")
                                .build())
                        .build())
                .build();

        ChatResponse chat = chatModel.chat(ChatRequest.builder()
                .messages(UserMessage.from(message))
                .parameters(ChatRequestParameters.builder().responseFormat(responseFormat).build())
                .build());
        return chat.aiMessage().text();
    }

    @GetMapping("/v2")
    public Person chatV2(String message) {
        return chatAssistant.chatWithStructured(message);
    }
}
