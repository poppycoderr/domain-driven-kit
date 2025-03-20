package com.ddk.example.langchain4j.controller;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Base64;
import java.util.List;

/**
 * @author Elijah Du
 * @date 2025/3/17
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/chat/image")
public class ChatImageController {

    private final ChatLanguageModel chatLanguageModel;

    @GetMapping
    public String chat(String message) throws IOException {
        File file = ResourceUtils.getFile("classpath:image/cat.png");
        byte[] bytes = Files.readAllBytes(file.toPath());
        UserMessage userMessage = UserMessage.from(
                TextContent.from(message),
                ImageContent.from(Base64.getEncoder().encodeToString(bytes), "image/png", ImageContent.DetailLevel.HIGH));
        return chatLanguageModel.chat(List.of(userMessage)).aiMessage().text();
    }
}
