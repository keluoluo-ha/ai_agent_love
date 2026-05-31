package com.hhk.aiagentlove.rag.eval;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class RagEvalMetricsResult {

    private RagEvalProfile profile;
    private int totalCases;
    private double hitAt5;
    private double mrrAt5;
    private double statusAccAt1;
    @Builder.Default
    private List<RagEvalCaseResult> caseResults = new ArrayList<>();

    public double relativeImprovementPercent(RagEvalMetricsResult baseline) {
        if (baseline == null || baseline.hitAt5 <= 0) {
            return 0;
        }
        return (hitAt5 - baseline.hitAt5) / baseline.hitAt5 * 100.0;
    }
}
