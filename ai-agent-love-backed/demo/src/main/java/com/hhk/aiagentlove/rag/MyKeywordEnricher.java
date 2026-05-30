package com.hhk.aiagentlove.rag;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 元数据rag增强器
 */
@Component
class MyKeywordEnricher {

    private final ChatModel dashscopeChatModel;

    MyKeywordEnricher(ChatModel chatModel) {
        this.dashscopeChatModel = chatModel;
    }

    List<Document> enrichDocuments(List<Document> documents) {
        KeywordMetadataEnricher enricher = KeywordMetadataEnricher.builder(dashscopeChatModel)
                .keywordCount(5)
                .build();

        return enricher.apply(documents);
    }
}