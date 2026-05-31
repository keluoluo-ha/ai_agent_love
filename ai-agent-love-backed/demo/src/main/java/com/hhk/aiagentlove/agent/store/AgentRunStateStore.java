package com.hhk.aiagentlove.agent.store;

import com.hhk.aiagentlove.agent.model.AgentRunState;

public interface AgentRunStateStore {

    void save(AgentRunState state);

    AgentRunState get(String runId);

    void remove(String runId);
}
