package com.example.demo.websocket.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 运营监控 WebSocket 会话管理
 *
 * 运营端通过 /ws/monitor?agentId=xxx 订阅指定 Agent 的实时消息。
 * 当 Agent 向服务端发送消息（或服务端向 Agent 发送消息）时，
 * 通过 broadcast() 方法将消息副本推送到所有订阅该 Agent 的运营端连接。
 */
@Slf4j
@Component
public class MonitorSessionManager {

    /** agentId → 订阅该 Agent 的运营端 WebSocket 会话集合 */
    private final Map<String, Set<WebSocketSession>> subscriptions = new ConcurrentHashMap<>();

    /**
     * 运营端订阅指定 Agent 的消息
     */
    public void subscribe(String agentId, WebSocketSession session) {
        subscriptions.computeIfAbsent(agentId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("[Monitor] 运营端订阅: agentId={}, sessionId={}", agentId, session.getId());
    }

    /**
     * 运营端取消订阅
     */
    public void unsubscribe(String agentId, WebSocketSession session) {
        Set<WebSocketSession> sessions = subscriptions.get(agentId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                subscriptions.remove(agentId);
            }
        }
        log.info("[Monitor] 运营端取消订阅: agentId={}", agentId);
    }

    /**
     * 广播消息到所有订阅指定 Agent 的运营端
     *
     * @param agentId    消息所属 Agent
     * @param direction  方向标识: "AGENT_UP"(Agent→Server) 或
     *                   "SERVER_DOWN"(Server→Agent)
     * @param rawPayload 原始报文内容
     */
    public void broadcast(String agentId, String direction, String rawPayload) {
        Set<WebSocketSession> sessions = subscriptions.get(agentId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        // 封装为简单的监控信封
        String monitorMsg = String.format(
                "{\"dir\":\"%s\",\"agentId\":\"%s\",\"ts\":%d,\"raw\":%s}",
                direction, agentId, System.currentTimeMillis(), rawPayload);

        TextMessage textMessage = new TextMessage(monitorMsg);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    log.warn("[Monitor] 推送失败: sessionId={}, error={}", session.getId(), e.getMessage());
                }
            }
        }
    }
}
