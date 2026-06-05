package com.hhk.aiagentlove.rag.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

@Component
public class RagEvalDatasetLoader {

    public static final String DEFAULT_DATASET = "rag/rag-eval-dataset.json";
    public static final String HARD_DATASET = "rag/rag-eval-dataset-hard.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<RagEvalCase> loadDefaultDataset() {
        return loadDataset(DEFAULT_DATASET);
    }

    public List<RagEvalCase> loadDataset(String classpathLocation) {
        try (InputStream inputStream = new ClassPathResource(classpathLocation).getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("加载 RAG 评测数据集失败: " + classpathLocation, e);
        }
    }
}
