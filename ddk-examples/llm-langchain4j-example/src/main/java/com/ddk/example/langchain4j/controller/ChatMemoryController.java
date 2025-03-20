package com.ddk.example.langchain4j.controller;

import com.ddk.example.langchain4j.service.ChatAssistant;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Elijah Du
 * @date 2025/3/16
 */
@RequestMapping("/chat/memory")
@RequiredArgsConstructor
@RestController
public class ChatMemoryController {

    private final ChatLanguageModel chatModel;
    private final ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);

    private final ChatAssistant chatAssistant;

    @GetMapping("/v1")
    public String chatV1(String message) {
        chatMemory.add(UserMessage.userMessage(message));
        ChatResponse response = chatModel.chat(chatMemory.messages());
        chatMemory.add(response.aiMessage());
        return response.aiMessage().text();
    }

    @GetMapping("/v2")
    public String chatV2(String memoryId, String message) {
        return chatAssistant.chat(memoryId, message);
    }

    @GetMapping("/v3")
    public String chatV3(String memoryId, String message) {
        // TODO: 多人对话-ChatMemoryStore
        return null;
    }
}
