package com.hhk.aiagentlove.agent;

import com.hhk.aiagentlove.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public abstract class ReActAgent extends BaseAgent {

    /** think 阶段产出的最终回复（无工具调用时使用） */
    protected String lastStepAnswer;

    public abstract boolean think();

    public abstract String act();

    @Override
    public String step() {
        try {
            lastStepAnswer = null;
            boolean shouldAct = think();
            if (!shouldAct) {
                setAgentState(AgentState.FINISHED);
                return (lastStepAnswer != null && !lastStepAnswer.isEmpty())
                        ? lastStepAnswer
                        : "思考完成。";
            }
            String actionResult = act();
            // act 返回 null 表示中间步骤，继续下一轮 think 整理回答
            if (actionResult == null) {
                return "";
            }
            return actionResult;
        } catch (Exception e) {
            log.error("步骤执行失败", e);
            setAgentState(AgentState.FINISHED);
            return "步骤执行失败：" + e.getMessage();
        }
    }
}
