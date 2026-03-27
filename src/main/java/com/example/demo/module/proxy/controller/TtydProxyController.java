package com.example.demo.module.proxy.controller;

import com.example.demo.websocket.session.AgentSessionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Enumeration;

/**
 * TTYD HTTP 反向代理控制器
 * 将 /api/proxy/ttyd/{agentId}/{port}/ 下的 HTTP 请求转发到对应 Agent 的 TTYD 端口
 * WebSocket 请求由 TtydWebSocketProxyHandler 单独处理
 */
@Slf4j
@Controller
public class TtydProxyController {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final AgentSessionManager sessionManager;

    public TtydProxyController(AgentSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @RequestMapping("/api/proxy/ttyd/{agentId}/{port}/**")
    public void proxyRequest(@PathVariable String agentId,
                             @PathVariable int port,
                             HttpServletRequest request,
                             HttpServletResponse response) throws IOException {

        String requestUri = request.getRequestURI();
        String remainingPath = extractRemainingPath(request);

        // /ws 升级请求应该由 TtydWebSocketProxyHandler 处理；若落到这里则说明映射未命中。
        if ("/ws".equals(remainingPath) || remainingPath.startsWith("/ws?")) {
            log.warn("[TTYD Proxy] WebSocket request unexpectedly reached HTTP proxy: method={}, uri={}",
                    request.getMethod(), requestUri);
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        String agentIp = sessionManager.getAgentIp(agentId);
        if (agentIp == null) {
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Agent not connected: " + agentId);
            return;
        }

        // 构建目标 URL
        StringBuilder targetUrl = new StringBuilder();
        targetUrl.append("http://").append(agentIp).append(":").append(port).append(remainingPath);
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl.append("?").append(queryString);
        }

        log.info("[TTYD Proxy] {} {} -> {}", request.getMethod(), requestUri, targetUrl);

        // 通过 HttpURLConnection 转发请求
        HttpURLConnection conn = (HttpURLConnection) new URL(targetUrl.toString()).openConnection();
        conn.setRequestMethod(request.getMethod());
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(30_000);
        conn.setInstanceFollowRedirects(false);

        // 复制请求头 (排除 hop-by-hop 头)
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (isHopByHop(headerName)) continue;
            Enumeration<String> values = request.getHeaders(headerName);
            while (values.hasMoreElements()) {
                conn.addRequestProperty(headerName, values.nextElement());
            }
        }

        // 如果请求中有 credential 参数，转换成 Basic Auth 头并转发给 TTYD
        String credential = request.getParameter("credential");
        if (credential != null && !credential.isEmpty()) {
            conn.setRequestProperty("Authorization", "Basic " + credential);
        }

        // 如果有请求体（POST/PUT），转发它
        if ("POST".equalsIgnoreCase(request.getMethod()) || "PUT".equalsIgnoreCase(request.getMethod())) {
            conn.setDoOutput(true);
            try (InputStream in = request.getInputStream();
                 OutputStream out = conn.getOutputStream()) {
                copy(in, out);
            }
        }

        // 复制响应状态和头
        int statusCode = conn.getResponseCode();
        response.setStatus(statusCode);

        for (String key : conn.getHeaderFields().keySet()) {
            if (key == null || isHopByHop(key)) continue;
            for (String value : conn.getHeaderFields().get(key)) {
                response.addHeader(key, value);
            }
        }

        // 复制响应体
        try (InputStream in = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream()) {
            if (in != null) {
                copy(in, response.getOutputStream());
            }
        }
    }

    private String extractRemainingPath(HttpServletRequest request) {
        String bestMatchingPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String pathWithinMapping = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        if (bestMatchingPattern != null && pathWithinMapping != null) {
            String extracted = PATH_MATCHER.extractPathWithinPattern(bestMatchingPattern, pathWithinMapping);
            if (extracted == null || extracted.isEmpty()) {
                return "/";
            }
            return extracted.startsWith("/") ? extracted : "/" + extracted;
        }
        return "/";
    }

    private void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

    private boolean isHopByHop(String headerName) {
        String h = headerName.toLowerCase();
        return h.equals("connection") || h.equals("keep-alive") || h.equals("proxy-authenticate")
                || h.equals("proxy-authorization") || h.equals("te") || h.equals("trailer")
                || h.equals("transfer-encoding") || h.equals("upgrade");
    }
}
