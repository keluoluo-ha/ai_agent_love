package com.hhk.aiagentlove.rag;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.stereotype.Component;

@Component
public class QueryRewriter {
    private final ChatClient.Builder clientBuilder;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        this.clientBuilder = ChatClient.builder(dashscopeChatModel);
    }

    /**
     * 压缩查询转换器
     * @return
     */
    public Query CompressionQueryTransformer(){
        Query query = Query.builder()
                .text("And what is its second largest city?")
                .history(new UserMessage("What is the capital of Denmark?"),
                        new AssistantMessage("Copenhagen is the capital of Denmark."))
                .build();

        QueryTransformer queryTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(clientBuilder)
                .build();

        Query transform = queryTransformer.transform(query);
        String text = transform.text();
        return transform;
    }

    /**
     * 重写查询转换器
     * @return
     */
    public Query RewriteQueryTransformer(){
        Query query = new Query("I'm studying machine learning. What is an LLM?");

        QueryTransformer queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(clientBuilder)
                .build();

        return queryTransformer.transform(query);
    }

    /**
     * 翻译查询转换器
     * @return
     */
    public Query TranslationQueryTransformer(){
        Query query = new Query("Hvad er Danmarks hovedstad?");

        QueryTransformer queryTransformer = TranslationQueryTransformer.builder()
                .chatClientBuilder(clientBuilder)
                .targetLanguage("english")
                .build();

        Query transformedQuery = queryTransformer.transform(query);
        return transformedQuery;
    }

}
