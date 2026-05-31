package com.hhk.aiagentlove.rag.eval;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagEvalCaseResult {

    private String caseId;
    private boolean hitAtK;
    private double reciprocalRank;
    private boolean statusMatchAt1;
    private int retrievedCount;
    private String top1Preview;
}
