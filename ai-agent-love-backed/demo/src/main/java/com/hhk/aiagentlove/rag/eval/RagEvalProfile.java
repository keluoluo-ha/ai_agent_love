package com.hhk.aiagentlove.rag.eval;

import lombok.Getter;

/**
 * RAG 检索评测配置（对比不同基线）。
 */
@Getter
public enum RagEvalProfile {

    BASELINE_RAW(
            "Baseline-0 纯向量检索",
            false,
            false,
            false,
            0.0
    ),
    WITH_THRESHOLD(
            "Baseline-1 向量检索 + 相似度阈值",
            false,
            true,
            false,
            0.3
    ),
    WITH_STATUS_FILTER(
            "Baseline-2 向量检索 + status 过滤",
            true,
            true,
            false,
            0.3
    ),
    WITH_QUERY_EXPANSION(
            "Baseline-3 过滤 + Query 扩展",
            true,
            true,
            true,
            0.3
    ),
    FULL(
            "Ours 完整 RAG 检索链路",
            true,
            true,
            true,
            0.3
    );

    /**
     * 层级
     */
    private final String label;
    /**
     * 是否支持status过滤
     */
    private final boolean statusFilter;
    /**
     * 是否开启【相似度阈值过滤】
     */
    private final boolean similarityThresholdEnabled;
    /**
     * 是否支持Query 扩展
     */
    private final boolean queryExpansion;
    /**
     * 相似度阈值
     */
    private final double similarityThreshold;

    RagEvalProfile(
            String label,
            boolean statusFilter,
            boolean similarityThresholdEnabled,
            boolean queryExpansion,
            double similarityThreshold) {
        this.label = label;
        this.statusFilter = statusFilter;
        this.similarityThresholdEnabled = similarityThresholdEnabled;
        this.queryExpansion = queryExpansion;
        this.similarityThreshold = similarityThreshold;
    }
}
