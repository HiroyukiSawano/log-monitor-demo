package com.example.demo.websocket.handler;

import com.example.demo.module.command.service.RemoteCommandService;
import com.example.demo.websocket.protocol.BinaryFrameHandler;
import com.example.demo.websocket.protocol.MessageDispatcher;
import com.example.demo.websocket.protocol.WsMessage;
import com.example.demo.websocket.session.AgentSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

/**
 * Agent WebSocket 连接处理器
 * <p>
 * 职责：
 * 1. 连接建立时进行 agentId + token 认证
 * 2. 文本帧解析为 {@link WsMessage} 后交由 {@link MessageDispatcher} 路由
 * 3. 二进制帧交由 {@link BinaryFrameHandler} 处理
 * 4. 连接关闭时清理会话
 */
@Slf4j
@Component
public class AgentWebSocketHandler extends AbstractWebSocketHandler {

    private final AgentSessionManager sessionManager;
    private final MessageDispatcher dispatcher;
    private final BinaryFrameHandler binaryFrameHandler;
    private final ObjectMapper objectMapper;
    private final RemoteCommandService remoteCommandService;

    @Value("${ws.agent.token:default-pre-shared-key}")
    private String expectedToken;

    /** Session attribute key for storing agentId */
    private static final String ATTR_AGENT_ID = "agentId";

    public AgentWebSocketHandler(AgentSessionManager sessionManager,
            MessageDispatcher dispatcher,
            BinaryFrameHandler binaryFrameHandler,
            ObjectMapper objectMapper,
            RemoteCommandService remoteCommandService) {
        this.sessionManager = sessionManager;
        this.dispatcher = dispatcher;
        this.binaryFrameHandler = binaryFrameHandler;
        this.objectMapper = objectMapper;
        this.remoteCommandService = remoteCommandService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        URI uri = session.getUri();
        if (uri == null) {
            log.warn("[WS] 连接无 URI，拒绝");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // 解析 URL 参数: ?agentId=xxx&token=yyy
        Map<String, String> params = UriComponentsBuilder.fromUri(uri)
                .build().getQueryParams().toSingleValueMap();

        String agentId = params.get("agentId");
        String token = params.get("token");

        if (agentId == null || agentId.isEmpty()) {
            log.warn("[WS] 缺少 agentId 参数，拒绝连接");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        if (!expectedToken.equals(token)) {
            log.warn("[WS] token 验证失败: agentId={}", agentId);
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // 认证通过 — 注册会话
        session.getAttributes().put(ATTR_AGENT_ID, agentId);
        sessionManager.register(agentId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String agentId = getAgentId(session);
        if (agentId == null)
            return;

        try {
            // 先尝试检测 Agent 端自定义指令响应格式: {"type":"xxx@Return","result":"...","cmdID":"xxx"}
            com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(message.getPayload());
            if (rootNode.has("cmdID") && rootNode.has("type")) {
                String type = rootNode.get("type").asText("");
                if (type.contains("@Return") || type.equals("cmd")) {
                    String cmdId = rootNode.get("cmdID").asText();
                    String result = rootNode.has("result") ? rootNode.get("result").asText("") : "";
                    log.info("[WS] 收到 Agent 自定义指令响应: agentId={}, type={}, cmdID={}", agentId, type, cmdId);
                    remoteCommandService.handleResponse(agentId, cmdId, true, result);
                    return;
                }
            }

            // 标准 WsMessage 信封格式
            WsMessage wsMessage = objectMapper.readValue(message.getPayload(), WsMessage.class);
            // 确保信封中的 agentId 与连接认证一致
            wsMessage.setAgentId(agentId);
            dispatcher.dispatch(wsMessage);
        } catch (Exception e) {
            log.error("[WS] 消息解析失败: agentId={}, payload={}", agentId,
                    message.getPayload().length() > 200 ? message.getPayload().substring(0, 200) : message.getPayload(),
                    e);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        String agentId = getAgentId(session);
        if (agentId == null)
            return;

        binaryFrameHandler.handleBinaryFrame(agentId, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String agentId = getAgentId(session);
        if (agentId != null) {
            sessionManager.remove(agentId);
        }
        log.info("[WS] 连接关闭: agentId={}, status={}", agentId, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String agentId = getAgentId(session);
        log.error("[WS] 传输错误: agentId={}", agentId, exception);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    private String getAgentId(WebSocketSession session) {
        return (String) session.getAttributes().get(ATTR_AGENT_ID);
    }
}
