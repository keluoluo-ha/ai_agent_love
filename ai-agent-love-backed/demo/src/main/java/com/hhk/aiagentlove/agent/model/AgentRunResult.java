package com.hhk.aiagentlove.agent.model;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
/**
 *返回给前端的 VO
 */
public class AgentRunResult {

    public static final String TYPE_FINAL = "FINAL";
    public static final String TYPE_ASK_HUMAN = "ASK_HUMAN";
    public static final String TYPE_ERROR = "ERROR";

    /** 结果类型："FINAL"（最终答案）、"ASK_HUMAN"（需要人工输入）、"ERROR"（出错） */
    private String type;
    
    /** 本次执行的唯一标识，用于 resume 时从 stateStore 恢复状态 */
    private String runId;
    
    /** 会话ID，同一对话的多轮请求共用同一个 chatId */
    private String chatId;
    
    /** 返回给前端的内容文本（type=FINAL 时为最终答案，type=ERROR 时为错误信息） */
    private String content;
    
    /** 当 type=ASK_HUMAN 时，agent 向用户提出的问题 */
    private String question;
    
    /** 当 type=ASK_HUMAN 时，agent 需要提问的原因说明 */
    private String reason;
    
    /** 当 type=ASK_HUMAN 时，可提供给用户选择的选项列表（可为空） */
    @Builder.Default
    private List<String> options = new ArrayList<>();
    
    /** 当前任务状态，值为 AgentState 的 name：FINISHED / WAITING_FOR_HUMAN / ERROR */
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
