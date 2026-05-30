package com.hhk.aiagentlove.app;
import com.hhk.aiagentlove.advisor.MyLoggerAdvisor;
import com.hhk.aiagentlove.chatmemory.FileBasedChatMemory;
import com.hhk.aiagentlove.rag.queryExpander;
import com.hhk.aiagentlove.tools.DateTimeTools;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;

import org.springframework.ai.rag.Query;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;


@Slf4j
@Component
public class LoveApp {





    private final ChatClient chatClient;

    private static final String SYSTEM_PROMPT = "扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。" +
        "围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；" +
        "恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。" +
        "引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。";




    /**
     * 创建ChatClient
     * @param dashscopeChatModel
     */
    public LoveApp(ChatModel dashscopeChatModel) {

        String fileDir=System.getProperty("user.dir")+"/chat-memory";
        ChatMemory chatMemory=new FileBasedChatMemory(fileDir);
        chatClient=ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        new MessageChatMemoryAdvisor(chatMemory),
                        new MyLoggerAdvisor()
                )
                .build();

    }

    /**
     * ai 基础对话
     * @param message
     * @param chatId
     * @return
     */
    public String doChat(String message,String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .tools(new DateTimeTools())
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.debug(content);
        return content;
    }

    /**
     * 基于SSE的流式输出接口
     * @param message
     * @param chatId
     * @return
     */
    public Flux<ChatResponse>  doChatwithSSE(String message,String chatId) {
        Flux<ChatResponse> chatResponseFlux = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .tools(new DateTimeTools())
                .stream()
                .chatResponse();

        return chatResponseFlux;
    }


    record LoveReport(String title, List<String> suggestions) {}

    /**
     * 报告结构化输出
     * @param message
     * @param ChatId
     * @return
     */
    public LoveReport doChatWithReport( String message, String ChatId) {
        LoveReport loveReport=chatClient.prompt()
                .system(SYSTEM_PROMPT+"每次对话后都要生成恋爱结果，标题为{用户名}的恋爱报告，内容为建议列表")
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, ChatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .call()
                .entity(LoveReport.class);
        return loveReport;
    }

//      @Resource
//      private VectorStore loveAppVectorStore;

    @Resource
    private VectorStore pgVectorVectorStore;

    @Resource
    private queryExpander queryExpander;

    public String doChatWithRag(String message, String ChatId) {

        List<Query> expandedQueries = queryExpander.expand(message);

        // 把扩展后的问题 拼成一个大问题（让检索更精准）
        StringBuilder finalQuery = new StringBuilder(message);
        for (Query q : expandedQueries) {
            finalQuery.append("\n").append(q.text());
        }

        ChatResponse chatResponse = chatClient.prompt()
                .user(finalQuery.toString())
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, ChatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                .advisors(new QuestionAnswerAdvisor(pgVectorVectorStore))
                .call()
                .chatResponse();

        return chatResponse.getResult().getOutput().getText();
    }

    @Resource
    private ToolCallback[] allTools;

    /**
     * tool测试
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTools(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(allTools)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    public String doChatWithMcp(String message, String chatId) {
        ChatResponse response = chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10))
                // 开启日志，便于观察效果
                .advisors(new MyLoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }






}
