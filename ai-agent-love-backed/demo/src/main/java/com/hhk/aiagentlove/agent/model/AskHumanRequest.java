package com.hhk.aiagentlove.agent.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AskHumanRequest {

    private String toolCallId;
    private String question;
    private String reason;
    private List<String> options = new ArrayList<>();
}
