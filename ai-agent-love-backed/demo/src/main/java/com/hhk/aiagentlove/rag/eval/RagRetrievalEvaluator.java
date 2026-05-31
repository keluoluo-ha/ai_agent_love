package com.hhk.aiagentlove.rag.eval;

import com.hhk.aiagentlove.rag.queryExpander;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RagRetrievalEvaluator {

    private static final int TOP_K = 5;

    @Autowired
    @Qualifier("loveAppVectorStore")
    private VectorStore vectorStore;

    @Resource
    private queryExpander queryExpander;

    public RagEvalMetricsResult evaluateProfile(RagEvalProfile profile, List<RagEvalCase> cases) {
        List<RagEvalCaseResult> caseResults = new ArrayList<>();
        double hitSum = 0;
        double mrrSum = 0;
        double statusAccSum = 0;

        for (RagEvalCase evalCase : cases) {
            RagEvalCaseResult caseResult = evaluateCase(profile, evalCase);
            caseResults.add(caseResult);
            hitSum += caseResult.isHitAtK() ? 1 : 0;
            mrrSum += caseResult.getReciprocalRank();
            statusAccSum += caseResult.isStatusMatchAt1() ? 1 : 0;
        }

        int total = cases.size();
        return RagEvalMetricsResult.builder()
                .profile(profile)
                .totalCases(total)
                .hitAt5(hitSum / total)
                .mrrAt5(mrrSum / total)
                .statusAccAt1(statusAccSum / total)
                .caseResults(caseResults)
                .build();
    }

    public List<RagEvalMetricsResult> evaluateAllProfiles(List<RagEvalCase> cases) {
        List<RagEvalMetricsResult> results = new ArrayList<>();
        for (RagEvalProfile profile : RagEvalProfile.values()) {
            log.info("开始评测配置: {}", profile.getLabel());
            results.add(evaluateProfile(profile, cases));
        }
        return results;
    }

    private RagEvalCaseResult evaluateCase(RagEvalProfile profile, RagEvalCase evalCase) {
        String query = buildQuery(profile, evalCase.getQuestion());
        List<Document> retrieved = retrieve(profile, query, evalCase.getStatus());

        int firstHitRank = findFirstHitRank(retrieved, evalCase);
        boolean hitAtK = firstHitRank > 0;
        double reciprocalRank = hitAtK ? 1.0 / firstHitRank : 0.0;
        boolean statusMatchAt1 = !retrieved.isEmpty()
                && evalCase.getStatus().equals(String.valueOf(retrieved.get(0).getMetadata().get("status")));

        String top1Preview = retrieved.isEmpty()
                ? ""
                : truncate(retrieved.get(0).getText(), 80);

        return RagEvalCaseResult.builder()
                .caseId(evalCase.getId())
                .hitAtK(hitAtK)
                .reciprocalRank(reciprocalRank)
                .statusMatchAt1(statusMatchAt1)
                .retrievedCount(retrieved.size())
                .top1Preview(top1Preview)
                .build();
    }

    private String buildQuery(RagEvalProfile profile, String question) {
        if (!profile.isQueryExpansion()) {
            return question;
        }
        try {
            StringBuilder finalQuery = new StringBuilder(question);
            List<Query> expandedQueries = queryExpander.expand(question);
            for (Query expandedQuery : expandedQueries) {
                finalQuery.append("\n").append(expandedQuery.text());
            }
            return finalQuery.toString();
        } catch (Exception e) {
            log.warn("Query 扩展失败，回退原问题: {}", e.getMessage());
            return question;
        }
    }

    private List<Document> retrieve(RagEvalProfile profile, String query, String status) {
        int fetchSize = profile.isStatusFilter() ? TOP_K * 4 : TOP_K;
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(fetchSize);

        if (profile.isSimilarityThresholdEnabled()) {
            builder.similarityThreshold(profile.getSimilarityThreshold());
        }

        List<Document> documents = new ArrayList<>(vectorStore.similaritySearch(builder.build()));

        if (profile.isStatusFilter()) {
            documents = documents.stream()
                    .filter(doc -> status.equals(String.valueOf(doc.getMetadata().get("status"))))
                    .limit(TOP_K)
                    .toList();
        } else if (documents.size() > TOP_K) {
            documents = documents.subList(0, TOP_K);
        }

        return documents;
    }

    private int findFirstHitRank(List<Document> retrieved, RagEvalCase evalCase) {
        for (int i = 0; i < retrieved.size(); i++) {
            if (isRelevant(retrieved.get(i), evalCase)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * 命中规则：文档 status 与期望一致，且内容包含足够数量的 goldKeywords。
     */
    boolean isRelevant(Document document, RagEvalCase evalCase) {
        Map<String, Object> metadata = document.getMetadata();
        String docStatus = metadata == null ? null : String.valueOf(metadata.get("status"));
        if (!evalCase.getStatus().equals(docStatus)) {
            return false;
        }

        String content = document.getText() == null ? "" : document.getText();
        long matched = evalCase.getGoldKeywords().stream()
                .filter(content::contains)
                .count();
        return matched >= evalCase.getMinKeywordMatches();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ");
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen) + "...";
    }
}
