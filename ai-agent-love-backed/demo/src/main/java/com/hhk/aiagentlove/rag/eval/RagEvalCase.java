package com.hhk.aiagentlove.rag.eval;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RagEvalCase {

    private String id;
    private String question;
    private String status;
    private String topic;
    private List<String> goldKeywords = new ArrayList<>();
    private int minKeywordMatches = 1;
}
