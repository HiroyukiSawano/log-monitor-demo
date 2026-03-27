package com.example.demo.websocket.handler;

import com.example.demo.websocket.session.AgentSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.SubProtocolCapable;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TtydWebSocketProxyHandler extends AbstractWebSocketHandler implements SubProtocolCapable {

    private static final String HEADER_SEC_WEBSOCKET_PROTOCOL = "Sec-WebSocket-Protocol";
    private static final List<String> SUPPORTED_PROTOCOLS = Arrays.asList("tty");
    private static final String WS_SUFFIX = "/ws";

    private final AgentSessionManager sessionManager;
    private final StandardWebSocketClient wsClient = new StandardWebSocketClient();

    // browser session ID -> target TTYD WebSocket session
    private final Map<String, WebSocketSession> clientSessions = new ConcurrentHashMap<>();

    public TtydWebSocketProxyHandler(AgentSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession browserSession) throws Exception {
        URI browserUri = browserSession.getUri();
        if (browserUri == null) {
            log.warn("[WS Proxy] Browser session has no URI: sessionId={}", browserSession.getId());
            browserSession.close(CloseStatus.SERVER_ERROR.withReason("Missing request URI"));
            return;
        }

        String[] pathParts = browserUri.getPath().split("/");
        if (pathParts.length < 7 || !WS_SUFFIX.equals("/" + pathParts[6])) {
            log.warn("[WS Proxy] Unexpected browser WS path: sessionId={}, path={}",
                    browserSession.getId(), browserUri.getPath());
            browserSession.close(CloseStatus.BAD_DATA.withReason("Invalid TTYD WS path"));
            return;
        }

        String agentId = pathParts[4];
        String port = pathParts[5];
        String agentIp = sessionManager.getAgentIp(agentId);
        if (agentIp == null) {
            log.warn("[WS Proxy] Target agent unavailable: agentId={}, sessionId={}", agentId, browserSession.getId());
            browserSession.close(CloseStatus.SERVER_ERROR.withReason("Target Agent unavailable"));
            return;
        }

        String targetWsUrl = "ws://" + agentIp + ":" + port + WS_SUFFIX;
        WebSocketHttpHeaders headers = buildProxyHeaders(browserSession, browserUri);
        log.info("[WS Proxy] Browser connected: sessionId={}, agentId={}, target={}, protocols={}",
                browserSession.getId(), agentId, targetWsUrl, headers.getSecWebSocketProtocol());

        try {
            WebSocketSession clientSession = wsClient.doHandshake(
                    new TargetTtydHandler(browserSession, targetWsUrl),
                    headers,
                    new URI(targetWsUrl)
            ).get();

            clientSessions.put(browserSession.getId(), clientSession);
            log.info("[WS Proxy] Target handshake established: browserSessionId={}, targetSessionId={}, target={}",
                    browserSession.getId(), clientSession.getId(), targetWsUrl);
        } catch (Exception e) {
            log.error("[WS Proxy] Failed to connect to TTYD target: browserSessionId={}, target={}",
                    browserSession.getId(), targetWsUrl, e);
            if (browserSession.isOpen()) {
                browserSession.close(CloseStatus.SERVER_ERROR.withReason("TTYD target handshake failed"));
            }
        }
    }

    @Override
    public void handleMessage(@NonNull WebSocketSession browserSession, @NonNull WebSocketMessage<?> message)
            throws Exception {
        WebSocketSession clientSession = clientSessions.get(browserSession.getId());
        if (clientSession != null && clientSession.isOpen()) {
            clientSession.sendMessage(message);
            log.debug("[WS Proxy] Browser -> TTYD: browserSessionId={}, payloadType={}",
                    browserSession.getId(), message.getClass().getSimpleName());
        } else {
            log.warn("[WS Proxy] Dropping browser message because target session is unavailable: browserSessionId={}",
                    browserSession.getId());
        }
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession browserSession, @NonNull Throwable exception) throws Exception {
        log.error("[WS Proxy] Browser transport error: browserSessionId={}", browserSession.getId(), exception);
        WebSocketSession clientSession = clientSessions.get(browserSession.getId());
        if (clientSession != null && clientSession.isOpen()) {
            clientSession.close(new CloseStatus(CloseStatus.SERVER_ERROR.getCode(), "Browser transport error"));
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession browserSession, @NonNull CloseStatus closeStatus) throws Exception {
        WebSocketSession clientSession = clientSessions.remove(browserSession.getId());
        log.info("[WS Proxy] Browser session closed: browserSessionId={}, status={}",
                browserSession.getId(), closeStatus);
        if (clientSession != null && clientSession.isOpen()) {
            clientSession.close(closeStatus);
        }
    }

    private WebSocketHttpHeaders buildProxyHeaders(WebSocketSession browserSession, URI browserUri) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        HttpHeaders handshakeHeaders = browserSession.getHandshakeHeaders();

        String cookie = handshakeHeaders.getFirst(HttpHeaders.COOKIE);
        if (cookie != null && !cookie.isEmpty()) {
            headers.add(HttpHeaders.COOKIE, cookie);
        }

        List<String> protocols = handshakeHeaders.get(HEADER_SEC_WEBSOCKET_PROTOCOL);
        if (protocols != null && !protocols.isEmpty()) {
            headers.setSecWebSocketProtocol(protocols);
        }

        String credential = extractCredential(browserUri.getQuery());
        if (credential != null && !credential.isEmpty()) {
            headers.add(HttpHeaders.AUTHORIZATION, "Basic " + credential);
        }

        return headers;
    }

    private String extractCredential(String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }

        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "credential".equals(kv[0])) {
                try {
                    return URLDecoder.decode(kv[1], "UTF-8");
                } catch (java.io.UnsupportedEncodingException e) {
                    throw new IllegalStateException("UTF-8 should always be supported", e);
                }
            }
        }
        return null;
    }

    @Override
    public List<String> getSubProtocols() {
        return SUPPORTED_PROTOCOLS;
    }

    private final class TargetTtydHandler extends AbstractWebSocketHandler {

        private final WebSocketSession browserSession;
        private final String targetWsUrl;

        private TargetTtydHandler(WebSocketSession browserSession, String targetWsUrl) {
            this.browserSession = browserSession;
            this.targetWsUrl = targetWsUrl;
        }

        @Override
        public void afterConnectionEstablished(@NonNull WebSocketSession session) {
            log.info("[WS Proxy] Target session connected: browserSessionId={}, targetSessionId={}, acceptedProtocol={}, target={}",
                    browserSession.getId(), session.getId(), session.getAcceptedProtocol(), targetWsUrl);
        }

        @Override
        public void handleMessage(@NonNull WebSocketSession session, @NonNull WebSocketMessage<?> message)
                throws Exception {
            if (browserSession.isOpen()) {
                browserSession.sendMessage(message);
                log.debug("[WS Proxy] TTYD -> Browser: browserSessionId={}, payloadType={}",
                        browserSession.getId(), message.getClass().getSimpleName());
            }
        }

        @Override
        public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception)
                throws Exception {
            log.error("[WS Proxy] Target transport error: browserSessionId={}, targetSessionId={}, target={}",
                    browserSession.getId(), session.getId(), targetWsUrl, exception);
            clientSessions.remove(browserSession.getId(), session);
            if (browserSession.isOpen()) {
                browserSession.close(new CloseStatus(CloseStatus.SERVER_ERROR.getCode(), "TTYD transport error"));
            }
        }

        @Override
        public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus closeStatus)
                throws Exception {
            clientSessions.remove(browserSession.getId(), session);
            log.info("[WS Proxy] Target session closed: browserSessionId={}, targetSessionId={}, status={}, target={}",
                    browserSession.getId(), session.getId(), closeStatus, targetWsUrl);
            if (browserSession.isOpen()) {
                browserSession.close(closeStatus);
            }
        }
    }
}
