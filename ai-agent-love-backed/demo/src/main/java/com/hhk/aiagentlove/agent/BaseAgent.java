package com.hhk.aiagentlove.agent;

import com.hhk.aiagentlove.agent.model.AgentState;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.util.StringUtil;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Data
@Slf4j
public abstract class BaseAgent {

    SseEmitter emitter = new SseEmitter(300000L);
    private String NextPrompt;
    private String SystemPrompt;
    //核心属性
    private String name;

    private AgentState agentState=AgentState.IDLE;
    private int maxSteps=10;
    private int currentStep=0;
    private ChatClient chatClient;
    private List<Message> messageList=new ArrayList<>();


    public SseEmitter run(String userprompt) {

        CompletableFuture.runAsync(() -> {
            try {
                if (this.agentState != AgentState.IDLE) {
                    emitter.send("错误：无法从状态运行代理" + this.agentState);
                    emitter.complete();
                    return;
                }
                if (StringUtil.isEmpty(userprompt)) {
                    emitter.send("错误：不能用空提示词运行代理" + this.agentState);
                    emitter.complete();
                    return;
                }
                agentState = AgentState.RUNNING;
                messageList.add(new UserMessage(userprompt));
                try {
                    for (int i = 0; i < maxSteps && agentState != AgentState.FINISHED; i++) {
                        int stepNumber = i + 1;
                        currentStep = stepNumber;
                        log.info("Excuting Step " + stepNumber + "/" + maxSteps);

                        log.info("AgentState:" + agentState);

                        String stepResult = step();
                        // 仅推送最终回答，中间工具步骤不刷屏
                        if (agentState == AgentState.FINISHED && stepResult != null && !stepResult.isBlank()) {
                            emitter.send(stepResult);
                        } else if (stepResult != null && !stepResult.isBlank()) {
                            log.debug("Step {} 中间结果（不推送前端）", stepNumber);
                        }
                    }
                    if (agentState != AgentState.FINISHED) {
                        String fallback = null;
                        if (this instanceof ToolCallAgent toolCallAgent) {
                            fallback = toolCallAgent.tryFinalizeOnMaxSteps();
                        }
                        if (fallback != null && !fallback.isBlank()) {
                            emitter.send(fallback);
                        } else {
                            agentState = AgentState.FINISHED;
                            emitter.send("执行结束：已达到最大步骤（" + maxSteps + "）");
                        }
                    }
                    emitter.complete();
//            return String.join("\n",results);
                } catch (Exception e) {
                    agentState = AgentState.ERROR;
                    log.error("执行智能体失败",e);

                    try {
                        emitter.send("执行错误" + e.getMessage());
                        emitter.complete();
                    }catch (Exception e1){
                        emitter.completeWithError(e1);
                    }

                } finally {
                    this.cleanup();

                }
            }catch (Exception ex){
              emitter.completeWithError(ex);
            }




            emitter.onTimeout(() -> {
                this.agentState = AgentState.ERROR;
                this.cleanup();
                log.warn("SSE connection timed out");
            });
            emitter.onCompletion(() -> {
                if (agentState == AgentState.RUNNING) {
                    agentState = AgentState.FINISHED;
                }
                this.cleanup();
                log.warn("SSE connection completed");
            });



        });
        return emitter;
    }


    public abstract String step();
    public void cleanup(){}




}
