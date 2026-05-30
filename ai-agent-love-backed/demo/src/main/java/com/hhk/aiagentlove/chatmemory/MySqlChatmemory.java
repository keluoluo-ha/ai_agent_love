package com.hhk.aiagentlove.chatmemory;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhk.aiagentlove.mapper.AiChatMemoryMapper;
import com.hhk.aiagentlove.model.AiChatMemory;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Primary
public class MySqlChatmemory implements ChatMemory {

    @Resource
    private AiChatMemoryMapper mapper;



    @Override
    public void add(String conversationId, List<Message> messages) {

        for (Message message : messages) {
            AiChatMemory aiChatMemory = new AiChatMemory();
            aiChatMemory.setConversation_id(conversationId);
            aiChatMemory.setType(message.getMessageType().getValue());
            aiChatMemory.setContent(message.getText());
            mapper.insert(aiChatMemory);

        }

    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        Page<AiChatMemory> page = new Page<>(1, lastN);
        QueryWrapper<AiChatMemory> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId)
                .orderByDesc("create_time");

        List<AiChatMemory> aiChatMemories = mapper.selectList(wrapper);
        // 反转列表,使得最新的消息在最后
        Collections.reverse(aiChatMemories);

        // 转换为Message对象
        List<Message> messages = new ArrayList<>();
        for (AiChatMemory aiChatMemory : aiChatMemories) {
            String type = aiChatMemory.getType();
            switch (type) {
                case "user" -> messages.add(new UserMessage(aiChatMemory.getContent()));
                case "assistant" -> messages.add(new AssistantMessage(aiChatMemory.getContent()));
                case "system" -> messages.add(new SystemMessage(aiChatMemory.getContent()));
                default -> throw new IllegalArgumentException("Unknown message type: " + type);
            }
        }
        return messages;

    }

    @Override
    public void clear(String conversationId) {
        QueryWrapper<AiChatMemory> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("conversation_id", conversationId);
        mapper.delete(queryWrapper);
    }
}