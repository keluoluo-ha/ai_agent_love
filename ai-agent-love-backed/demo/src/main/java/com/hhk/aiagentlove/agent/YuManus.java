package com.hhk.aiagentlove.agent;

import com.hhk.aiagentlove.agent.model.AgentRunResult;
import com.hhk.aiagentlove.agent.model.AgentRunState;
import com.hhk.aiagentlove.agent.model.AgentState;
import com.hhk.aiagentlove.agent.model.AgentStepLoopResult;
import com.hhk.aiagentlove.agent.store.AgentRunStateStore;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class YuManus {

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ChatModel dashscopeChatModel;

    @Resource
    private AgentRunStateStore stateStore;

    public AgentRunResult start(String message, String chatId) {
        String runId = newRunId();
        String resolvedChatId = resolveChatId(chatId, runId);

        YuManusAgent agent = newAgent();
        agent.getMessageList().add(new UserMessage(message));
        return execute(agent, runId, resolvedChatId, null);
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

        YuManusAgent agent = newAgent();
        agent.restore(state);
        agent.injectHumanResponse(userAnswer);
        return execute(agent, runId, state.getChatId(), null);
    }

    public SseEmitter startSse(String message, String chatId, String runId, String replyType) {
        SseEmitter emitter = new SseEmitter(300000L);
        CompletableFuture.runAsync(() -> {
            try {
                if ("AGENT_REPLY".equalsIgnoreCase(replyType) && runId != null && !runId.isBlank()) {
                    sendSseResult(emitter, resume(runId, message));
                } else {
                    String newRunId = newRunId();
                    String resolvedChatId = resolveChatId(chatId, newRunId);
                    YuManusAgent agent = newAgent();
                    agent.getMessageList().add(new UserMessage(message));
                    execute(agent, newRunId, resolvedChatId, emitter);
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private AgentRunResult execute(YuManusAgent agent, String runId, String chatId, SseEmitter emitter) {
        try {
            AgentStepLoopResult loopResult = agent.runStepLoop();
            return toRunResult(agent, runId, chatId, emitter, loopResult);
        } finally {
            if (agent.getAgentState() != AgentState.WAITING_FOR_HUMAN) {
                agent.cleanup();
            }
        }
    }

    private AgentRunResult toRunResult(
            YuManusAgent agent,
            String runId,
            String chatId,
            SseEmitter emitter,
            AgentStepLoopResult loopResult) {
        return switch (loopResult.getEndState()) {
            case WAITING_FOR_HUMAN -> {
                AgentRunState state = agent.snapshot(runId, chatId);
                stateStore.save(state);
                AgentRunResult result = AgentRunResult.askHuman(runId, chatId, agent.getPendingAskHuman());
                emitAskHuman(emitter, result);
                yield result;
            }
            case FINISHED -> {
                stateStore.remove(runId);
                AgentRunResult result = AgentRunResult.finalAnswer(runId, chatId, loopResult.getContent());
                emitFinal(emitter, result);
                yield result;
            }
            case ERROR -> {
                stateStore.remove(runId);
                AgentRunResult result = AgentRunResult.error(loopResult.getContent());
                emitError(emitter, result);
                yield result;
            }
            default -> {
                stateStore.remove(runId);
                AgentRunResult result = AgentRunResult.finalAnswer(runId, chatId, loopResult.getContent());
                emitFinal(emitter, result);
                yield result;
            }
        };
    }

    private YuManusAgent newAgent() {
        return new YuManusAgent(allTools, dashscopeChatModel);
    }

    private String newRunId() {
        return "run_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveChatId(String chatId, String runId) {
        return (chatId == null || chatId.isBlank()) ? runId : chatId;
    }

    private void sendSseResult(SseEmitter emitter, AgentRunResult result) {
        try {
            switch (result.getType()) {
                case AgentRunResult.TYPE_ASK_HUMAN -> {
                    emitter.send(SseEmitter.event().name("ask_human").data(buildAskHumanPayload(result)));
                    emitter.send(SseEmitter.event().name("done").data(Map.of("status", result.getStatus())));
                }
                case AgentRunResult.TYPE_FINAL -> {
                    if (result.getContent() != null && !result.getContent().isBlank()) {
                        emitter.send(result.getContent());
                    }
                    emitter.send(SseEmitter.event().name("done").data(Map.of("status", result.getStatus())));
                }
                default -> emitter.send(SseEmitter.event().name("error").data(Map.of("message", result.getContent())));
            }
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private Map<String, Object> buildAskHumanPayload(AgentRunResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("runId", result.getRunId());
        payload.put("chatId", result.getChatId());
        payload.put("question", result.getQuestion());
        payload.put("reason", result.getReason());
        payload.put("options", result.getOptions());
        payload.put("status", result.getStatus());
        return payload;
    }

    private void emitAskHuman(SseEmitter emitter, AgentRunResult result) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("ask_human").data(buildAskHumanPayload(result)));
            emitter.send(SseEmitter.event().name("done").data(Map.of("status", result.getStatus())));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void emitFinal(SseEmitter emitter, AgentRunResult result) {
        if (emitter == null) {
            return;
        }
        try {
            if (result.getContent() != null && !result.getContent().isBlank()) {
                emitter.send(result.getContent());
            }
            emitter.send(SseEmitter.event().name("done").data(Map.of("status", result.getStatus())));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private void emitError(SseEmitter emitter, AgentRunResult result) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("message", result.getContent())));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }
}
