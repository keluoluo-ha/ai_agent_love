package com.hhk.aiagentlove.rag.eval;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
public class RagEvalReportWriter {

    public Path writeMarkdownReport(List<RagEvalMetricsResult> results, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        Files.writeString(outputPath, buildMarkdown(results), StandardCharsets.UTF_8);
        return outputPath;
    }

    public String buildMarkdown(List<RagEvalMetricsResult> results) {
        RagEvalMetricsResult baseline = results.stream()
                .filter(r -> r.getProfile() == RagEvalProfile.BASELINE_RAW)
                .findFirst()
                .orElse(results.get(0));

        StringBuilder sb = new StringBuilder();
        sb.append("# RAG 检索评测报告\n\n");
        sb.append("- 生成时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("- 测试集: `rag-eval-dataset.json`（30 条，含单身/恋爱/已婚各 10 条）\n");
        sb.append("- 指标: Hit@5、MRR@5、StatusAcc@1\n");
        sb.append("- 命中规则: Top5 中出现 **status 正确** 且包含 **goldKeywords** 的文档块\n\n");

        sb.append("## 总览对比\n\n");
        sb.append("| 配置 | Hit@5 | MRR@5 | StatusAcc@1 | 相对 Baseline-0 提升 |\n");
        sb.append("|------|-------|-------|-------------|----------------------|\n");
        for (RagEvalMetricsResult result : results) {
            sb.append("| ")
                    .append(result.getProfile().getLabel())
                    .append(" | ")
                    .append(formatPercent(result.getHitAt5()))
                    .append(" | ")
                    .append(formatRatio(result.getMrrAt5()))
                    .append(" | ")
                    .append(formatPercent(result.getStatusAccAt1()))
                    .append(" | ")
                    .append(formatRelative(result.relativeImprovementPercent(baseline)))
                    .append(" |\n");
        }

        sb.append("\n## 面试口述参考\n\n");
        RagEvalMetricsResult full = findProfile(results, RagEvalProfile.FULL);
        RagEvalMetricsResult thresholdBaseline = findProfile(results, RagEvalProfile.WITH_THRESHOLD);
        RagEvalMetricsResult rawBaseline = baseline;

        sb.append("> 我们在 30 条标注测试集上做了检索层离线评测（Hit@5 / MRR@5 / StatusAcc@1）。\n\n");

        if (full != null && thresholdBaseline != null) {
            double absGain = (full.getHitAt5() - thresholdBaseline.getHitAt5()) * 100;
            double relGain = thresholdBaseline.getHitAt5() > 0
                    ? full.relativeImprovementPercent(thresholdBaseline)
                    : 0;
            sb.append(String.format(Locale.CHINA,
                    "> **推荐对比基线**：在「向量检索 + 相似度阈值 0.73」下 Hit@5 为 %.1f%%；加入 status 过滤与 Query 扩展后提升到 %.1f%%（绝对提升 %.1f 个百分点，相对提升 %.1f%%）。\n\n",
                    thresholdBaseline.getHitAt5() * 100,
                    full.getHitAt5() * 100,
                    absGain,
                    relGain));
        }

        if (rawBaseline != null && full != null && full.getHitAt5() < rawBaseline.getHitAt5()) {
            sb.append(String.format(Locale.CHINA,
                    "> **注意**：纯向量检索（无阈值）Hit@5 为 %.1f%%，高于完整方案。说明当前阈值可能偏高，会牺牲召回；面试中应强调「过滤噪声 vs 提升召回」的权衡，而不是只报单一提升比例。\n",
                    rawBaseline.getHitAt5() * 100));
        }

        sb.append("## 未命中样本（完整方案）\n\n");
        if (full != null) {
            List<RagEvalCaseResult> misses = full.getCaseResults().stream()
                    .filter(r -> !r.isHitAtK())
                    .toList();
            if (misses.isEmpty()) {
                sb.append("全部命中。\n");
            } else {
                sb.append("| Case ID | Top1 预览 |\n");
                sb.append("|---------|----------|\n");
                for (RagEvalCaseResult miss : misses) {
                    sb.append("| ").append(miss.getCaseId()).append(" | ").append(escapePipe(miss.getTop1Preview())).append(" |\n");
                }
            }
        }

        sb.append("\n## 说明\n\n");
        sb.append("1. 该报告为**检索层**离线评测，不代表最终回答质量。\n");
        sb.append("2. Query 扩展配置会调用大模型，运行前请确认 API Key 可用。\n");
        sb.append("3. 可将 goldKeywords 调整为更严格（`minKeywordMatches=2`）做灵敏度分析。\n");
        return sb.toString();
    }

    public Path writeCsvDetail(RagEvalMetricsResult result, Path outputPath) throws IOException {
        Files.createDirectories(outputPath.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("caseId,hitAt5,reciprocalRank,statusMatchAt1,retrievedCount,top1Preview\n");
        for (RagEvalCaseResult caseResult : result.getCaseResults()) {
            sb.append(caseResult.getCaseId()).append(',')
                    .append(caseResult.isHitAtK()).append(',')
                    .append(String.format(Locale.US, "%.4f", caseResult.getReciprocalRank())).append(',')
                    .append(caseResult.isStatusMatchAt1()).append(',')
                    .append(caseResult.getRetrievedCount()).append(',')
                    .append('"').append(caseResult.getTop1Preview().replace("\"", "\"\"")).append('"')
                    .append('\n');
        }
        Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);
        return outputPath;
    }

    private String formatPercent(double value) {
        return String.format(Locale.CHINA, "%.1f%%", value * 100);
    }

    private String formatRatio(double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private String formatRelative(double value) {
        if (value > 0) {
            return String.format(Locale.CHINA, "+%.1f%%", value);
        }
        return String.format(Locale.CHINA, "%.1f%%", value);
    }

    private RagEvalMetricsResult findProfile(List<RagEvalMetricsResult> results, RagEvalProfile profile) {
        return results.stream()
                .filter(r -> r.getProfile() == profile)
                .findFirst()
                .orElse(null);
    }

    private String escapePipe(String text) {
        return text == null ? "" : text.replace("|", "\\|");
    }
}
