package com.ddk.example.langchain4j.controller;

import com.ddk.example.langchain4j.service.ChatAssistant;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author Elijah Du
 * @date 2025/3/17
 */
@RequestMapping("/chat")
@RequiredArgsConstructor
@RestController
public class ChatController {

    private final ChatLanguageModel chatModel;
    private final ChatAssistant chatAssistant;

    @GetMapping("/v1")
    public String chatV1(String message) {
        return chatModel
                .chat(List.of(
                        SystemMessage.systemMessage("以json格式输出答案"),
                        UserMessage.userMessage(message)))
                .aiMessage()
                .text();
    }

    @GetMapping("/v2")
    public String chatV2(String message) {
        return chatAssistant.chat(message);
    }
}
