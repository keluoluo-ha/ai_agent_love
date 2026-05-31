package com.hhk.aiagentlove.services;

import com.hhk.aiagentlove.agent.InteractiveAgentRunner;
import com.hhk.aiagentlove.agent.YuManus;
import com.hhk.aiagentlove.agent.model.AgentRunResult;
import com.hhk.aiagentlove.agent.model.AgentRunState;
import com.hhk.aiagentlove.agent.model.AgentState;
import com.hhk.aiagentlove.agent.store.AgentRunStateStore;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class InteractiveAgentService {

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private AgentRunStateStore stateStore;

    @Resource
    private InteractiveAgentRunner interactiveAgentRunner;

    public AgentRunResult start(String message, String chatId) {
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "");
        String resolvedChatId = (chatId == null || chatId.isBlank()) ? runId : chatId;

        YuManus agent = createAgent();
        agent.getMessageList().add(new UserMessage(message));
        return interactiveAgentRunner.execute(agent, runId, resolvedChatId, null);
    }

    public AgentRunResult resume(String runId, String userAnswer) {
        AgentRunState state = stateStore.get(runId);
        if (state == null) {
            return AgentRunResult.error("运行任务不存在或已过期，请重新发起任务。");
        }
        if (state.getAgentState() != AgentState.WAITING_FOR_HUMAN) {
            return AgentRunResult.error("当前任务不在等待用户输入状态。");
        }
        if (userAnswer == null || userAnswer.isBlank()) {
            return AgentRunResult.error("回复内容不能为空。");
        }

        YuManus agent = createAgent();
        agent.restore(state);
        agent.injectHumanResponse(userAnswer);
        return interactiveAgentRunner.execute(agent, runId, state.getChatId(), null);
    }

    public SseEmitter startSse(String message, String chatId, String runId, String replyType) {
        SseEmitter emitter = new SseEmitter(300000L);
        CompletableFuture.runAsync(() -> {
            try {
                AgentRunResult result;
                if ("AGENT_REPLY".equalsIgnoreCase(replyType) && runId != null && !runId.isBlank()) {
                    result = resume(runId, message);
                    sendSseResult(emitter, result);
                } else {
                    String newRunId = "run_" + UUID.randomUUID().toString().replace("-", "");
                    String resolvedChatId = (chatId == null || chatId.isBlank()) ? newRunId : chatId;
                    YuManus agent = createAgent();
                    agent.getMessageList().add(new UserMessage(message));
                    interactiveAgentRunner.execute(agent, newRunId, resolvedChatId, emitter);
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void sendSseResult(SseEmitter emitter, AgentRunResult result) {
        try {
            switch (result.getType()) {
                case AgentRunResult.TYPE_ASK_HUMAN -> {
                    emitter.send(SseEmitter.event().name("ask_human").data(buildAskHumanPayload(result)));
                    emitter.send(SseEmitter.event().name("done").data(java.util.Map.of("status", result.getStatus())));
                }
                case AgentRunResult.TYPE_FINAL -> {
                    if (result.getContent() != null && !result.getContent().isBlank()) {
                        emitter.send(result.getContent());
                    }
                    emitter.send(SseEmitter.event().name("done").data(java.util.Map.of("status", result.getStatus())));
                }
                default -> emitter.send(SseEmitter.event().name("error").data(java.util.Map.of("message", result.getContent())));
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private java.util.Map<String, Object> buildAskHumanPayload(AgentRunResult result) {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("runId", result.getRunId());
        payload.put("chatId", result.getChatId());
        payload.put("question", result.getQuestion());
        payload.put("reason", result.getReason());
        payload.put("options", result.getOptions());
        payload.put("status", result.getStatus());
        return payload;
    }

    private YuManus createAgent() {
        return new YuManus(allTools, dashscopeChatModel);
    }
}
