package com.hhk.aiagentlove.agent.model;

import lombok.Data;
import org.springframework.ai.chat.messages.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class AgentRunState {

    private String runId;
    private String chatId;
    private AgentState agentState;
    private int currentStep;
    private int maxSteps;
    private List<Message> messageList = new ArrayList<>();
    private AskHumanRequest pendingAskHuman;
    private String lastStepAnswer;

    private String lastToolSignature = "";
    private int sameToolRepeatCount;
    private int searchWebCount;
    private boolean pdfRequested;
    private boolean pdfGenerated;

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
