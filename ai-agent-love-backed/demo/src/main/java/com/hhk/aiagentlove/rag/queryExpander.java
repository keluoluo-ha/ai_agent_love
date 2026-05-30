package com.hhk.aiagentlove.rag;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class queryExpander {


    private final ChatClient.Builder  clientBuilder;
    public queryExpander(ChatModel dashscopeChatModel) {
        this.clientBuilder=ChatClient.builder(dashscopeChatModel);
    }

    public List<Query> expand(String query){
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(clientBuilder)
                .numberOfQueries(3)
                .build();
        List<Query> queries = queryExpander.expand(new Query(query));
        return queries;
    }

}
