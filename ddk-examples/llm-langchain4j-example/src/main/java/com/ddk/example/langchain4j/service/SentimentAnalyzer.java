package com.ddk.example.langchain4j.service;

import dev.langchain4j.service.UserMessage;

/**
 * @author Elijah Du
 * @date 2025/3/16
 */
public interface SentimentAnalyzer {

    @UserMessage("Analyze sentiment of {{it}}")
    Sentiment analyzeSentimentOf(String text);
}

