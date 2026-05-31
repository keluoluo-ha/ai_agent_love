package com.hhk.aiagentlove.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.hhk.aiagentlove.agent.model.AgentRunState;
import com.hhk.aiagentlove.agent.model.AgentState;
import com.hhk.aiagentlove.agent.model.AskHumanRequest;
import com.hhk.aiagentlove.constant.FileConstant;
import com.hhk.aiagentlove.tools.PDFGenerationTool;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class ToolCallAgent extends ReActAgent {

    private static final int MAX_SAME_TOOL_REPEAT = 2;
    public static final String ASK_HUMAN_MARKER = "__ASK_HUMAN__";

    private final ToolCallback[] toolCallbacks;
    private ChatResponse toolCallResponse;
    private final ToolCallingManager toolCallingManager;
    private final ChatOptions chatOptions;

    private String lastToolSignature = "";
    private int sameToolRepeatCount = 0;
    private int searchWebCount = 0;
    private boolean pdfRequested = false;
    private boolean pdfGenerated = false;
    private AskHumanRequest pendingAskHuman;

    public ToolCallAgent(ToolCallback[] toolCallbacks) {
        this.toolCallbacks = toolCallbacks;
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DashScopeChatOptions.builder()
                .withProxyToolCalls(true)
                .build();
    }


    @Override
    public boolean think() {

        //遍历是否需要文档
        detectUserIntent();

        try {
            ChatResponse chatResponse = getChatClient().prompt()
                    .messages(getMessageList())
                    .system(buildSystemPrompt())
                    .options(chatOptions)
                    .tools(toolCallbacks)
                    .call()
                    .chatResponse();

            this.toolCallResponse = chatResponse;
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            String text = assistantMessage.getText() != null ? assistantMessage.getText() : "";

            var toolCallList = assistantMessage.getToolCalls();
            log.info("{} 的思考: {}", getName(), text);
            log.info("{} 选择了 {} 个工具", getName(), toolCallList.size());

            if (!toolCallList.isEmpty()) {
                log.info(toolCallList.stream()
                        .map(tc -> String.format("工具名称：%s，参数：%s", tc.name(), tc.arguments()))
                        .collect(Collectors.joining("\n")));

                if (shouldForceFinalizeNow(toolCallList)) {
                    log.warn("检测到重复 searchWeb 或步数即将耗尽，强制生成 PDF 并结束");
                    lastStepAnswer = forceFinalizeWithPdf();
                    setAgentState(AgentState.FINISHED);
                    return false;
                }

                if (!text.isBlank()) {
                    lastStepAnswer = text;
                }
                getMessageList().add(assistantMessage);
                return true;
            }

            getMessageList().add(assistantMessage);
            if (pdfRequested && !pdfGenerated && searchWebCount >= 1) {
                lastStepAnswer = forceFinalizeWithPdf();
            } else if (shouldPauseForHumanInput(text)) {
                AskHumanRequest fallbackRequest = buildAskHumanFromAssistantText(text);
                this.pendingAskHuman = fallbackRequest;
                setAgentState(AgentState.WAITING_FOR_HUMAN);
                lastStepAnswer = text;
                log.warn("{} 未调用 askHuman 工具但在文本中向用户提问，已自动转为 AskHuman 暂停", getName());
                return false;
            } else {
                lastStepAnswer = text.isBlank() ? "（模型未返回文本）" : text;
            }
            setAgentState(AgentState.FINISHED);
            return false;

        } catch (Exception e) {
            log.error("think 失败", e);
            String err = "处理时遇到错误：" + e.getMessage();
            getMessageList().add(new AssistantMessage(err));
            lastStepAnswer = err;
            setAgentState(AgentState.FINISHED);
            return false;
        }
    }

    @Override
    public String act() {
        if (toolCallResponse == null || !toolCallResponse.hasToolCalls()) {
            setAgentState(AgentState.FINISHED);
            return "没有工具调用";
        }

        List<AssistantMessage.ToolCall> toolCalls = toolCallResponse.getResult().getOutput().getToolCalls();
        AskHumanRequest askHumanRequest = extractAskHumanRequest(toolCalls);
        if (askHumanRequest != null) {
            this.pendingAskHuman = askHumanRequest;
            setAgentState(AgentState.WAITING_FOR_HUMAN);
            log.info("AskHuman 触发，等待用户输入: {}", askHumanRequest.getQuestion());
            return ASK_HUMAN_MARKER;
        }

        //生成提示词
        Prompt prompt = new Prompt(getMessageList(), chatOptions);

        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolCallResponse);
        setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage =
                (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());

        for (var response : toolResponseMessage.getResponses()) {
            if ("searchWeb".equals(response.name())) {
                searchWebCount++;
            }
            if ("generatePDF".equals(response.name())) {
                pdfGenerated = true;
            }
        }

        String toolSummary = toolResponseMessage.getResponses().stream()
                .map(response -> response.name() + " → " + truncateForLog(response.responseData(), 300))
                .collect(Collectors.joining(" | "));

        boolean terminateToolCalled = toolResponseMessage.getResponses().stream()
                .anyMatch(response -> "doTerminate".equals(response.name()));

        if (terminateToolCalled) {
            setAgentState(AgentState.FINISHED);
            String pdfInfo = toolResponseMessage.getResponses().stream()
                    .filter(r -> "generatePDF".equals(r.name()))
                    .map(ToolResponseMessage.ToolResponse::responseData)
                    .findFirst()
                    .orElse(null);

            StringBuilder answer = new StringBuilder();
            if (lastStepAnswer != null && !lastStepAnswer.isBlank()) {
                answer.append(lastStepAnswer);
            } else {
                answer.append(generateFinalAnswer());
            }
            if (pdfInfo != null && !pdfInfo.isBlank() && answer.indexOf(pdfInfo) < 0) {
                answer.append(formatPdfSection(pdfInfo));
            }
            return answer.toString();
        }

        if (pdfGenerated && pdfRequested) {
            String pdfPath = toolResponseMessage.getResponses().stream()
                    .filter(r -> "generatePDF".equals(r.name()))
                    .map(ToolResponseMessage.ToolResponse::responseData)
                    .findFirst()
                    .orElse("");
            lastStepAnswer = generateFinalAnswer() + formatPdfSection(pdfPath);
            setAgentState(AgentState.FINISHED);
            return lastStepAnswer;
        }

        log.info("工具执行完成: {}", toolSummary);
        return null;
    }


    @Override
    public void cleanup() {
        lastToolSignature = "";
        sameToolRepeatCount = 0;
        searchWebCount = 0;
        pdfRequested = false;
        pdfGenerated = false;
        pendingAskHuman = null;
    }


    /**
     * 把 SystemPrompt（你是谁）和 NextPrompt（工作规则）拼接成一个完整提示词发给 LLM
     * @return
     */
    protected String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder();
        if (getSystemPrompt() != null && !getSystemPrompt().isBlank()) {
            sb.append(getSystemPrompt().trim());
        }
        if (getNextPrompt() != null && !getNextPrompt().isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(getNextPrompt().trim());
        }
        return sb.toString();
    }

    /**
     * 通过遍历消息列表来判断用户是否想要生成 PDF 文档
     */
    private void detectUserIntent() {
        pdfRequested = getMessageList().stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).getText())
                .anyMatch(text -> text != null && (
                        text.toLowerCase().contains("pdf")
                                || text.contains("PDF")
                                || text.contains("文档")));
    }



    /**
     * 给工具调用生成指纹：`"searchWeb:{query参数}
     * @param toolCalls
     * @return
     */
    private String buildToolSignature(List<AssistantMessage.ToolCall> toolCalls) {
        return toolCalls.stream()
                .map(tc -> tc.name() + ":" + tc.arguments())
                .sorted()
                .collect(Collectors.joining("|"));
    }

    /**
     * 当前工具签名和上次一样 → 计数+1；不一样 → 重置为 1
     * @param toolCalls
     */
    private void updateRepeatCounter(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls.isEmpty()) {
            return;
        }
        String sig = buildToolSignature(toolCalls);
        if (sig.equals(lastToolSignature)) {
            sameToolRepeatCount++;
        } else {
            sameToolRepeatCount = 1;
            lastToolSignature = sig;
        }
    }

    /**
     * 判断是否全是 searchWeb 且重复次数 >= 2（防止模型无限搜）
     * @param toolCalls
     * @return
     */
    private boolean isRepeatedSearchOnly(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls.isEmpty()) {
            return false;
        }
        boolean allSearch = toolCalls.stream().allMatch(tc -> "searchWeb".equals(tc.name()));
        return allSearch && sameToolRepeatCount >= MAX_SAME_TOOL_REPEAT;
    }

    /**
     * 	综合判断：重复搜索 ≥2 次 或 PDF 场景下步数快用完 → 强制结束
     * @param toolCalls
     * @return
     */
    private boolean shouldForceFinalizeNow(List<AssistantMessage.ToolCall> toolCalls) {
        if (toolCalls.isEmpty()) {
            return false;
        }
        updateRepeatCounter(toolCalls);

        if (isRepeatedSearchOnly(toolCalls) && searchWebCount >= 1) {
            return true;
        }
        if (pdfRequested && searchWebCount >= 1 && !pdfGenerated && getCurrentStep() >= getMaxSteps() - 2) {
            return true;
        }
        return false;
    }


    /** 搜索已完成但模型反复 searchWeb 或即将耗尽步数时，强制汇总并生成 PDF */
    protected String forceFinalizeWithPdf() {
        log.info("强制结束：生成最终回答{}",
                pdfRequested && !pdfGenerated ? "并创建 PDF" : "");

        String summary = generateFinalAnswer();
        if (!pdfRequested) {
            return summary;
        }
        if (pdfGenerated) {
            return summary;
        }

        String fileName = buildPdfFileName();
        PDFGenerationTool pdfTool = new PDFGenerationTool();
        String pdfResult = pdfTool.generatePDF(fileName, summary);
        pdfGenerated = true;
        return summary + formatPdfSection(pdfResult);
    }


    /** 步数用尽时的兜底 */
    public String tryFinalizeOnMaxSteps() {
        if (getAgentState() == AgentState.FINISHED) {
            return null;
        }
        setAgentState(AgentState.FINISHED);
        if (searchWebCount > 0 || !getMessageList().isEmpty()) {
            return forceFinalizeWithPdf();
        }
        return null;
    }

    /**
     * 用完整对话历史调用一次 LLM，要求输出中文最终回答（禁止调工具）
     * @return
     */
    private String generateFinalAnswer() {
        try {
            String finalSystem = buildSystemPrompt()
                    + "\n\n【重要】请根据以上对话与工具执行结果，直接向用户输出完整、清晰的中文最终回答。"
                    + "若用户需要地点列表，请输出 10 条，格式：序号、名称、地址/亮点。"
                    + "若 generatePDF 工具已返回 Windows 绝对路径（如 C:\\...\\tmp\\pdf\\xxx.pdf），必须在文末原样写出该绝对路径，禁止使用 /api/files/pdf/ 相对地址。"
                    + "禁止调用任何工具。";
            ChatResponse response = getChatClient().prompt()
                    .messages(getMessageList())
                    .system(finalSystem)
                    .options(chatOptions)
                    .call()
                    .chatResponse();
            String text = response.getResult().getOutput().getText();
            if (text != null && !text.isBlank()) {
                getMessageList().add(response.getResult().getOutput());
                return text;
            }
        } catch (Exception e) {
            log.error("生成最终回答失败", e);
        }
        return "任务已结束。";
    }


    /**
     * 从 LLM 返回的 toolCalls 中找出 askHuman 工具，解析 question / reason / options
     * @param toolCalls
     * @return
     */
    private AskHumanRequest extractAskHumanRequest(List<AssistantMessage.ToolCall> toolCalls) {
        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            if (!"askHuman".equals(toolCall.name())) {
                continue;
            }
            AskHumanRequest request = new AskHumanRequest();
            request.setToolCallId(toolCall.id());
            JSONObject args = JSONUtil.parseObj(toolCall.arguments());
            request.setQuestion(args.getStr("question", "请补充更多信息以便我继续完成任务。"));
            request.setReason(args.getStr("reason"));
            String optionsRaw = args.getStr("options");
            if (optionsRaw != null && !optionsRaw.isBlank()) {
                request.setOptions(Arrays.stream(optionsRaw.split("[,，;；]"))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()));
            }
            return request;
        }
        return null;
    }

    /**
     * 模型未调用 askHuman 工具，但在文本里向用户提问时，自动转为交互式暂停。
     */
    private boolean shouldPauseForHumanInput(String assistantText) {
        if (assistantText == null || assistantText.isBlank() || pdfRequested) {
            return false;
        }
        long questionMarks = assistantText.chars()
                .filter(ch -> ch == '？' || ch == '?')
                .count();
        boolean hasAskPhrases = containsAny(assistantText,
                "需要了解", "请告诉我", "请问", "能否告诉", "关键信息",
                "请提供", "请补充", "还不清楚", "还没想好", "为了给您", "为了给你",
                "我需要知道", "麻烦您", "麻烦你", "想确认");
        boolean hasNumberedQuestions = assistantText.matches("(?s).*\\d+[.、．][^\\n]{0,80}[？?].*");
        boolean userMissingInfo = userIndicatesMissingInfo();

        if (questionMarks >= 2) {
            return true;
        }
        if (hasNumberedQuestions && questionMarks >= 1) {
            return true;
        }
        return hasAskPhrases && questionMarks >= 1 && userMissingInfo;
    }

    /**
     * 	检测用户消息中是否有 "还没想好""不确定""待定" 等词
     * @return
     */
    private boolean userIndicatesMissingInfo() {
        return getMessageList().stream()
                .filter(UserMessage.class::isInstance)
                .map(message -> ((UserMessage) message).getText())
                .filter(text -> text != null && !text.isBlank())
                .anyMatch(text -> containsAny(text,
                        "还没想好", "没想好", "不确定", "不知道", "待定", "还没定", "没定"));
    }


    /**
     * 把 LLM 的文本提问包装成 AskHumanRequest
     * @param assistantText
     * @return
     */
    private AskHumanRequest buildAskHumanFromAssistantText(String assistantText) {
        AskHumanRequest request = new AskHumanRequest();
        request.setToolCallId("fallback-ask-" + System.currentTimeMillis());
        request.setQuestion(extractQuestionFromAssistantText(assistantText));
        request.setReason("需要您补充信息后才能继续完成任务");
        return request;
    }

    /**
     * 从 LLM 文本中提取第一行作为问题
     * @param assistantText
     * @return
     */
    private String extractQuestionFromAssistantText(String assistantText) {
        String trimmed = assistantText.trim();
        int firstBreak = trimmed.indexOf('\n');
        if (firstBreak > 0 && firstBreak < 120) {
            return trimmed.substring(0, firstBreak).trim();
        }
        return trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed;
    }

    /**
     * 工具方法：字符串是否包含任一关键词
     * @param text
     * @param keywords
     * @return
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 	把用户回答包装成 ToolResponseMessage(name="askHuman") 插入消息列表，恢复 RUNNING 状态
     * @param userAnswer
     */
    public void injectHumanResponse(String userAnswer) {
        if (pendingAskHuman == null) {
            return;
        }
        ToolResponseMessage toolResponseMessage = new ToolResponseMessage(List.of(
                new ToolResponseMessage.ToolResponse(
                        pendingAskHuman.getToolCallId(),
                        "askHuman",
                        "用户回答：" + userAnswer
                )
        ));
        getMessageList().add(toolResponseMessage);
        pendingAskHuman = null;
        setAgentState(AgentState.RUNNING);
    }


    /**
     * 	把当前 agent 全部状态（messageList、步数、计数器等）打包成 AgentRunState，用于暂停后恢复
     * @param runId
     * @param chatId
     * @return
     */
    public AgentRunState snapshot(String runId, String chatId) {
        AgentRunState state = new AgentRunState();
        state.setRunId(runId);
        state.setChatId(chatId);
        state.setAgentState(getAgentState());
        state.setCurrentStep(getCurrentStep());
        state.setMaxSteps(getMaxSteps());
        state.setMessageList(new ArrayList<>(getMessageList()));
        state.setPendingAskHuman(pendingAskHuman);
        state.setLastStepAnswer(lastStepAnswer);
        state.setLastToolSignature(lastToolSignature);
        state.setSameToolRepeatCount(sameToolRepeatCount);
        state.setSearchWebCount(searchWebCount);
        state.setPdfRequested(pdfRequested);
        state.setPdfGenerated(pdfGenerated);
        return state;
    }

    /**
     * 从 AgentRunState 恢复所有状态
     * @param state
     */
    public void restore(AgentRunState state) {
        setAgentState(state.getAgentState());
        setCurrentStep(state.getCurrentStep());
        setMaxSteps(state.getMaxSteps());
        setMessageList(new ArrayList<>(state.getMessageList()));
        pendingAskHuman = state.getPendingAskHuman();
        lastStepAnswer = state.getLastStepAnswer();
        lastToolSignature = state.getLastToolSignature();
        sameToolRepeatCount = state.getSameToolRepeatCount();
        searchWebCount = state.getSearchWebCount();
        pdfRequested = state.isPdfRequested();
        pdfGenerated = state.isPdfGenerated();
    }


    /**
     * 根据对话内容生成 PDF 关键词，并拼接固定目录的绝对路径。
     * 目录：{aigent-love}/demo/tmp/pdf/
     */
    private String buildPdfAbsolutePath() {
        String keyword = generatePdfKeywordFromChat();
        String fileName = keyword + "_" + System.currentTimeMillis() + ".pdf";
        return FileConstant.PDF_SAVE_DIR + java.io.File.separator + fileName;
    }

    /**
     * 	生成 PDF 文件名
     * @return
     */
    private String buildPdfFileName() {
        String path = buildPdfAbsolutePath();
        return new java.io.File(path).getName();
    }


    /** 调用模型根据用户对话生成英文文件名关键词 */
    private String generatePdfKeywordFromChat() {
        String userText = getMessageList().stream()
                .filter(UserMessage.class::isInstance)
                .map(m -> ((UserMessage) m).getText())
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("\n"));

        if (userText.isBlank()) {
            return "report";
        }

        try {
            ChatResponse response = getChatClient().prompt()
                    .system("""
                            根据用户对话，生成一个简短的英文 PDF 文件名关键词。
                            要求：仅小写字母、数字、下划线，长度 3~24，不要 .pdf 后缀。
                            只输出关键词本身，不要解释、不要标点。
                            示例：beijing_dating, japan_street_view, shanghai_date_spots
                            """)
                    .user(userText)
                    .options(chatOptions)
                    .call()
                    .chatResponse();

            String keyword = response.getResult().getOutput().getText();
            keyword = sanitizePdfKeyword(keyword);
            log.info("对话生成 PDF 关键词: {}", keyword);
            return keyword;
        } catch (Exception e) {
            log.warn("对话生成 PDF 关键词失败，使用默认 report", e);
            return "report";
        }
    }

    /** 拼接 PDF 绝对路径说明（仅磁盘路径，不用 /api 相对地址） */
    private String formatPdfSection(String pathOrToolOutput) {
        String path = extractAbsolutePath(pathOrToolOutput);
        if (path == null || path.isBlank()) {
            return "";
        }
        return "\n\n---\n✅ PDF 文件已保存，绝对路径：\n" + path;
    }

    /**
     * 清洗关键词：去非法字符、截断到 24 字符
     * @param keyword
     * @return
     */
    private String sanitizePdfKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "report";
        }
        String cleaned = keyword.trim()
                .toLowerCase()
                .replaceAll("[^a-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (cleaned.isBlank()) {
            return "report";
        }
        return cleaned.length() > 24 ? cleaned.substring(0, 24) : cleaned;
    }

    /**
     * 从工具返回的文本中提取绝对路径
     * @param raw
     * @return
     */
    private String extractAbsolutePath(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim();
        if (text.contains(":\\") && text.toLowerCase().contains(".pdf")) {
            int pdfIdx = text.toLowerCase().indexOf(".pdf");
            if (pdfIdx > 0) {
                int start = text.lastIndexOf(':', pdfIdx);
                start = text.lastIndexOf('\n', start);
                if (start < 0) {
                    start = 0;
                } else {
                    start++;
                }
                return text.substring(start, pdfIdx + 4).trim();
            }
            return text;
        }
        int idx = text.indexOf("绝对路径：");
        if (idx >= 0) {
            return text.substring(idx + "绝对路径：".length()).split("[；;\\n]")[0].trim();
        }
        return text;
    }



    /**
     * 	日志截断（工具输出太长时）
     * @param text
     * @param maxLen
     * @return
     */
    private static String truncateForLog(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ");
        return oneLine.length() <= maxLen ? oneLine : oneLine.substring(0, maxLen) + "...";
    }





}
