package com.ddk.example.langchain4j.config;

import com.ddk.example.langchain4j.service.ChatAssistant;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.chroma.ChromaEmbeddingStore;
import dev.langchain4j.web.search.searchapi.SearchApiWebSearchEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * @author Elijah Du
 * @date 2025/3/17
 */
@Configuration
@RequiredArgsConstructor
public class AssistantConfig {

    @Bean
    public ChatAssistant chatAssistant(ChatLanguageModel chatModel,
                                       EmbeddingStore<TextSegment> embeddingStore,
                                       EmbeddingModel embeddingModel,
                                       SearchApiWebSearchEngine engine) {
        return AiServices.builder(ChatAssistant.class)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))
                .contentRetriever(new EmbeddingStoreContentRetriever(embeddingStore, embeddingModel))
                // .tools(new Calculator())
                // .tools(new WebSearchTool(engine))
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // return new InMemoryEmbeddingStore<>();
        return ChromaEmbeddingStore.builder()
                .baseUrl("http://127.0.0.1:8000")
                .logRequests(true)
                .logResponses(false)
                .collectionName(UUID.randomUUID().toString())
                .build();
    }

    @Bean
    public SearchApiWebSearchEngine searchEngine() {
        return SearchApiWebSearchEngine.builder()
                .engine("google")
                .apiKey(System.getenv("SEARCH_APIKEY"))
                .build();
    }
}
