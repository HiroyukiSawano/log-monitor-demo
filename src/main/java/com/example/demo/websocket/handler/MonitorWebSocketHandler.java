package com.example.demo.websocket.handler;

import com.example.demo.websocket.session.MonitorSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.lang.NonNull;

import java.net.URI;
import java.util.Map;

/**
 * 运营端监控 WebSocket 处理器
 *
 * 连接示例: ws://host:port/ws/monitor?agentId=server-01
 * 连接建立后，服务端会将指定 Agent 的所有上下行报文实时推送到此连接。
 */
@Slf4j
@Component
public class MonitorWebSocketHandler extends AbstractWebSocketHandler {

    private final MonitorSessionManager monitorSessionManager;

    private static final String ATTR_AGENT_ID = "monitorAgentId";

    public MonitorWebSocketHandler(MonitorSessionManager monitorSessionManager) {
        this.monitorSessionManager = monitorSessionManager;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
        String agentId = extractAgentId(session);
        if (agentId == null || agentId.isEmpty()) {
            session.close(CloseStatus.BAD_DATA.withReason("缺少 agentId 参数"));
            return;
        }

        session.getAttributes().put(ATTR_AGENT_ID, agentId);
        monitorSessionManager.subscribe(agentId, session);

        // 通知运营端连接成功
        String welcome = String.format(
                "{\"dir\":\"SYSTEM\",\"agentId\":\"%s\",\"ts\":%d,\"raw\":\"已订阅 Agent [%s] 的实时消息\"}",
                agentId, System.currentTimeMillis(), agentId);
        session.sendMessage(new TextMessage(welcome));
        log.info("[Monitor] 运营端已连接并订阅: agentId={}", agentId);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        String agentId = getAgentId(session);
        if (agentId != null) {
            monitorSessionManager.unsubscribe(agentId, session);
        }
        log.info("[Monitor] 运营端断开: agentId={}", agentId);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws Exception {
        // 运营端也可以通过此连接发送指令（未来扩展）
        // 目前忽略运营端发来的文本消息
        log.debug("[Monitor] 运营端发来消息(忽略): {}", message.getPayload());
    }

    private String extractAgentId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null)
            return null;
        Map<String, String> params = UriComponentsBuilder.fromUri(uri).build()
                .getQueryParams().toSingleValueMap();
        return params.get("agentId");
    }

    private String getAgentId(WebSocketSession session) {
        return (String) session.getAttributes().get(ATTR_AGENT_ID);
    }
}
