package com.hhk.aiagentlove.rag.eval;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RagEvalCaseResult {

    /**
     * 测试编号
     */
    private String caseId;
    /**
     * 是否找到正确答案
     */
    private boolean hitAtK;
    /**
     * 测试排名分数
     */
    private double reciprocalRank;
    /**
     * 状态对不对
     */
    private boolean statusMatchAt1;
    /**
     *一共找到了多少条资料
     */
    private int retrievedCount;
    /**
     *第一条结果的内容预览
     */
    private String top1Preview;
}
