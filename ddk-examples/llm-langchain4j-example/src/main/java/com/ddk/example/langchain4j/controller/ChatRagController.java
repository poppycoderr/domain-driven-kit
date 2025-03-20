package com.ddk.example.langchain4j.controller;

import com.ddk.example.langchain4j.service.ChatAssistant;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.ClassPathDocumentLoader;
import dev.langchain4j.data.document.splitter.DocumentByLineSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.PostConstruct;
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
@RequestMapping("/chat/rag")
@RequiredArgsConstructor
@RestController
public class ChatRagController {

    private final ChatAssistant chatAssistant;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;

    @PostConstruct
    public void loadStory() {
        List<Document> documents = ClassPathDocumentLoader.loadDocuments("rag");

        // EmbeddingStoreIngestor.ingest(documents, embeddingStore);
        EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(new DocumentByLineSplitter(100, 20))
                .build()
                .ingest(documents);

        log.info("Ingested {} documents, detail: {}", documents.size(), documents);
    }

    @GetMapping("/v1")
    public String ChatV1(String message) {
        return chatAssistant.chat(message);
    }
}
