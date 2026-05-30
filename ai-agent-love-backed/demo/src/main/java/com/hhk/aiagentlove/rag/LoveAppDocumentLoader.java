package com.hhk.aiagentlove.rag;


import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;



import org.springframework.core.io.Resource;

import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;



import java.util.ArrayList;
import java.util.List;

/**
 * 文档加载
 */
@Component
@Slf4j
class LoveAppDocumentLoader {


    private final ResourcePatternResolver resourcePatternResolver;



    LoveAppDocumentLoader(ResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
    }



            List<Document> loadMarkdown(){
                List<Document> documents=new ArrayList<>();

                try {
                    Resource[] resources=resourcePatternResolver.getResources("classpath:document/*.md");
                    for(Resource resource:resources){
                        String filename = resource.getFilename();
                        String status = filename.substring(filename.length() - 6, filename.length() - 4);
                        MarkdownDocumentReaderConfig readerConfig = MarkdownDocumentReaderConfig.builder()
                                .withHorizontalRuleCreateDocument(true)
                                .withIncludeCodeBlock(true)
                                .withIncludeBlockquote(true)
                                .withAdditionalMetadata("filename", filename)
                                .withAdditionalMetadata("status", status)
                                .build();
                        MarkdownDocumentReader markdownDocumentReader = new MarkdownDocumentReader(resource, readerConfig);
                        List<Document> documents1 = markdownDocumentReader.get();
                        documents.addAll(documents1);
                    }

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                    return documents;
            }




}
