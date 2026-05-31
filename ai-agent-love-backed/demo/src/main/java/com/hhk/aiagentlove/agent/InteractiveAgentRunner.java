package com.hhk.aiagentlove.agent;

import com.hhk.aiagentlove.agent.model.AgentRunResult;
import com.hhk.aiagentlove.agent.model.AgentRunState;
import com.hhk.aiagentlove.agent.model.AgentState;
import com.hhk.aiagentlove.agent.store.AgentRunStateStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class InteractiveAgentRunner {

    private final AgentRunStateStore stateStore;

    public InteractiveAgentRunner(AgentRunStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public AgentRunResult execute(YuManus agent, String runId, String chatId, SseEmitter emitter) {
        try {
            agent.setAgentState(AgentState.RUNNING);

            for (int i = agent.getCurrentStep(); i < agent.getMaxSteps(); i++) {
                if (agent.getAgentState() == AgentState.FINISHED
                        || agent.getAgentState() == AgentState.WAITING_FOR_HUMAN) {
                    break;
                }

                int stepNumber = i + 1;
                agent.setCurrentStep(stepNumber);
                log.info("InteractiveAgent Step {}/{}", stepNumber, agent.getMaxSteps());

                String stepResult = agent.step();

                if (agent.getAgentState() == AgentState.WAITING_FOR_HUMAN) {
                    AgentRunState state = agent.snapshot(runId, chatId);
                    stateStore.save(state);
                    AgentRunResult result = AgentRunResult.askHuman(runId, chatId, agent.getPendingAskHuman());
                    emitAskHuman(emitter, result);
                    return result;
                }

                if (agent.getAgentState() == AgentState.FINISHED && stepResult != null && !stepResult.isBlank()) {
                    stateStore.remove(runId);
                    AgentRunResult result = AgentRunResult.finalAnswer(runId, chatId, stepResult);
                    emitFinal(emitter, result);
                    return result;
                }
            }

            if (agent.getAgentState() != AgentState.FINISHED) {
                String fallback = agent.tryFinalizeOnMaxSteps();
                if (fallback != null && !fallback.isBlank()) {
                    stateStore.remove(runId);
                    AgentRunResult result = AgentRunResult.finalAnswer(runId, chatId, fallback);
                    emitFinal(emitter, result);
                    return result;
                }
            }

            stateStore.remove(runId);
            AgentRunResult result = AgentRunResult.finalAnswer(
                    runId,
                    chatId,
                    "执行结束：已达到最大步骤（" + agent.getMaxSteps() + "）"
            );
            emitFinal(emitter, result);
            return result;
        } catch (Exception e) {
            log.error("InteractiveAgent 执行失败", e);
            stateStore.remove(runId);
            AgentRunResult result = AgentRunResult.error("执行错误：" + e.getMessage());
            emitError(emitter, result);
            return result;
        } finally {
            if (agent.getAgentState() != AgentState.WAITING_FOR_HUMAN) {
                agent.cleanup();
            }
        }
    }

    private void emitAskHuman(SseEmitter emitter, AgentRunResult result) {
        if (emitter == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("runId", result.getRunId());
            payload.put("chatId", result.getChatId());
            payload.put("question", result.getQuestion());
            payload.put("reason", result.getReason());
            payload.put("options", result.getOptions());
            payload.put("status", result.getStatus());
            emitter.send(SseEmitter.event().name("ask_human").data(payload));
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
