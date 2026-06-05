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
        //生成唯一 runId
        String runId = "run_" + UUID.randomUUID().toString().replace("-", "");
        //如果前端没传id就用runId
        String resolvedChatId = (chatId == null || chatId.isBlank()) ? runId : chatId;

        YuManusAgent agent = newAgent();
        agent.getMessageList().add(new UserMessage(message));
        return execute(agent, runId, resolvedChatId, null);
    }

    //重置状态
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
        //	把用户回答包装成 ToolResponseMessage(name="askHuman") 插入消息列表，恢复 RUNNING 状态
        agent.injectHumanResponse(userAnswer);
        return execute(agent, runId, state.getChatId(), null);
    }

    public SseEmitter startSse(String message, String chatId, String runId, String replyType) {
        SseEmitter emitter = new SseEmitter(300000L);
        CompletableFuture.runAsync(() -> {
            try {
                if ("AGENT_REPLY".equalsIgnoreCase(replyType) && runId != null && !runId.isBlank()) {
                    AgentRunResult resume = resume(runId, message);
                    sendSseResult(emitter, resume);
                } else {
                    String newRunId = "run_" + UUID.randomUUID().toString().replace("-", "");
                    String resolvedChatId = (chatId == null || chatId.isBlank()) ? runId : chatId;
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
            //开启react形式
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
                //把当前 agent 全部状态（messageList、步数、计数器等）打包成 AgentRunState
                AgentRunState state = agent.snapshot(runId, chatId);
                //快照存到内存
                stateStore.save(state);

                //返回前端vo
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


    /**
     * resume() 内部调 execute() 时传不进 emitter，
     * 只能返回 AgentRunResult 对象，由这个方法负责把对象转成 SSE 事件推给前端。
     * @param emitter
     * @param result
     */
    private void sendSseResult(SseEmitter emitter, AgentRunResult result) {
        try {
            switch (result.getType()) {
                case AgentRunResult.TYPE_ASK_HUMAN -> {
                    //需要人工介入了，请前端展示人工客服界面
                    emitter.send(SseEmitter.event().name("ask_human").data(buildAskHumanPayload(result)));
                    //告诉前端：本次流程结束了
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
