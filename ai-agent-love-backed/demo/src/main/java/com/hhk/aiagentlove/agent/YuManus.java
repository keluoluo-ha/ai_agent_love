package com.hhk.aiagentlove.agent;

import com.hhk.aiagentlove.advisor.MyLoggerAdvisor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class YuManus extends ToolCallAgent{


    public YuManus(ToolCallback[] alltools, ChatModel dashscopeChatModel) {
        super(alltools);
        this.setName("YuManus");
        String SYSTEM_PROMPT = """  
               You are YuManus, an all-capable AI assistant, aimed at solving any task presented by the user.  
               You have various tools at your disposal that you can call upon to efficiently complete complex requests.  
                """;
        String NEXT_STEP_PROMPT = """  
                Workflow rules:
                1. If user asks for PDF: call searchWeb ONCE, then call generatePDF with the summarized Chinese content (fileName must end with .pdf), then reply with the list and PDF path, then call doTerminate.
                2. Never call searchWeb twice with the same or similar query.
                3. After searchWeb returns results, your next action MUST be generatePDF (when PDF requested) or a final Chinese answer, NOT another searchWeb.
                4. Reply in clear Chinese with numbered lists. Do NOT paste raw JSON.
                5. When done, write the full answer in message text, then call doTerminate.
                6. generatePDF tool returns ONLY a disk absolute path (e.g. C:\\Users\\...\\aigent-love\\tmp\\pdf\\report_xxx.pdf). You MUST copy that exact path for the user. Never use /api/files/pdf/ relative URLs.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        this.setNextPrompt(NEXT_STEP_PROMPT);
        this.setMaxSteps(20);
        this.setCurrentStep(0);
        ChatClient chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build();
        this.setChatClient(chatClient);


    }

}

