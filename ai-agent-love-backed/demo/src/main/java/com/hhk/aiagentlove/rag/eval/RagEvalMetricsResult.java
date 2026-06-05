package com.hhk.aiagentlove.rag.eval;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class RagEvalMetricsResult {


    /**
     * 检索效果测试
     */
    private RagEvalProfile profile;
    /**
     * 测试总数
     */
    private int totalCases;
    /**
     *前 5 条答案里，有没有正确答案
     */
    private double hitAt5;
    /**
     * 正确答案排在第几名？越靠前分越高
     */
    private double mrrAt5;
    /**
     * 只看第一条结果对不对
     */
    private double statusAccAt1;


    @Builder.Default
    private List<RagEvalCaseResult> caseResults = new ArrayList<>();

    public double relativeImprovementPercent(RagEvalMetricsResult baseline) {
        if (baseline == null || baseline.hitAt5 <= 0) {
            return 0;
        }
        //提升百分比 = (新分数 - 旧分数) ÷ 旧分数 × 100
        return (hitAt5 - baseline.hitAt5) / baseline.hitAt5 * 100.0;
    }
}
