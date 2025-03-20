package com.ddk.example.langchain4j.service;

import com.ddk.example.langchain4j.structured.Person;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * @author Elijah Du
 * @date 2025/3/17
 */
public interface ChatAssistant {

   @SystemMessage("以法语格式输出")
    String chat(String message);

    String chat(@MemoryId String memoryId, @UserMessage String message);

    Person chatWithStructured(String message);
}
