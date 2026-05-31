package com.hhk.aiagentlove.agent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AgentStepLoopResult {

    private AgentState endState;
    private String content;

    public static AgentStepLoopResult waitingForHuman(String content) {
        return AgentStepLoopResult.builder()
                .endState(AgentState.WAITING_FOR_HUMAN)
                .content(content)
                .build();
    }

    public static AgentStepLoopResult finished(String content) {
        return AgentStepLoopResult.builder()
                .endState(AgentState.FINISHED)
                .content(content)
                .build();
    }

    public static AgentStepLoopResult maxStepsReached(int maxSteps) {
        return AgentStepLoopResult.builder()
                .endState(AgentState.FINISHED)
                .content("执行结束：已达到最大步骤（" + maxSteps + "）")
                .build();
    }

    public static AgentStepLoopResult error(String message) {
        return AgentStepLoopResult.builder()
                .endState(AgentState.ERROR)
                .content(message)
                .build();
    }
}
