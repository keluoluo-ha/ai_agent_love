package com.hhk.aiagentlove.controller;

import com.hhk.aiagentlove.agent.YuManus;
import com.hhk.aiagentlove.app.LoveApp;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;


@RestController
@RequestMapping("/ai")
public class Aicontroller {

    @Resource
    private ToolCallback[] allTools;
    @Resource
    public ChatModel dashscopeChatModel;
    @Resource
    public LoveApp  loveApp;

    /**
     * 第一种写法
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/sync")
    public String doChatWithLoveAppSync(String message,String chatId){
       return loveApp.doChat(message,chatId);
    }

    /**
     * 第二种写法
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping(value = "/love_app/chat/sse",produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>>  doChatWithLoveAppSse(String message,String chatId){
       return loveApp.doChatwithSSE(message, chatId)
                .mapNotNull(this::extractTextChunk)
                .map(text -> ServerSentEvent.<String>builder().data(text).build());

    }

    /** 从流式 ChatResponse 中提取纯文本，避免把整个对象 toString 发给前端 */
    private String extractTextChunk(ChatResponse chunk) {
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return null;
        }
        String text = chunk.getResult().getOutput().getText();
        return (text == null || text.isEmpty()) ? null : text;
    }

    /**
     * 第三种写法
     * @param message
     * @param chatId
     * @return
     */
    @GetMapping("/love_app/chat/sse/emitter")
    public SseEmitter doChatWithLoveAppSsseEmitter(String message,String chatId){

        SseEmitter emitter = new SseEmitter(180000L);
        loveApp.doChatwithSSE(message,chatId)
                .subscribe(
                        chunk-> {
                            try {
                                String text = extractTextChunk(chunk);
                                if (text != null) {
                                    emitter.send(text);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        },
                        emitter::completeWithError,
                        emitter::complete
                );
        return emitter;
    }

    @GetMapping("/manus/chat")
    public SseEmitter doChatWithManus(String message){
        YuManus manus = new YuManus(allTools,dashscopeChatModel);
        return manus.run(message);
    }







}
