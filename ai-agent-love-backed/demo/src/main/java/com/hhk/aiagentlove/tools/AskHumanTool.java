package com.hhk.aiagentlove.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 向用户询问信息或获取反馈。实际暂停逻辑由 ToolCallAgent 拦截处理。
 */
public class AskHumanTool {

    @Tool(description = """
            Ask the user for missing information, confirmation, or preference when you cannot proceed safely or accurately.
            Use this when key details are missing, the request is ambiguous, or an irreversible action needs user approval.
            Do NOT ask for information that is already clear from the conversation.
            """)
    public String askHuman(
            @ToolParam(description = "The question to ask the user, in Chinese") String question,
            @ToolParam(description = "Why this information is needed", required = false) String reason,
            @ToolParam(description = "Optional choices for the user, comma-separated", required = false) String options) {
        return "等待用户输入";
    }
}
