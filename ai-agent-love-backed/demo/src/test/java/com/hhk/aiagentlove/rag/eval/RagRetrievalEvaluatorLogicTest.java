package com.hhk.aiagentlove.rag.eval;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRetrievalEvaluatorLogicTest {

    private final RagRetrievalEvaluator evaluator = new RagRetrievalEvaluator();

    @Test
    void relevantWhenStatusAndKeywordMatch() {
        RagEvalCase evalCase = new RagEvalCase();
        evalCase.setStatus("恋爱");
        evalCase.setGoldKeywords(List.of("未来规划", "共同目标"));
        evalCase.setMinKeywordMatches(1);

        Document doc = new Document(
                "选择合适时机沟通未来规划，并制定共同目标。",
                Map.of("status", "恋爱")
        );

        assertTrue(evaluator.isRelevant(doc, evalCase));
    }

    @Test
    void notRelevantWhenStatusMismatch() {
        RagEvalCase evalCase = new RagEvalCase();
        evalCase.setStatus("恋爱");
        evalCase.setGoldKeywords(List.of("未来规划"));
        evalCase.setMinKeywordMatches(1);

        Document doc = new Document(
                "婚后夫妻消费观念不同，如何协调理财规划？",
                Map.of("status", "已婚")
        );

        assertFalse(evaluator.isRelevant(doc, evalCase));
    }
}
