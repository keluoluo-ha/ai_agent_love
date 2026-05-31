package com.hhk.aiagentlove.rag.eval;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.util.List;

/**
 * RAG 检索离线评测。
 *
 * 运行方式：
 * mvn -Dtest=RagRetrievalEvalTest test
 *
 * 报告输出：
 * target/rag-eval/rag-eval-report.md
 * target/rag-eval/full-profile-detail.csv
 */
@Slf4j
@SpringBootTest
class RagRetrievalEvalTest {

    @Resource
    private RagEvalDatasetLoader datasetLoader;

    @Resource
    private RagRetrievalEvaluator retrievalEvaluator;

    @Resource
    private RagEvalReportWriter reportWriter;

    @Test
    void runRetrievalEvaluation() throws Exception {
        List<RagEvalCase> dataset = datasetLoader.loadDefaultDataset();
        List<RagEvalMetricsResult> results = retrievalEvaluator.evaluateAllProfiles(dataset);

        Path reportPath = Path.of("target", "rag-eval", "rag-eval-report.md");
        reportWriter.writeMarkdownReport(results, reportPath);

        RagEvalMetricsResult fullResult = results.stream()
                .filter(r -> r.getProfile() == RagEvalProfile.FULL)
                .findFirst()
                .orElseThrow();
        Path csvPath = Path.of("target", "rag-eval", "full-profile-detail.csv");
        reportWriter.writeCsvDetail(fullResult, csvPath);

        log.info("===== RAG 检索评测完成 =====");
        for (RagEvalMetricsResult result : results) {
            log.info("{} -> Hit@5={}%, MRR@5={}, StatusAcc@1={}%",
                    result.getProfile().getLabel(),
                    String.format("%.1f", result.getHitAt5() * 100),
                    String.format("%.3f", result.getMrrAt5()),
                    String.format("%.1f", result.getStatusAccAt1() * 100));
        }
        log.info("Markdown 报告: {}", reportPath.toAbsolutePath());
        log.info("CSV 明细: {}", csvPath.toAbsolutePath());
    }
}
