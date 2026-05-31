package com.hhk.aiagentlove.agent.store;

import com.hhk.aiagentlove.agent.model.AgentRunState;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryAgentRunStateStore implements AgentRunStateStore {

    private static final Duration TTL = Duration.ofMinutes(60);

    private final ConcurrentHashMap<String, AgentRunState> store = new ConcurrentHashMap<>();

    @Override
    public void save(AgentRunState state) {
        state.setUpdatedAt(Instant.now());
        store.put(state.getRunId(), state);
        cleanupExpired();
    }

    @Override
    public AgentRunState get(String runId) {
        AgentRunState state = store.get(runId);
        if (state == null) {
            return null;
        }
        if (Duration.between(state.getUpdatedAt(), Instant.now()).compareTo(TTL) > 0) {
            store.remove(runId);
            return null;
        }
        return state;
    }

    @Override
    public void remove(String runId) {
        store.remove(runId);
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        store.entrySet().removeIf(entry ->
                Duration.between(entry.getValue().getUpdatedAt(), now).compareTo(TTL) > 0);
    }
}
