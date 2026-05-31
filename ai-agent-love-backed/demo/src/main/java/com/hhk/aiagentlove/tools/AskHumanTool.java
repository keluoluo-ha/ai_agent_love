package com.hhk.aiagentlove.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 向用户询问信息或获取反馈。实际暂停逻辑由 ToolCallAgent 拦截处理。
 */
public class AskHumanTool {

    @Tool(description = """
            【必须使用的工具】向用户询问缺失信息、确认偏好或获取反馈。
            当任务缺少关键信息、存在歧义、或执行不可逆操作前需要用户确认时，必须调用本工具，禁止只在回复文本里提问。
            调用后任务会暂停，等待用户回答再继续。
            """)
    public String askHuman(
            @ToolParam(description = "向用户提出的中文问题，需清晰具体") String question,
            @ToolParam(description = "为什么需要这个信息", required = false) String reason,
            @ToolParam(description = "可选答案，逗号分隔", required = false) String options) {

        return "等待用户输入";
    }
}
