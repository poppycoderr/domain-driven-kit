package com.ddk.example.langchain4j.controller;

import com.ddk.example.langchain4j.service.ChatAssistant;
import com.ddk.example.langchain4j.tool.Calculator;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Elijah Du
 * @date 2025/3/17
 */
@Slf4j
@RequestMapping("/chat/function")
@RequiredArgsConstructor
@RestController
public class ChatFunctionController {

    private final ChatAssistant chatAssistant;
    private final ChatLanguageModel chatModel;

    @GetMapping("/v1")
    public String chatV1(String message) {
        List<ToolSpecification> specifications = ToolSpecifications.toolSpecificationsFrom(Calculator.class);
        ChatResponse chatResponse = chatModel.doChat(ChatRequest.builder()
                .messages(UserMessage.from(message))
                .parameters(ChatRequestParameters.builder().toolSpecifications(specifications).build())
                .build());
        if (chatResponse.aiMessage().hasToolExecutionRequests()){
            log.warn("Function Call - Calculator：{}", chatResponse.aiMessage().toolExecutionRequests());
            // manually execute ...
        }
        return chatResponse.aiMessage().text();
    }

    @GetMapping("v2")
    public String chatV2(String message) {
        return chatAssistant.chat(message);
    }
}
