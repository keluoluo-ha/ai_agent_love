package com.hhk.aiagentlove.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.stereotype.Component;

/**
 * 重写转换器
 * 把usermessage再重写给ai
 */
@Component
public class RewriteTransformer {

    private final QueryTransformer queryTransformer;

    public RewriteTransformer(ChatModel dashscopeChatModel){

        ChatClient.Builder builder = ChatClient.builder(dashscopeChatModel);
       queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(builder)
                .build();

    }
    public String Rewrite(String message){
        Query query = new Query(message);
        Query transform = queryTransformer.transform(query);
        return transform.text();

    }

}
