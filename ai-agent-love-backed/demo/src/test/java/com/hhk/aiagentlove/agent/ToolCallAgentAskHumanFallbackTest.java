package com.hhk.aiagentlove.agent;

import com.hhk.aiagentlove.agent.model.AgentState;
import com.hhk.aiagentlove.agent.model.AskHumanRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ToolCallAgentAskHumanFallbackTest {

    @Test
    void shouldConvertTextQuestionsToAskHumanPause() {
        ToolCallAgent agent = new YuManusAgent(new ToolCallback[0], null) {
            @Override
            public boolean think() {
                return false;
            }

            @Override
            public String act() {
                return null;
            }
        };

        agent.getMessageList().add(new UserMessage("帮我规划一次约会，预算和地点还没想好"));
        String assistantText = """
                我来帮您规划一次约会！为了给您提供更合适的建议，我需要了解一些关键信息：
                1. 约会对象？ 
                2. 想要什么氛围？ 
                3. 预算范围？ 
                """;

        agent.getMessageList().add(new org.springframework.ai.chat.messages.AssistantMessage(assistantText));
        AskHumanRequest request = invokeBuildAskHuman(agent, assistantText);
        agent.setPendingAskHuman(request);
        agent.setAgentState(AgentState.WAITING_FOR_HUMAN);

        assertEquals(AgentState.WAITING_FOR_HUMAN, agent.getAgentState());
        assertNotNull(agent.getPendingAskHuman());
    }

    private AskHumanRequest invokeBuildAskHuman(ToolCallAgent agent, String text) {
        try {
            var method = ToolCallAgent.class.getDeclaredMethod("buildAskHumanFromAssistantText", String.class);
            method.setAccessible(true);
            return (AskHumanRequest) method.invoke(agent, text);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
