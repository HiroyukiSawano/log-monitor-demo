package com.example.demo.monitor.health;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局健康状态存储 — ConcurrentHashMap 实现
 */
@Component
public class HealthStateStore {

    private final Map<String, ServerHealthState> store = new ConcurrentHashMap<>();

    public void put(String agentId, ServerHealthState state) {
        store.put(agentId, state);
    }

    public ServerHealthState get(String agentId) {
        return store.get(agentId);
    }

    public ServerHealthState getOrCreate(String agentId) {
        return store.computeIfAbsent(agentId, id -> ServerHealthState.builder().agentId(id).build());
    }

    public void remove(String agentId) {
        store.remove(agentId);
    }

    public Map<String, ServerHealthState> getAll() {
        return Collections.unmodifiableMap(store);
    }
}
