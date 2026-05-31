package com.hhk.aiagentlove.agent.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class AgentRunResult {

    public static final String TYPE_FINAL = "FINAL";
    public static final String TYPE_ASK_HUMAN = "ASK_HUMAN";
    public static final String TYPE_ERROR = "ERROR";

    private String type;
    private String runId;
    private String chatId;
    private String content;
    private String question;
    private String reason;
    @Builder.Default
    private List<String> options = new ArrayList<>();
    private String status;

    public static AgentRunResult finalAnswer(String runId, String chatId, String content) {
        return AgentRunResult.builder()
                .type(TYPE_FINAL)
                .runId(runId)
                .chatId(chatId)
                .content(content)
                .status(AgentState.FINISHED.name())
                .build();
    }

    public static AgentRunResult askHuman(String runId, String chatId, AskHumanRequest request) {
        return AgentRunResult.builder()
                .type(TYPE_ASK_HUMAN)
                .runId(runId)
                .chatId(chatId)
                .question(request.getQuestion())
                .reason(request.getReason())
                .options(request.getOptions())
                .status(AgentState.WAITING_FOR_HUMAN.name())
                .build();
    }

    public static AgentRunResult error(String message) {
        return AgentRunResult.builder()
                .type(TYPE_ERROR)
                .content(message)
                .status(AgentState.ERROR.name())
                .build();
    }
}
