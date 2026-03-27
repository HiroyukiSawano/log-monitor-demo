package com.example.demo.websocket.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 会话管理器 — 维护在线 Agent 的 WebSocket 会话和心跳时间戳
 */
@Slf4j
@Component
public class AgentSessionManager {

    /** agentId → WebSocketSession */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /** agentId → 最后心跳时间 (epoch ms) */
    private final Map<String, Long> lastHeartbeat = new ConcurrentHashMap<>();

    /**
     * 注册 Agent 会话
     */
    public void register(String agentId, WebSocketSession session) {
        sessions.put(agentId, session);
        lastHeartbeat.put(agentId, System.currentTimeMillis());
        log.info("[WS] Agent 上线: agentId={}, sessionId={}", agentId, session.getId());
    }

    /**
     * 移除 Agent 会话
     */
    public void remove(String agentId) {
        sessions.remove(agentId);
        lastHeartbeat.remove(agentId);
        log.info("[WS] Agent 下线: agentId={}", agentId);
    }

    /**
     * 刷新心跳时间戳
     */
    public void refreshHeartbeat(String agentId) {
        lastHeartbeat.put(agentId, System.currentTimeMillis());
    }

    /**
     * 获取指定 Agent 的会话
     */
    public Optional<WebSocketSession> getSession(String agentId) {
        return Optional.ofNullable(sessions.get(agentId));
    }

    /**
     * 判断 Agent 是否超时（超过 timeoutMs 未发送心跳）
     */
    public boolean isTimedOut(String agentId, long timeoutMs) {
        Long last = lastHeartbeat.get(agentId);
        if (last == null)
            return true;
        return (System.currentTimeMillis() - last) > timeoutMs;
    }

    /**
     * 获取 Agent 的远程 IP 地址
     */
    public String getAgentIp(String agentId) {
        WebSocketSession session = sessions.get(agentId);
        if (session != null) {
            java.net.InetSocketAddress address = session.getRemoteAddress();
            if (address != null && address.getAddress() != null) {
                return address.getAddress().getHostAddress();
            }
        }
        return null;
    }
    /**
     * 获取所有在线 Agent 的不可变视图
     */
    public Map<String, WebSocketSession> getAllSessions() {
        return Collections.unmodifiableMap(sessions);
    }

    /**
     * 强制断开指定 Agent
     */
    public void forceDisconnect(String agentId) {
        WebSocketSession session = sessions.get(agentId);
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.warn("[WS] 强制断开 Agent 失败: agentId={}", agentId, e);
            }
        }
        remove(agentId);
    }
}
