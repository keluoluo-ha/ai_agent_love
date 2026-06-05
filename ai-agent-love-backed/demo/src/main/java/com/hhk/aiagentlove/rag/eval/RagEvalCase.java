package com.hhk.aiagentlove.rag.eval;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RagEvalCase {

    /**
     * 测试id
     */
    private String id;
    /**
     * 测试问题
     */
    private String question;
    /**
     * 测试对应状态 （单身，恋爱，已婚）
     */
    private String status;
    /**
     * 测试主题
     */
    private String topic;
    /**
     * 测试关键词
     */
    private List<String> goldKeywords = new ArrayList<>();
    /**
     * 测试匹配数
     */
    private int minKeywordMatches = 1;
}
