package com.hhk.aiagentlove.agent;

import com.hhk.aiagentlove.agent.model.AgentState;
import com.hhk.aiagentlove.agent.model.AgentStepLoopResult;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

@Data
@Slf4j
public abstract class BaseAgent {

    private String NextPrompt;
    private String SystemPrompt;
    private String name;

    private AgentState agentState = AgentState.IDLE;
    private int maxSteps = 10;
    private int currentStep = 0;
    private ChatClient chatClient;
    private List<Message> messageList = new ArrayList<>();

    /**
     * 统一的 ReAct 步进循环，供 YuManus 等入口复用。
     */
    public AgentStepLoopResult runStepLoop() {
        try {
            agentState = AgentState.RUNNING;

            for (int i = currentStep; i < maxSteps; i++) {
                if (agentState == AgentState.FINISHED || agentState == AgentState.WAITING_FOR_HUMAN) {
                    break;
                }

                int stepNumber = i + 1;
                currentStep = stepNumber;
                log.info("{} Step {}/{}", name, stepNumber, maxSteps);

                String stepResult = step();

                if (agentState == AgentState.WAITING_FOR_HUMAN) {
                    return AgentStepLoopResult.waitingForHuman(stepResult);
                }

                if (agentState == AgentState.FINISHED && stepResult != null && !stepResult.isBlank()) {
                    return AgentStepLoopResult.finished(stepResult);
                }
            }

            if (agentState != AgentState.FINISHED && this instanceof ToolCallAgent toolCallAgent) {
                String fallback = toolCallAgent.tryFinalizeOnMaxSteps();
                if (fallback != null && !fallback.isBlank()) {
                    return AgentStepLoopResult.finished(fallback);
                }
            }

            return AgentStepLoopResult.maxStepsReached(maxSteps);
        } catch (Exception e) {
            log.error("{} 步进循环失败", name, e);
            agentState = AgentState.ERROR;
            return AgentStepLoopResult.error("执行错误：" + e.getMessage());
        }
    }

    public void prepareNewRun(String userPrompt) {
        if (userPrompt != null && !userPrompt.isBlank()) {
            messageList.add(new UserMessage(userPrompt));
        }
        agentState = AgentState.IDLE;
        currentStep = 0;
    }

    public abstract String step();

    public void cleanup() {
    }
}
